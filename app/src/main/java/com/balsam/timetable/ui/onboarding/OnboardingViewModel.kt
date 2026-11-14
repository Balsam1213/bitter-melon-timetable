package com.balsam.timetable.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balsam.timetable.Graph
import com.balsam.timetable.data.model.SemesterDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 首启向导：创建学期（预填可改）→ 选导入方式（复用 ImportScreen 识别/确认）
 * → 就绪页（本周课表已生成 + 提醒引导）。跳过任何一步都能进入课表。
 */
class OnboardingViewModel : ViewModel() {

    private val repo = Graph.repository
    private val settingsRepo = Graph.settingsRepository

    enum class Step { SEMESTER, IMPORT, READY }

    data class UiState(
        val step: Step = Step.SEMESTER,
        val busy: Boolean = false,
        val error: String? = null,
        // Step.SEMESTER 表单（预填，用户可改）
        val semesterName: String = SemesterDefaults.nameSuggestion(),
        val startDate: String = SemesterDefaults.startSuggestion(),
        val totalWeeks: String = SemesterDefaults.TOTAL_WEEKS.toString(),
        // Step.READY
        val semesterCreated: Boolean = false,
        val semesterDisplayName: String? = null,
        val courseCount: Int = 0,
        val reminderEnabled: Boolean = true,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    fun updateName(v: String) {
        _ui.value = _ui.value.copy(semesterName = v)
    }

    fun updateStartDate(v: String) {
        _ui.value = _ui.value.copy(startDate = v)
    }

    fun updateTotalWeeks(v: String) {
        if (v.all(Char::isDigit) && v.length <= 2) _ui.value = _ui.value.copy(totalWeeks = v)
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }

    /** 第 1 步：创建学期（默认值已预填，直接确认即可） */
    fun createSemester() {
        val s = _ui.value
        val weeks = s.totalWeeks.toIntOrNull() ?: SemesterDefaults.TOTAL_WEEKS
        if (s.semesterName.isBlank() || weeks !in 1..40) {
            _ui.value = s.copy(error = "请填写学期名称，总周数应在 1-40 之间")
            return
        }
        viewModelScope.launch {
            _ui.value = s.copy(busy = true, error = null)
            try {
                repo.createSemester(s.semesterName.trim(), s.startDate, weeks, setCurrent = true)
                _ui.value = _ui.value.copy(
                    busy = false,
                    step = Step.IMPORT,
                    semesterCreated = true,
                    semesterDisplayName = s.semesterName.trim(),
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, error = "学期创建失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 第 2 步：不导入直接看课表（或导入返回后手动进入就绪页） */
    fun goToReady() {
        viewModelScope.launch {
            val sem = repo.currentSemesterOnce()
            val count = sem?.let { repo.coursesOnce(it.id).size } ?: 0
            _ui.value = _ui.value.copy(
                step = Step.READY,
                semesterCreated = sem != null,
                semesterDisplayName = sem?.name,
                courseCount = count,
                reminderEnabled = settingsRepo.once().reminderEnabled,
            )
        }
    }

    /** 从导入页返回时调用：已经导入了课程就自动进入就绪页，否则留在导入步 */
    fun goToReadyIfImported() {
        viewModelScope.launch {
            val sem = repo.currentSemesterOnce() ?: return@launch
            val count = repo.coursesOnce(sem.id).size
            if (count > 0) {
                _ui.value = _ui.value.copy(
                    step = Step.READY,
                    semesterCreated = true,
                    semesterDisplayName = sem.name,
                    courseCount = count,
                    reminderEnabled = settingsRepo.once().reminderEnabled,
                )
            }
        }
    }

    fun setReminderEnabled(v: Boolean) {
        _ui.value = _ui.value.copy(reminderEnabled = v)
        viewModelScope.launch { settingsRepo.setReminderEnabled(v) }
    }

    /** 完成：落「已完成引导」标记，导航回课表（调用方 onFinished） */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepo.setOnboardingDone()
            onDone()
        }
    }
}
