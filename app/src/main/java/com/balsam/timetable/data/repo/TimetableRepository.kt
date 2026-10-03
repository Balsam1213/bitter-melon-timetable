package com.balsam.timetable.data.repo

import com.balsam.timetable.data.db.AppDatabase
import com.balsam.timetable.data.db.Course
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Lesson
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.db.TimeSlot
import com.balsam.timetable.data.importer.ExportDoc
import com.balsam.timetable.data.importer.ParsedEntry
import com.balsam.timetable.data.model.CourseColors
import com.balsam.timetable.data.model.LessonLike
import com.balsam.timetable.data.model.WeekLogic
import kotlinx.coroutines.flow.Flow

class TimetableRepository(private val db: AppDatabase) {

    /** 数据变更后由 Graph 挂接：重排提醒 + 刷新桌面小组件 */
    var onDataChanged: (suspend () -> Unit)? = null

    private suspend fun changed() {
        onDataChanged?.invoke()
    }

    // ---------------- 查询 ----------------

    fun semesters(): Flow<List<Semester>> = db.semesterDao().all()
    fun currentSemester(): Flow<Semester?> = db.semesterDao().current()
    suspend fun currentSemesterOnce(): Semester? = db.semesterDao().currentOnce()
    suspend fun semesterById(id: Long): Semester? = db.semesterDao().byId(id)
    suspend fun semesterByNameAndStart(name: String, startDate: String): Semester? =
        db.semesterDao().byNameAndStart(name, startDate)

    fun slots(semesterId: Long): Flow<List<TimeSlot>> = db.timeSlotDao().bySemester(semesterId)
    suspend fun slotsOnce(semesterId: Long): List<TimeSlot> = db.timeSlotDao().bySemesterOnce(semesterId)

    fun coursesWithLessons(semesterId: Long): Flow<List<CourseWithLessons>> = db.courseDao().withLessons(semesterId)
    suspend fun coursesOnce(semesterId: Long): List<CourseWithLessons> = db.courseDao().withLessonsOnce(semesterId)
    suspend fun courseById(id: Long): Course? = db.courseDao().byId(id)

    // ---------------- 学期 ----------------

    /** 返回新建学期 id；slots 为空时用默认节次，copySlotsFrom 指定则复制其节次 */
    suspend fun createSemester(
        name: String,
        startDate: String,
        totalWeeks: Int,
        copySlotsFrom: Long? = null,
        setCurrent: Boolean = false,
        slots: List<Pair<String, String>>? = null,
    ): Long {
        val id = db.semesterDao().insert(Semester(name = name, startDate = startDate, totalWeeks = totalWeeks))
        val slotList = when {
            slots != null -> slots.mapIndexed { i, (s, e) -> TimeSlot(semesterId = id, orderIndex = i + 1, startTime = s, endTime = e) }
            copySlotsFrom != null -> db.timeSlotDao().bySemesterOnce(copySlotsFrom)
                .mapIndexed { i, s -> s.copy(id = 0, semesterId = id, orderIndex = i + 1) }
            else -> defaultSlots(id)
        }
        db.timeSlotDao().insertAll(slotList)
        if (setCurrent || db.semesterDao().currentOnce() == null) {
            db.semesterDao().setCurrent(id)
        }
        changed()
        return id
    }

    /** 默认节次模板：上午 5 节(1-5) + 下午 5 节(6-10) + 晚上 3 节(11-13)；午休/晚休由课间空档自动推导 */
    fun defaultSlots(semesterId: Long): List<TimeSlot> = listOf(
        "08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:45", "10:55" to "11:40", "11:50" to "12:35",
        "14:00" to "14:45", "14:55" to "15:40", "16:00" to "16:45", "16:55" to "17:40", "17:50" to "18:35",
        "19:00" to "19:45", "19:55" to "20:40", "20:50" to "21:35",
    ).mapIndexed { i, (s, e) ->
        TimeSlot(semesterId = semesterId, orderIndex = i + 1, startTime = s, endTime = e)
    }

