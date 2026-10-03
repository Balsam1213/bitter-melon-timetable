package com.balsam.timetable.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {
    @Query("SELECT * FROM semesters ORDER BY isArchived ASC, id DESC")
    fun all(): Flow<List<Semester>>

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    fun current(): Flow<Semester?>

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    suspend fun currentOnce(): Semester?

    @Query("SELECT * FROM semesters WHERE id = :id")
    suspend fun byId(id: Long): Semester?

    @Query("SELECT * FROM semesters WHERE name = :name AND startDate = :startDate LIMIT 1")
    suspend fun byNameAndStart(name: String, startDate: String): Semester?

    @Query("SELECT * FROM semesters WHERE isArchived = 0 ORDER BY id DESC LIMIT 1")
    suspend fun latestActive(): Semester?

    @Insert
    suspend fun insert(s: Semester): Long

    @Update
    suspend fun update(s: Semester)

    @Query("DELETE FROM semesters WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE semesters SET isCurrent = 0 WHERE isCurrent = 1")
    suspend fun clearCurrent()

    @Query("UPDATE semesters SET isCurrent = 1 WHERE id = :id")
    suspend fun markCurrent(id: Long)

    @Transaction
    suspend fun setCurrent(id: Long) {
        clearCurrent()
        markCurrent(id)
    }
}

@Dao
interface TimeSlotDao {
    @Query("SELECT * FROM time_slots WHERE semesterId = :semesterId ORDER BY orderIndex ASC")
    fun bySemester(semesterId: Long): Flow<List<TimeSlot>>

    @Query("SELECT * FROM time_slots WHERE semesterId = :semesterId ORDER BY orderIndex ASC")
    suspend fun bySemesterOnce(semesterId: Long): List<TimeSlot>

    @Insert
    suspend fun insertAll(list: List<TimeSlot>)

    @Insert
    suspend fun insert(s: TimeSlot): Long

    @Update
    suspend fun updateAll(list: List<TimeSlot>)

    @Update
    suspend fun update(s: TimeSlot)

    @Query("DELETE FROM time_slots WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM time_slots WHERE semesterId = :semesterId")
    suspend fun deleteForSemester(semesterId: Long)
}

@Dao
interface CourseDao {
    @Transaction
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY name ASC")
    fun withLessons(semesterId: Long): Flow<List<CourseWithLessons>>

    @Transaction
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId")
    suspend fun withLessonsOnce(semesterId: Long): List<CourseWithLessons>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun byId(id: Long): Course?

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId")
    suspend fun bySemesterOnce(semesterId: Long): List<Course>

    @Insert
    suspend fun insert(c: Course): Long

    @Update
    suspend fun update(c: Course)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM lessons WHERE courseId = :courseId")
    suspend fun deleteLessonsOf(courseId: Long)

    @Transaction
    suspend fun deleteCourseWithLessons(id: Long) {
        deleteLessonsOf(id)
        delete(id)
    }
}

@Dao
interface LessonDao {
    @Query("SELECT lessons.* FROM lessons JOIN courses ON lessons.courseId = courses.id WHERE courses.semesterId = :semesterId")
    suspend fun ofSemesterOnce(semesterId: Long): List<Lesson>

    @Insert
    suspend fun insertAll(list: List<Lesson>)

    @Insert
    suspend fun insert(l: Lesson): Long
}
