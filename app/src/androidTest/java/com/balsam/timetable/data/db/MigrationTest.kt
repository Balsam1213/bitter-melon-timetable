package com.balsam.timetable.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room v3→v4 迁移测试（v1.1 → v1.2 现役升级路径）。
 *
 * v1/v2 只存在于未发布的开发期（初始提交即为 version 3），无 schema 可考，
 * 故只验证 3→4：lessons 的「区间+单双周」三列展开为 weeks 周集合单列、
 * time_slots 删除废弃 isBreak 列。3.json 由 v1.1 历史提交（38abc87）开启
 * exportSchema 重新生成收割而来。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate3To4WeeksExpanded() {
        val db = helper.createDatabase(TEST_DB, 3)

        db.execSQL(
            "INSERT INTO semesters (id, name, startDate, totalWeeks, isCurrent, isArchived) " +
                "VALUES (1, '2026-2027-1', '2026-09-07', 20, 1, 0)",
        )
        db.execSQL(
            "INSERT INTO time_slots (id, semesterId, orderIndex, startTime, endTime, isBreak) " +
                "VALUES (1, 1, 1, '08:00', '08:45', 0)",
        )
        db.execSQL(
            "INSERT INTO time_slots (id, semesterId, orderIndex, startTime, endTime, isBreak) " +
                "VALUES (2, 1, 2, '08:55', '09:40', 0)",
        )
        db.execSQL(
            "INSERT INTO courses (id, semesterId, name, teacher, colorArgb, note, reminderMode) " +
                "VALUES (1, 1, '高等数学', '王老师', 4280997618, '', 'GLOBAL')",
        )
        // 三种周次形态：每周 / 单周 / 双周
        db.execSQL(
            "INSERT INTO lessons (id, courseId, dayOfWeek, startSlot, endSlot, startWeek, endWeek, weekPattern, location) " +
                "VALUES (1, 1, 1, 1, 2, 1, 17, 'EVERY', 'A101')",
        )
        db.execSQL(
            "INSERT INTO lessons (id, courseId, dayOfWeek, startSlot, endSlot, startWeek, endWeek, weekPattern, location) " +
                "VALUES (2, 1, 3, 1, 2, 4, 6, 'ODD', 'B202')",
        )
        db.execSQL(
            "INSERT INTO lessons (id, courseId, dayOfWeek, startSlot, endSlot, startWeek, endWeek, weekPattern, location) " +
                "VALUES (3, 1, 5, 3, 4, 2, 10, 'EVEN', '')",
        )
        db.close()

        // 迁移并按 4.json 校验表结构（含 isBreak 列已删除）
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4)

        val weeks = mutableListOf<String>()
        migrated.query("SELECT weeks FROM lessons ORDER BY id").use { c ->
            while (c.moveToNext()) weeks.add(c.getString(0))
        }
        // 1-17 每周 → 1..17；4-6 单周 → {5}；2-10 双周 → 偶数周
        assertEquals(
            listOf((1..17).joinToString(","), "5", listOf(2, 4, 6, 8, 10).joinToString(",")),
            weeks,
        )

        // 其余列原样保留
        migrated.query("SELECT dayOfWeek, startSlot, endSlot, location FROM lessons WHERE id = 2").use { c ->
            c.moveToFirst()
            assertEquals(3, c.getInt(0))
            assertEquals(1, c.getInt(1))
            assertEquals(2, c.getInt(2))
            assertEquals("B202", c.getString(3))
        }

        // 废弃枚举值兜底由 ReminderMode.from 负责（读取层），迁移本身原样拷贝
        migrated.query("SELECT COUNT(*) FROM time_slots").use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
