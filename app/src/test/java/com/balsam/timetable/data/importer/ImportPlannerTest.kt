package com.balsam.timetable.data.importer

import com.balsam.timetable.data.importer.ImportPlanner.REASON_BAD_DAY
import com.balsam.timetable.data.importer.ImportPlanner.REASON_BAD_SLOT
import com.balsam.timetable.data.importer.ImportPlanner.REASON_NO_NAME
import com.balsam.timetable.data.importer.ImportPlanner.REASON_WEEKS_EMPTY
import com.balsam.timetable.data.importer.ImportPlanner.REASON_WEEKS_OUT_OF_RANGE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入计划纯逻辑：校验、周次收敛、与已有课程去重。
 * 这里是「导入重复课程不破坏原有数据」承诺的核心防线。
 */
class ImportPlannerTest {

    private fun entry(
        name: String = "高等数学",
        teacher: String = "王老师",
        location: String = "X302",
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
        weeks: Set<Int> = (1..16).toSet(),
        include: Boolean = true,
    ) = ParsedEntry(
        name = name, teacher = teacher, location = location,
        dayOfWeek = day, startSlot = start, endSlot = end,
        weeks = weeks, include = include,
    )

    private fun existing(
        name: String,
        teacher: String = "王老师",
        vararg lessons: ImportPlanner.LessonFingerprint,
    ) = ImportPlanner.ExistingCourse(name, teacher, lessons.toList())

    @Test
    fun `同名同教师合并为一门课 不同教师分开`() {
        val plan = ImportPlanner.plan(
            listOf(
                entry(day = 1, start = 1, end = 2),
                entry(day = 3, start = 3, end = 4),
                entry(name = "高等数学", teacher = "李老师"),
            ),
            existing = emptyList(),
            totalWeeks = 16,
        )
        assertEquals(2, plan.courses.size)
        val wang = plan.courses.first { it.teacher == "王老师" }
        assertEquals(2, wang.lessons.size)
    }

    @Test
    fun `周次超出学期总周数的部分收敛`() {
        val plan = ImportPlanner.plan(
            listOf(entry(weeks = (1..20).toSet())),
            existing = emptyList(),
            totalWeeks = 16,
        )
        assertEquals((1..16).toSet(), plan.courses.single().lessons.single().weeks)
    }

    @Test
    fun `周次全部超界判无效而不是原样保留`() {
        val plan = ImportPlanner.plan(
            listOf(entry(weeks = (18..20).toSet())),
            existing = emptyList(),
            totalWeeks = 16,
        )
        assertTrue(plan.courses.isEmpty())
        assertEquals(listOf(REASON_WEEKS_OUT_OF_RANGE), plan.invalid.map { it.reason })
    }

    @Test
    fun `无学期时不做周次收敛`() {
        val plan = ImportPlanner.plan(
            listOf(entry(weeks = (18..20).toSet())),
            existing = emptyList(),
            totalWeeks = 0,
        )
        assertEquals((18..20).toSet(), plan.courses.single().lessons.single().weeks)
    }

    @Test
    fun `非法星期与节次与空名称与空周次各自报因`() {
        val plan = ImportPlanner.plan(
            listOf(
                entry(name = "  ", day = 1),
                entry(name = "A", day = 0),
                entry(name = "B", day = 8),
                entry(name = "C", start = 3, end = 2),
                entry(name = "D", start = 0),
                entry(name = "E", weeks = emptySet()),
            ),
            existing = emptyList(),
            totalWeeks = 16,
        )
        assertEquals(
            listOf(REASON_NO_NAME, REASON_BAD_DAY, REASON_BAD_DAY, REASON_BAD_SLOT, REASON_BAD_SLOT, REASON_WEEKS_EMPTY),
            plan.invalid.map { it.reason },
        )
        assertTrue(plan.courses.isEmpty())
    }

    @Test
    fun `未勾选的条目直接忽略不算无效`() {
        // 空名称本来会被判无效，但未勾选就跳过、连校验都不做
        val plan = ImportPlanner.plan(
            listOf(entry(name = "  ", include = false)),
            existing = emptyList(),
            totalWeeks = 16,
        )
        assertTrue(plan.invalid.isEmpty())
        assertTrue(plan.courses.isEmpty())
        assertTrue(plan.skippedDuplicates == 0)
    }

    @Test
    fun `与已有课程完全重复的条目跳过`() {
        val old = existing(
            "高等数学", "王老师",
            ImportPlanner.LessonFingerprint(1, 1, 2, (1..16).toSet(), "X302"),
        )
        val plan = ImportPlanner.plan(listOf(entry()), existing = listOf(old), totalWeeks = 16)
        assertTrue(plan.courses.isEmpty())
        assertEquals(1, plan.skippedDuplicates)
        assertTrue(plan.invalid.isEmpty())
    }

    @Test
    fun `同名不同周次不算重复`() {
        val old = existing(
            "高等数学", "王老师",
            ImportPlanner.LessonFingerprint(1, 1, 2, (1..8).toSet(), "X302"),
        )
        val plan = ImportPlanner.plan(listOf(entry()), existing = listOf(old), totalWeeks = 16)
        assertEquals(1, plan.courses.size)
        assertEquals(0, plan.skippedDuplicates)
    }

