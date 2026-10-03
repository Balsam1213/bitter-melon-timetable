package com.balsam.timetable.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balsam.timetable.Graph
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.db.TimeSlot
import com.balsam.timetable.data.model.AppSettings
import com.balsam.timetable.data.model.WeekLogic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModel : ViewModel() {

    private val repo = Graph.repository
    private val settingsRepo = Graph.settingsRepository

    /** null = 跟随本周 */
    private val selectedWeek = MutableStateFlow<Int?>(null)

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
        ) : UiState
    }

    val uiState: StateFlow<UiState> =
        combine(repo.currentSemester(), selectedWeek, settingsRepo.settings) { s, w, st ->
            Triple(s, w, st)
        }.flatMapLatest { (semester, selWeek, settings) ->
            if (semester == null) {
                flowOf(UiState.Empty)
            } else {
                combine(repo.slots(semester.id), repo.coursesWithLessons(semester.id)) { slots, courses ->
                    val today = LocalDate.now()
                    val todayWeek = WeekLogic.weekOf(today, semester.startDate, semester.totalWeeks)
                    val week = selWeek ?: todayWeek ?: 1
                    UiState.Ready(semester, slots, courses, week, todayWeek, settings.showWeekend, today)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /** 学期切换对话框用 */
    val semesters: StateFlow<List<Semester>> =
        repo.semesters().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCurrentSemester(id: Long) {
        viewModelScope.launch { repo.setCurrentSemester(id) }
    }

    fun selectWeek(week: Int?) {
        selectedWeek.value = week
    }
}
