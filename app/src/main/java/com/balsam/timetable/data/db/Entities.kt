package com.balsam.timetable.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.balsam.timetable.data.model.LessonLike
import com.balsam.timetable.data.model.ReminderMode
import com.balsam.timetable.data.model.WeeksCodec

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
    /** 上课周次集合，升序逗号分隔（"1,2,...,8,11"）；解析见 WeeksCodec */
    val weeks: String,
    val location: String = "",
) : LessonLike {
    override val weekSet: Set<Int> get() = WeeksCodec.parse(weeks)
}

data class CourseWithLessons(
    @Embedded val course: Course,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val lessons: List<Lesson>,
)