    suspend fun updateSemester(s: Semester) {
        db.semesterDao().update(s)
        changed()
    }

    suspend fun setCurrentSemester(id: Long) {
        db.semesterDao().setCurrent(id)
        changed()
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        db.semesterDao().byId(id)?.let { db.semesterDao().update(it.copy(isArchived = archived)) }
        changed()
    }

    suspend fun deleteSemester(id: Long) {
        val sem = db.semesterDao().byId(id) ?: return
        db.courseDao().bySemesterOnce(id).forEach { db.courseDao().deleteCourseWithLessons(it.id) }
        db.timeSlotDao().deleteForSemester(id)
        db.semesterDao().delete(id)
        if (sem.isCurrent) {
            db.semesterDao().latestActive()?.let { db.semesterDao().setCurrent(it.id) }
        }
        changed()
    }

    // ---------------- 节次 ----------------

    /** 用给定起止时间整体替换学期节次表（新学期首次导入用） */
    suspend fun replaceSlots(semesterId: Long, ranges: List<Pair<String, String>>) {
        db.timeSlotDao().deleteForSemester(semesterId)
        db.timeSlotDao().insertAll(
            ranges.mapIndexed { i, (s, e) ->
                TimeSlot(semesterId = semesterId, orderIndex = i + 1, startTime = s, endTime = e)
            }
        )
        changed()
    }

    suspend fun updateSlot(slot: TimeSlot) {
        db.timeSlotDao().update(slot)
        changed()
    }

    suspend fun addSlot(semesterId: Long, startTime: String, endTime: String) {
        val next = (db.timeSlotDao().bySemesterOnce(semesterId).maxOfOrNull { it.orderIndex } ?: 0) + 1
        db.timeSlotDao().insert(TimeSlot(semesterId = semesterId, orderIndex = next, startTime = startTime, endTime = endTime))
        changed()
    }

    suspend fun removeSlot(slot: TimeSlot) {
        db.timeSlotDao().delete(slot.id)
        val rest = db.timeSlotDao().bySemesterOnce(slot.semesterId)
            .sortedBy { it.orderIndex }
            .mapIndexed { i, s -> s.copy(orderIndex = i + 1) }
        db.timeSlotDao().updateAll(rest)
        changed()
    }

    // ---------------- 课程 ----------------

    suspend fun saveCourse(course: Course, lessons: List<Lesson>) {
        if (course.id == 0L) {
            val id = db.courseDao().insert(course)
            db.lessonDao().insertAll(lessons.map { it.copy(courseId = id) })
        } else {
            db.courseDao().update(course)
            db.courseDao().deleteLessonsOf(course.id)
            db.lessonDao().insertAll(lessons.map { it.copy(id = 0, courseId = course.id) })
        }
        changed()
    }

    suspend fun deleteCourse(courseId: Long) {
        db.courseDao().deleteCourseWithLessons(courseId)
        changed()
    }

    /** 返回与候选时间段冲突的课程（可排除某课程自身，用于编辑场景） */
    suspend fun findConflicts(semesterId: Long, candidate: LessonLike, excludeCourseId: Long): List<CourseWithLessons> =
        db.courseDao().withLessonsOnce(semesterId).filter { cwl ->
            cwl.course.id != excludeCourseId && cwl.lessons.any { WeekLogic.conflicts(candidate, it) }
        }

    // ---------------- 导入 / 导出 ----------------

