package com.balsam.timetable.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balsam.timetable.Graph
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.db.TimeSlot
import com.balsam.timetable.data.model.AppSettings
import com.balsam.timetable.data.model.HolidayCalendar
import com.balsam.timetable.data.model.HomeViewMode
import com.balsam.timetable.data.model.TextScale
import com.balsam.timetable.data.model.TodayLesson
import com.balsam.timetable.data.model.TodayLogic
import com.balsam.timetable.data.model.WeekLogic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModel : ViewModel() {

    private val repo = Graph.repository
    private val settingsRepo = Graph.settingsRepository

    /** null = 跟随本周 */
    private val selectedWeek = MutableStateFlow<Int?>(null)

    /** 每半分钟一次时钟：驱动今日视图的上课中/倒计时状态与跨午夜翻日 */
    private val minuteTick = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000L)
        }
    }

    sealed interface UiState {
        data object Loading : UiState
        data object Empty : UiState
        data class Ready(
            val semester: Semester,
            val slots: List<TimeSlot>,
            val courses: List<CourseWithLessons>,
            val week: Int,
            val todayWeek: Int?,
            val showWeekend: Boolean,
            val today: LocalDate,
            val holidays: HolidayCalendar = HolidayCalendar(),
            val textScale: TextScale = TextScale.STANDARD,
            val compactGrid: Boolean = false,
            val viewMode: HomeViewMode = HomeViewMode.TODAY,
            val todayLessons: List<TodayLesson> = emptyList(),
        ) : UiState
    }

    val uiState: StateFlow<UiState> =
        combine(repo.currentSemester(), selectedWeek, settingsRepo.settings.distinctUntilChanged()) { s, w, st ->
            Triple(s, w, st)
        }.flatMapLatest { (semester, selWeek, settings) ->
            if (semester == null) {
                flowOf(UiState.Empty)
            } else {
                combine(
                    repo.slots(semester.id),
                    repo.coursesWithLessons(semester.id),
                    settingsRepo.holidayCalendar,
                    minuteTick,
                ) { slots, courses, holidays, now ->
                    val today = now.toLocalDate()
                    val todayWeek = WeekLogic.weekOf(today, semester.startDate, semester.totalWeeks)
                    val week = selWeek ?: todayWeek ?: 1
                    // 今天按哪张课表：调休上班日映射周几；放假日 UI 层显示放假态
                    val dow = holidays.effectiveDayOfWeek(today)
                    val todayLessons = TodayLogic.todayLessons(now.toLocalTime(), dow, todayWeek, slots, courses)
                    UiState.Ready(
                        semester, slots, courses, week, todayWeek,
                        settings.showWeekend, today, holidays,
                        settings.textScale, settings.compactGrid,
                        settings.homeViewMode, todayLessons,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /** 学期切换对话框用 */
    val semesters: StateFlow<List<Semester>> =
        repo.semesters().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- 首页状态横幅（提醒受限 > 备份失败 > 从未备份，最多显示一条） ----------------

    enum class BannerKind { REMINDER, BACKUP, BACKUP_NEVER }

    data class HomeBanner(val kind: BannerKind, val text: String)

    /** 回前台时 bump 一次：权限授予/撤销只有回到应用才可能变化 */
    private val bannerRefresh = MutableStateFlow(0)

    fun refreshBanner() {
        bannerRefresh.value++
    }

    /** 当前学期是否有课程（无学期视为没有）；「从未备份」横幅只在有课程时提示 */
    private val hasCurrentSemesterCourses: Flow<Boolean> = repo.currentSemester()
        .flatMapLatest { sem ->
            if (sem == null) flowOf(false)
            else repo.hasCourses(sem.id)
        }

    // DataStore 对任意 key 的写入都会发射 settings；distinctUntilChanged 避免无关写入
    // （备份时间/通知已问/更新检查日）取消重建整条课程流水线
    val banner: StateFlow<HomeBanner?> =
        combine(
            settingsRepo.settings.distinctUntilChanged(),
            settingsRepo.lastBackupOk,
            hasCurrentSemesterCourses,
            bannerRefresh,
        ) { st, lastOk, hasCourses, _ ->
            when (pickHomeBanner(
                reminderEnabled = st.reminderEnabled,
                reminderBlocked = !hasNotificationPermission() || !canScheduleExactAlarms(),
                lastBackupOk = lastOk,
                hasCurrentSemesterCourses = hasCourses,
            )) {
                BannerKind.REMINDER -> HomeBanner(BannerKind.REMINDER, "课前提醒可能不响：通知或精确闹钟权限未就绪，点此去开启")
                BannerKind.BACKUP -> HomeBanner(BannerKind.BACKUP, "上次自动备份失败，点此查看备份状态")
                BannerKind.BACKUP_NEVER -> HomeBanner(BannerKind.BACKUP_NEVER, "本学期的课程还没备份过，点此去设置开启自动备份")
                null -> null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun hasNotificationPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            Graph.appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return androidx.core.app.NotificationManagerCompat.from(Graph.appContext).areNotificationsEnabled()
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 31) return true
        val am = Graph.appContext.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
        return am?.canScheduleExactAlarms() ?: true
    }

    fun setCurrentSemester(id: Long) {
        viewModelScope.launch { repo.setCurrentSemester(id) }
    }

    fun selectWeek(week: Int?) {
        selectedWeek.value = week
    }

    fun setViewMode(mode: HomeViewMode) {
        viewModelScope.launch { settingsRepo.setHomeViewMode(mode) }
    }
}

/**
 * 首页横幅决策（纯函数，单测守门）：提醒受限 > 备份失败 > 从未备份，最多一条。
 * 用户主动关闭提醒不算异常（reminderEnabled=false 时不提示）；「从未备份」只在有课程时温和提示，
 * 且备份过一次（成功或失败都算「有过备份记录」）就交给失败横幅接管。
 */
fun pickHomeBanner(
    reminderEnabled: Boolean,
    reminderBlocked: Boolean,
    lastBackupOk: Boolean?,
    hasCurrentSemesterCourses: Boolean,
): TimetableViewModel.BannerKind? = when {
    reminderEnabled && reminderBlocked -> TimetableViewModel.BannerKind.REMINDER
    lastBackupOk == false -> TimetableViewModel.BannerKind.BACKUP
    lastBackupOk == null && hasCurrentSemesterCourses -> TimetableViewModel.BannerKind.BACKUP_NEVER
    else -> null
}
