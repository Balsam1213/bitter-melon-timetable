package com.balsam.timetable.ui.settings

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balsam.timetable.Graph
import com.balsam.timetable.data.model.AppSettings
import com.balsam.timetable.data.model.ColorTheme
import com.balsam.timetable.data.model.HolidayCalendar
import com.balsam.timetable.data.model.TextScale
import com.balsam.timetable.data.repo.DEFAULT_HOLIDAYS
import com.balsam.timetable.data.repo.DEFAULT_MAKEUP_DAYS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {
    private val settingsRepo = Graph.settingsRepository
    private val repo = Graph.repository

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // 提醒相关设置变更后立即重排闹钟，否则要等下次数据变更/重启才生效
    fun setReminderEnabled(v: Boolean) = viewModelScope.launch {
        settingsRepo.setReminderEnabled(v)
        Graph.reminderScheduler.reschedule()
    }

    fun setLeadMinutes(v: Int) = viewModelScope.launch {
        settingsRepo.setLeadMinutes(v.coerceIn(0, 120))
        Graph.reminderScheduler.reschedule()
    }

    fun setShowWeekend(v: Boolean) = viewModelScope.launch { settingsRepo.setShowWeekend(v) }

    fun setTextScale(v: TextScale) = viewModelScope.launch { settingsRepo.setTextScale(v) }

    fun setCompactGrid(v: Boolean) = viewModelScope.launch { settingsRepo.setCompactGrid(v) }

    fun setColorTheme(v: ColorTheme) = viewModelScope.launch { settingsRepo.setColorTheme(v) }

    /** 自动备份开关：开 → 保证已排期；关 → 取消周期任务 */
    fun setAutoBackupEnabled(v: Boolean) = viewModelScope.launch {
        settingsRepo.setAutoBackupEnabled(v)
        if (v) {
            com.balsam.timetable.data.backup.AutoBackup.ensureScheduled(Graph.appContext)
        } else {
            com.balsam.timetable.data.backup.AutoBackup.cancel(Graph.appContext)
        }
    }

    /** 立即备份（异步执行，结果见 backupUi 变化） */
    fun backupNow() = com.balsam.timetable.data.backup.AutoBackup.runNow(Graph.appContext)

    /** 备份状态（数据区块展示）。旧版本只记成功时间，没有 ok 标记时视为成功，避免老用户误报红字 */
    sealed interface BackupUiState {
        data object Never : BackupUiState
        data class Done(val timeMillis: Long) : BackupUiState
        data class Failed(val timeMillis: Long) : BackupUiState
    }

    val backupUi: StateFlow<BackupUiState> = kotlinx.coroutines.flow.combine(
        settingsRepo.lastBackupTime,
        settingsRepo.lastBackupOk,
    ) { time, ok ->
        when {
            time == null -> BackupUiState.Never
            ok == false -> BackupUiState.Failed(time)
            else -> BackupUiState.Done(time)
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), BackupUiState.Never)

    // 日历管理页（节假日/调休的增删、同步）在 ui/holidays，有自己的 ViewModel
    val holidayCalendar: StateFlow<HolidayCalendar> = settingsRepo.holidayCalendar
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HolidayCalendar(offDays = DEFAULT_HOLIDAYS, makeupDays = DEFAULT_MAKEUP_DAYS),
        )

    fun setHolidaySkipEnabled(v: Boolean) = viewModelScope.launch {
        settingsRepo.setHolidaySkipEnabled(v)
        Graph.reminderScheduler.reschedule()
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return Graph.appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // ACTION 常量编译期内联，低版本引用安全；该 Intent 只会在 31+ 的精确闹钟引导里使用
    @android.annotation.SuppressLint("InlinedApi")
    fun requestExactAlarmIntent(): Intent =
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            "package:${Graph.appContext.packageName}".toUri(),
        )

    fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, Graph.appContext.packageName)

    /** 导出当前学期 JSON；返回错误信息（成功返回 null） */
    suspend fun exportToUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val semester = repo.currentSemesterOnce() ?: return@withContext "没有当前学期"
            val doc = repo.exportDoc(semester.id) ?: return@withContext "导出失败"
            val json = kotlinx.serialization.json.Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }.encodeToString(
                com.balsam.timetable.data.importer.ExportDoc.serializer(), doc,
            )
            Graph.appContext.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
            } ?: return@withContext "无法写入所选文件"
            null
        } catch (e: Exception) {
            "导出失败：${e.message}"
        }
    }
}
