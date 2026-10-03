package com.balsam.timetable.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.balsam.timetable.data.model.LessonLike
import com.balsam.timetable.data.model.ReminderMode
import com.balsam.timetable.data.model.WeekPattern

@Entity(tableName = "semesters")
data class Semester(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 第 1 周的周一，yyyy-MM-dd */
    val startDate: String,
    val totalWeeks: Int,
    val isCurrent: Boolean = false,
    val isArchived: Boolean = false,
)

@Entity(tableName = "time_slots")
data class TimeSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    /** 第几节，从 1 开始 */
    val orderIndex: Int,
    val startTime: String,
    val endTime: String,
    /** 已废弃：午休/晚休改为由上下午课间空档自动推导，此字段恒为 false（保留以兼容 v2 库） */
    val isBreak: Boolean = false,
)

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    val name: String,
    val teacher: String = "",
    val colorArgb: Long = 0xFF4F6AF6,
    val note: String = "",
    val reminderMode: String = ReminderMode.GLOBAL.name,
)

@Entity(tableName = "lessons")
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    override val dayOfWeek: Int,
    override val startSlot: Int,
    override val endSlot: Int,
    override val startWeek: Int,
    override val endWeek: Int,
    val weekPattern: String = WeekPattern.EVERY.name,
    val location: String = "",
) : LessonLike {
    override val pattern: WeekPattern get() = WeekPattern.from(weekPattern)
}

data class CourseWithLessons(
    @Embedded val course: Course,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val lessons: List<Lesson>,
)