    @Test
    fun `同名多时段部分重复时只跳过重复的那条`() {
        val old = existing(
            "高等数学", "王老师",
            ImportPlanner.LessonFingerprint(1, 1, 2, (1..16).toSet(), "X302"),
        )
        val plan = ImportPlanner.plan(
            listOf(entry(), entry(day = 3, start = 3, end = 4, location = "X303")),
            existing = listOf(old),
            totalWeeks = 16,
        )
        assertEquals(1, plan.courses.size)
        assertEquals(listOf(3 to 4), plan.courses.single().lessons.map { it.startSlot to it.endSlot })
        assertEquals(1, plan.skippedDuplicates)
    }

    @Test
    fun `名称与教师与地点按 trim 后比对`() {
        val old = existing("高等数学", "王老师", ImportPlanner.LessonFingerprint(1, 1, 2, (1..16).toSet(), "X302"))
        val plan = ImportPlanner.plan(
            listOf(entry(name = " 高等数学 ", location = " X302 ")),
            existing = listOf(old),
            totalWeeks = 16,
        )
        assertTrue(plan.courses.isEmpty())
        assertEquals(1, plan.skippedDuplicates)
    }

    @Test
    fun `组内完全相同的行去重`() {
        val plan = ImportPlanner.plan(
            listOf(entry(), entry()),
            existing = emptyList(),
            totalWeeks = 16,
        )
        assertEquals(1, plan.courses.size)
        assertEquals(1, plan.courses.single().lessons.size)
    }

    @Test
    fun `maxSlotNeeded 取合法条目的最大节次号`() {
        val plan = ImportPlanner.plan(
            listOf(entry(start = 3, end = 4), entry(name = "体育", start = 11, end = 12)),
            existing = emptyList(),
            totalWeeks = 16,
        )
        assertEquals(12, plan.maxSlotNeeded)
    }

    // —— conflictsAmong（预览阶段条目间时间冲突）——

    @Test
    fun `同天同节次的条目互报冲突`() {
        val map = ImportPlanner.conflictsAmong(
            listOf(entry(name = "高等数学", day = 1, start = 1, end = 2), entry(name = "大学英语", day = 1, start = 2, end = 3)),
            totalWeeks = 16,
        )
        assertEquals(setOf(0, 1), map.keys)
        assertEquals(listOf("大学英语（1-16周）"), map[0])
        assertEquals(listOf("高等数学（1-16周）"), map[1])
    }

    @Test
    fun `不同天或节次不相交或周次不相交都不算冲突`() {
        val map = ImportPlanner.conflictsAmong(
            listOf(
                entry(name = "高等数学", day = 1, start = 1, end = 2),
                entry(name = "不同天", day = 2, start = 1, end = 2),
                entry(name = "节次错开", day = 1, start = 3, end = 4),
                entry(name = "周次错开", day = 1, start = 1, end = 2, weeks = (17..20).toSet()),
            ),
            totalWeeks = 16,
        )
        // 周次错开的条目本身被判无效（全部超界），也不参与冲突
        assertTrue(map.isEmpty())
    }

    @Test
    fun `周次部分相交也算冲突`() {
        val map = ImportPlanner.conflictsAmong(
            listOf(
                entry(name = "高等数学", day = 3, start = 1, end = 2, weeks = (1..8).toSet()),
                entry(name = "大学英语", day = 3, start = 1, end = 2, weeks = (8..16).toSet()),
            ),
            totalWeeks = 16,
        )
        assertEquals(setOf(0, 1), map.keys)
        assertEquals(listOf("大学英语（8周）"), map[0])
    }

    @Test
    fun `无效与未勾选的条目不参与冲突`() {
        val map = ImportPlanner.conflictsAmong(
            listOf(
                entry(name = "高等数学", day = 1, start = 1, end = 2),
                entry(name = "  ", day = 1, start = 1, end = 2), // 无效：空名称
                entry(name = "未勾选", day = 1, start = 1, end = 2, include = false),
            ),
            totalWeeks = 16,
        )
        assertTrue(map.isEmpty())
    }

    @Test
    fun `多条冲突逐个列出`() {
        val map = ImportPlanner.conflictsAmong(
            listOf(
                entry(name = "高等数学", day = 5, start = 1, end = 4),
                entry(name = "大学英语", day = 5, start = 1, end = 2),
                entry(name = "大学物理", day = 5, start = 3, end = 4),
            ),
            totalWeeks = 16,
        )
        assertEquals(3, map.size)
        assertEquals(
            setOf("大学英语（1-16周）", "大学物理（1-16周）"),
            map.getValue(0).toSet(),
        )
    }

    // —— shiftWeeksIntoRange（一键平移超界周次）——

    @Test
    fun `全超界区间整体平移到学期末尾`() {
        val e = entry(weeks = (17..20).toSet())
        val shifted = ImportPlanner.shiftWeeksIntoRange(e, 16)
        assertEquals((13..16).toSet(), shifted.weeks)
        assertEquals(null, ImportPlanner.validate(shifted, 16))
    }

    @Test
    fun `稀疏全超界集合平移后保持形状`() {
        val shifted = ImportPlanner.shiftWeeksIntoRange(entry(weeks = setOf(18, 20)), 16)
        assertEquals(setOf(14, 16), shifted.weeks)
    }

    @Test
    fun `未超界与无学期时原样返回`() {
        val e = entry(weeks = (1..16).toSet())
        assertEquals(e, ImportPlanner.shiftWeeksIntoRange(e, 16))
        val out = entry(weeks = (17..20).toSet())
        assertEquals(out, ImportPlanner.shiftWeeksIntoRange(out, 0))
    }
}
