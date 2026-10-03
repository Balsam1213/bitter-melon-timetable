package com.balsam.timetable.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Semester::class, TimeSlot::class, Course::class, Lesson::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun timeSlotDao(): TimeSlotDao
    abstract fun courseDao(): CourseDao
    abstract fun lessonDao(): LessonDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE time_slots ADD COLUMN isBreak INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 删除手动标记的休息行（午休/晚休改为自动推导），并把节次重新连续编号 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM time_slots WHERE isBreak = 1")
                val cursor = db.query("SELECT DISTINCT semesterId FROM time_slots")
                val semesterIds = mutableListOf<Long>()
                while (cursor.moveToNext()) semesterIds.add(cursor.getLong(0))
                cursor.close()
                semesterIds.forEach { sid ->
                    val r = db.query(
                        "SELECT id FROM time_slots WHERE semesterId = ? ORDER BY orderIndex",
                        arrayOf<Any>(sid),
                    )
                    var n = 1
                    while (r.moveToNext()) {
                        db.execSQL(
                            "UPDATE time_slots SET orderIndex = ? WHERE id = ?",
                            arrayOf<Any>(n, r.getLong(0)),
                        )
                        n++
                    }
                    r.close()
                }
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "timetable.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
