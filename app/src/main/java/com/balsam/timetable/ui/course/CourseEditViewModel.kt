package com.balsam.timetable.ui.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balsam.timetable.Graph
import com.balsam.timetable.data.db.Course
import com.balsam.timetable.data.db.Lesson
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.model.CourseColors
import com.balsam.timetable.data.model.DAY_LABELS
import com.balsam.timetable.data.model.LessonLike
import com.balsam.timetable.data.model.ReminderMode
import com.balsam.timetable.data.model.WeekLogic
import com.balsam.timetable.data.model.WeekPattern
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CourseEditViewModel : ViewModel() {

    private val repo = Graph.repository

    /** 从课表空白格选中后带入的初始时间段（节次号） */
    data class LessonPrefill(val dayOfWeek: Int, val startSlot: Int, val endSlot: Int)

    data class FormLesson(
        override val dayOfWeek: Int = 1,
        override val startSlot: Int = 1,
        override val endSlot: Int = 2,
        override val startWeek: Int = 1,
        override val endWeek: Int = 16,
        override val pattern: WeekPattern = WeekPattern.EVERY,
        val location: String = "",
    ) : LessonLike

    data class UiState(
        val loading: Boolean = true,
        val semester: Semester? = null,
        val slotCount: Int = 10,
        val courseName: String = "",
        val teacher: String = "",
        val colorIdx: Int = 0,
        val note: String = "",
        val reminderMode: ReminderMode = ReminderMode.GLOBAL,
        val lessons: List<FormLesson> = listOf(FormLesson()),
        val conflictWith: List<String> = emptyList(),
        val saved: Boolean = false,
        val isNew: Boolean = true,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui
    private var courseId: Long = -1L

    /** 小窗重新打开时清除上次的保存/冲突标记，否则会因 saved=true 立即自动关闭 */
    fun resetTransient() {
        _ui.value = _ui.value.copy(saved = false, conflictWith = emptyList())
    }

    fun load(id: Long, prefill: LessonPrefill? = null) {
        // 每次都重建状态：新建表单必须全新（否则复用残留状态会导致"保存不显示"、
        // "与前一次冲突"等怪象）；已有课程也重新从库读取，保证看到最新数据。
        courseId = id
        viewModelScope.launch {
            val semester = repo.currentSemesterOnce()
            if (semester == null) {
                _ui.value = _ui.value.copy(loading = false)
                return@launch
            }
            val slots = repo.slotsOnce(semester.id)
            if (id > 0) {
                val cwl = repo.coursesOnce(semester.id).firstOrNull { it.course.id == id }
                if (cwl != null) {
                    val c = cwl.course
                    _ui.value = UiState(
                        loading = false,
                        semester = semester,
                        slotCount = slots.size,
                        courseName = c.name,
                        teacher = c.teacher,
                        colorIdx = CourseColors.PALETTE.indexOfFirst { it == c.colorArgb }.coerceAtLeast(0),
                        note = c.note,
                        reminderMode = ReminderMode.from(c.reminderMode),
                        lessons = cwl.lessons.map {
                            FormLesson(
                                it.dayOfWeek, it.startSlot, it.endSlot,
                                it.startWeek, it.endWeek, it.pattern, it.location,
                            )
                        }.ifEmpty { listOf(FormLesson(endWeek = semester.totalWeeks)) },
                        isNew = false,
                    )
                    return@launch
                }
            }
            _ui.value = UiState(
                loading = false,
                semester = semester,
                slotCount = slots.size,
                lessons = listOf(
                    if (prefill != null) {
                        FormLesson(
                            dayOfWeek = prefill.dayOfWeek,
                            startSlot = prefill.startSlot,
                            endSlot = prefill.endSlot,
                            endWeek = semester.totalWeeks,
                        )
                    } else {
                        FormLesson(endWeek = semester.totalWeeks)
                    }
                ),
                colorIdx = repo.coursesOnce(semester.id).size,
            )
        }
    }

    fun update(transform: (UiState) -> UiState) {
        _ui.value = transform(_ui.value)
    }

    fun save() {
        val s = _ui.value
        val semester = s.semester ?: return
        if (s.courseName.isBlank()) return
        viewModelScope.launch {
            val conflicts = collectConflicts(semester.id, s)
            if (conflicts.isEmpty()) {
                persist(s)
                _ui.value = _ui.value.copy(saved = true, conflictWith = emptyList())
            } else {
                _ui.value = _ui.value.copy(conflictWith = conflicts.distinct())
            }
        }
    }

    /** 用户看到冲突提示后仍选择保存 */
    fun forceSave() {
        val s = _ui.value
        if (s.courseName.isBlank()) return
        viewModelScope.launch {
            persist(s)
            _ui.value = _ui.value.copy(saved = true, conflictWith = emptyList())
        }
    }

    private suspend fun persist(s: UiState) {
        val semester = s.semester ?: return
        val course = Course(
            id = if (s.isNew) 0L else courseId,
            semesterId = semester.id,
            name = s.courseName.trim(),
            teacher = s.teacher.trim(),
            colorArgb = CourseColors.PALETTE[s.colorIdx % CourseColors.PALETTE.size],
            note = s.note,
            reminderMode = s.reminderMode.name,
        )
        val lessons = s.lessons.map {
            Lesson(
                courseId = course.id,
                dayOfWeek = it.dayOfWeek,
                startSlot = minOf(it.startSlot, it.endSlot),
                endSlot = maxOf(it.startSlot, it.endSlot),
                startWeek = minOf(it.startWeek, it.endWeek),
                endWeek = maxOf(it.startWeek, it.endWeek),
                weekPattern = it.pattern.name,
                location = it.location.trim(),
            )
        }
        repo.saveCourse(course, lessons)
    }

    private suspend fun collectConflicts(semesterId: Long, s: UiState): List<String> {
        val conflicts = mutableListOf<String>()
        val normalized = s.lessons.map {
            FormLesson(
                it.dayOfWeek,
                minOf(it.startSlot, it.endSlot), maxOf(it.startSlot, it.endSlot),
                minOf(it.startWeek, it.endWeek), maxOf(it.startWeek, it.endWeek),
                it.pattern, it.location,
            )
        }
        normalized.forEach { l ->
            repo.findConflicts(semesterId, l, if (s.isNew) -1 else courseId).forEach { cwl ->
                cwl.lessons.filter { WeekLogic.conflicts(l, it) }.forEach { cl ->
                    conflicts.add(
                        "${cwl.course.name}（${DAY_LABELS.getOrElse(l.dayOfWeek - 1) { "" }}第${cl.startSlot}-${cl.endSlot}节，${cl.startWeek}-${cl.endWeek}周${cl.pattern.label}）"
                    )
                }
            }
        }
        return conflicts
    }

    fun delete(onDone: () -> Unit) {
        if (_ui.value.isNew || courseId <= 0) {
            onDone()
            return
        }
        viewModelScope.launch {
            repo.deleteCourse(courseId)
            onDone()
        }
    }
}