    /** 把解析好的条目导入学期；同名同教师合并为一门课。返回新增课程数 */
    suspend fun importEntries(semesterId: Long, entries: List<ParsedEntry>, clearExisting: Boolean): Int {
        if (clearExisting) {
            db.courseDao().bySemesterOnce(semesterId).forEach { db.courseDao().deleteCourseWithLessons(it.id) }
        }
        val valid = entries.filter { it.include && it.name.isNotBlank() }
        // 识别出的节次超出当前节次表时自动追加行（45 分钟一节、10 分钟间隔顺延），
        // 避免高节次课程（如第 11-12 节）被钳制丢失
        val currentSlots = db.timeSlotDao().bySemesterOnce(semesterId).sortedBy { it.orderIndex }
        val maxNeeded = valid.maxOfOrNull { maxOf(it.startSlot, it.endSlot) } ?: 0
        if (currentSlots.isNotEmpty() && maxNeeded > currentSlots.size) {
            var cur = runCatching { java.time.LocalTime.parse(currentSlots.last().endTime) }
                .getOrDefault(java.time.LocalTime.of(21, 0))
            val toAdd = mutableListOf<TimeSlot>()
            for (n in (currentSlots.size + 1)..maxNeeded) {
                val end = cur.plusMinutes(45)
                toAdd.add(
                    TimeSlot(
                        semesterId = semesterId,
                        orderIndex = n,
                        startTime = cur.toString().take(5),
                        endTime = end.toString().take(5),
                    )
                )
                cur = end.plusMinutes(10)
            }
            db.timeSlotDao().insertAll(toAdd)
        }
        var colorIdx = db.courseDao().bySemesterOnce(semesterId).size
        val grouped = valid.groupBy { it.name.trim() to it.teacher.trim() }
        grouped.forEach { (_, group) ->
            val first = group.first()
            val courseId = db.courseDao().insert(
                Course(
                    semesterId = semesterId,
                    name = first.name.trim(),
                    teacher = first.teacher.trim(),
                    colorArgb = CourseColors.pick(colorIdx++),
                )
            )
            db.lessonDao().insertAll(group.map { e ->
                Lesson(
                    courseId = courseId,
                    dayOfWeek = e.dayOfWeek,
                    startSlot = e.startSlot,
                    endSlot = maxOf(e.startSlot, e.endSlot),
                    startWeek = e.startWeek,
                    endWeek = maxOf(e.startWeek, e.endWeek),
                    weekPattern = e.weekPattern.name,
                    location = e.location.trim(),
                )
            })
        }
        changed()
        return grouped.size
    }

    /** 导入本应用导出的 JSON 文档；学期同名同起始日则合并，否则新建并设为当前 */
    suspend fun importFromDoc(doc: ExportDoc): Int {
        val existing = db.semesterDao().byNameAndStart(doc.semester.name, doc.semester.startDate)
        val semesterId: Long
        if (existing != null) {
            semesterId = existing.id
        } else {
            semesterId = createSemester(
                name = doc.semester.name,
                startDate = doc.semester.startDate,
                totalWeeks = doc.semester.totalWeeks,
                setCurrent = true,
                slots = doc.slots.map { it.start to it.end },
            )
        }
        return importEntries(semesterId, doc.entries.map { it.toParsedEntry() }, clearExisting = false)
    }

    suspend fun exportDoc(semesterId: Long): ExportDoc? {
        val sem = db.semesterDao().byId(semesterId) ?: return null
        val slots = db.timeSlotDao().bySemesterOnce(semesterId).sortedBy { it.orderIndex }
        val courses = db.courseDao().withLessonsOnce(semesterId)
        return ExportDoc(
            semester = com.balsam.timetable.data.importer.ExportSemester(sem.name, sem.startDate, sem.totalWeeks),
            slots = slots.map { com.balsam.timetable.data.importer.ExportSlot(it.startTime, it.endTime) },
            entries = courses.flatMap { cwl ->
                cwl.lessons.map { l ->
                    com.balsam.timetable.data.importer.ExportEntry(
                        name = cwl.course.name,
                        teacher = cwl.course.teacher,
                        location = l.location,
                        day = l.dayOfWeek,
                        startSlot = l.startSlot,
                        endSlot = l.endSlot,
                        startWeek = l.startWeek,
                        endWeek = l.endWeek,
                        pattern = l.weekPattern,
                    )
                }
            },
        )
    }
}
