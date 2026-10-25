package com.balsam.timetable.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.balsam.timetable.data.model.WeekPattern
import com.balsam.timetable.data.model.WeeksCodec

@Database(
    entities = [Semester::class, TimeSlot::class, Course::class, Lesson::class],
    version = 4,
    exportSchema = true,
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

        /**
         * v4：课程周次从「区间 + 每周/单周/双周」改为任意周集合单列（lessons.weeks），
         * 旧数据由 WeeksCodec.fromRange 逐行展开；顺带删除 time_slots 废弃的 isBreak 列。
         * 两个表都走「建新表 → 拷贝 → 删旧表 → 改名」（SQLite DROP COLUMN 版本兼容）。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lessons_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`courseId` INTEGER NOT NULL, " +
                        "`dayOfWeek` INTEGER NOT NULL, " +
                        "`startSlot` INTEGER NOT NULL, " +
                        "`endSlot` INTEGER NOT NULL, " +
                        "`weeks` TEXT NOT NULL, " +
                        "`location` TEXT NOT NULL)",
                )
                val cursor = db.query(
                    "SELECT id, courseId, dayOfWeek, startSlot, endSlot, startWeek, endWeek, weekPattern, location FROM lessons",
                )
                val insert = db.compileStatement(
                    "INSERT INTO lessons_new (id, courseId, dayOfWeek, startSlot, endSlot, weeks, location) VALUES (?,?,?,?,?,?,?)",
                )
                while (cursor.moveToNext()) {
                    val weeks = WeeksCodec.format(
                        WeeksCodec.fromRange(
                            cursor.getInt(5),
                            cursor.getInt(6),
                            WeekPattern.from(cursor.getString(7)),
                        ),
                    )
                    insert.bindLong(1, cursor.getLong(0))
                    insert.bindLong(2, cursor.getLong(1))
                    insert.bindLong(3, cursor.getLong(2))
                    insert.bindLong(4, cursor.getLong(3))
                    insert.bindLong(5, cursor.getLong(4))
                    insert.bindString(6, weeks)
                    insert.bindString(7, cursor.getString(8) ?: "")
                    insert.executeInsert()
                }
                cursor.close()
                db.execSQL("DROP TABLE lessons")
                db.execSQL("ALTER TABLE lessons_new RENAME TO lessons")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `time_slots_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`semesterId` INTEGER NOT NULL, " +
                        "`orderIndex` INTEGER NOT NULL, " +
                        "`startTime` TEXT NOT NULL, " +
                        "`endTime` TEXT NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO time_slots_new (id, semesterId, orderIndex, startTime, endTime) " +
                        "SELECT id, semesterId, orderIndex, startTime, endTime FROM time_slots",
                )
                db.execSQL("DROP TABLE time_slots")
                db.execSQL("ALTER TABLE time_slots_new RENAME TO time_slots")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "timetable.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
