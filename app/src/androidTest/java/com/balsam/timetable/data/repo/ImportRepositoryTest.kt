package com.balsam.timetable.data.repo

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.balsam.timetable.data.db.AppDatabase
import com.balsam.timetable.data.importer.ExportDoc
import com.balsam.timetable.data.importer.ExportEntry
import com.balsam.timetable.data.importer.ExportSemester
import com.balsam.timetable.data.importer.ExportSlot
import com.balsam.timetable.data.importer.ImportPlanner
import com.balsam.timetable.data.importer.ParsedEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * importEntries 的落库行为（in-memory Room）：重复导入跳过、无效条目过滤、
 * 节次自动补行、clearExisting 整体替换。ImportPlanner 的纯逻辑另有 JVM 单测。
 */
@RunWith(AndroidJUnit4::class)
class ImportRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TimetableRepository
    private var semesterId: Long = 0

    private fun entry(
        name: String, teacher: String = "", location: String = "X101",
        day: Int = 1, start: Int = 1, end: Int = 2,
        weeks: Set<Int> = (1..16).toSet(),
    ) = ParsedEntry(
        name = name, teacher = teacher, location = location,
        dayOfWeek = day, startSlot = start, endSlot = end, weeks = weeks,
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TimetableRepository(db)
        semesterId = runBlocking { repo.createSemester("测试学期", "2026-09-07", totalWeeks = 16) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun courseCount(): Int =
        db.courseDao().bySemesterOnce(semesterId).size

    private suspend fun slotCount(): Int =
        db.timeSlotDao().bySemesterOnce(semesterId).size

    @Test
    fun 二次导入同一文件全部跳过且数据不变() = runBlocking {
        val entries = listOf(
            entry("高等数学", teacher = "王老师", day = 1),
            entry("高等数学", teacher = "王老师", day = 3, start = 3, end = 4, location = "X302"),
            entry("大学英语", teacher = "李老师", day = 2),
        )
        val first = repo.importEntries(semesterId, entries, clearExisting = false)
        assertEquals(2, first.importedCourses)
        assertEquals(2, courseCount())

        val second = repo.importEntries(semesterId, entries, clearExisting = false)
        assertEquals(0, second.importedCourses)
        assertEquals(3, second.skippedDuplicates)
        assertEquals(2, courseCount())
    }

    @Test
    fun 部分重复时只补新增的课程() = runBlocking {
        repo.importEntries(semesterId, listOf(entry("高等数学", teacher = "王老师")), clearExisting = false)
        val result = repo.importEntries(
            semesterId,
            listOf(
                entry("高等数学", teacher = "王老师"),
                entry("大学英语", teacher = "李老师", day = 2),
            ),
            clearExisting = false,
        )
        assertEquals(1, result.importedCourses)
        assertEquals(1, result.skippedDuplicates)
        assertEquals(2, courseCount())
    }

    @Test
    fun 无效条目被过滤且不影响其余导入() = runBlocking {
        val result = repo.importEntries(
            semesterId,
            listOf(
                entry("高等数学", teacher = "王老师"),
                entry("坏星期", day = 0),
                entry("", day = 2),
                entry("坏周次", day = 3, weeks = (17..20).toSet()),
            ),
            clearExisting = false,
        )
        assertEquals(1, result.importedCourses)
        assertEquals(3, result.invalid.size)
        assertEquals(
            setOf(
                ImportPlanner.REASON_BAD_DAY,
                ImportPlanner.REASON_NO_NAME,
                ImportPlanner.REASON_WEEKS_OUT_OF_RANGE,
            ),
            result.invalid.map { it.reason }.toSet(),
        )
        assertEquals(1, courseCount())
    }

    @Test
    fun 节次不足时按45分钟顺延自动补行() = runBlocking {
        val before = slotCount()
        assertEquals(13, before) // 默认 13 节模板
        repo.importEntries(
            semesterId,
            listOf(entry("晚课", day = 1, start = 15, end = 16)),
            clearExisting = false,
        )
        val slots = db.timeSlotDao().bySemesterOnce(semesterId).sortedBy { it.orderIndex }
        assertEquals(16, slots.size)
        // 第 14 节从原最后一节的结束时间起顺延
        assertEquals("21:35", slots[13].startTime)
        // 新行按 orderIndex 连续编号
        assertEquals(listOf(1, 2, 3), slots.take(3).map { it.orderIndex })
    }

    @Test
    fun clearExisting整体替换() = runBlocking {
        repo.importEntries(
            semesterId,
            listOf(entry("高等数学", teacher = "王老师"), entry("大学英语", teacher = "李老师", day = 2)),
            clearExisting = false,
        )
        repo.importEntries(semesterId, listOf(entry("物理", day = 5)), clearExisting = true)
        assertEquals(1, courseCount())
        assertEquals("物理", db.courseDao().bySemesterOnce(semesterId).single().name)
    }

    @Test
    fun 周次超学期总周数的部分收敛入库() = runBlocking {
        repo.importEntries(
            semesterId,
            listOf(entry("高等数学", teacher = "王老师", weeks = (1..20).toSet())),
            clearExisting = false,
        )
        val lessons = db.courseDao().withLessonsOnce(semesterId).single().lessons
        assertEquals(1, lessons.size)
        assertEquals((1..16).toSet(), lessons.single().weekSet)
        assertTrue(lessons.single().weeks == (1..16).joinToString(","))
    }

    // —— restoreDoc（从备份恢复）——

    private fun backupDoc(
        name: String = "备份学期",
        startDate: String = "2027-03-01",
        totalWeeks: Int = 18,
        slots: List<Pair<String, String>> = listOf("08:30" to "09:15", "09:25" to "10:10"),
        entries: List<ExportEntry> = listOf(
            ExportEntry.fromWeeks(
                name = "数据结构", teacher = "赵老师", location = "Y202",
                day = 2, startSlot = 1, endSlot = 2, weekSet = (1..18).toSet(),
            ),
        ),
    ) = ExportDoc(
        semester = ExportSemester(name, startDate, totalWeeks),
        slots = slots.map { ExportSlot(it.first, it.second) },
        entries = entries,
    )

    @Test
    fun 合并恢复保留现有课程且重复跳过() = runBlocking {
        repo.importEntries(
            semesterId,
            listOf(
                entry("数据结构", teacher = "赵老师", location = "Y202", day = 2),
                entry("操作系统", teacher = "钱老师", day = 3),
            ),
            clearExisting = false,
        )
        val result = repo.restoreDoc(semesterId, backupDoc(), replaceAll = false)
        assertEquals(0, result.importedCourses)
        assertEquals(1, result.skippedDuplicates)
        assertEquals(2, courseCount())
        // 学期字段不被合并恢复改动
        assertEquals("测试学期", db.semesterDao().byId(semesterId)!!.name)
        assertEquals(13, slotCount())
    }

    @Test
    fun 完整恢复清空课程并替换学期字段与节次() = runBlocking {
        repo.importEntries(
            semesterId,
            listOf(entry("高等数学", teacher = "王老师"), entry("大学英语", teacher = "李老师", day = 2)),
            clearExisting = false,
        )
        val result = repo.restoreDoc(semesterId, backupDoc(), replaceAll = true)
        assertEquals(1, result.importedCourses)
        assertEquals(1, courseCount())
        val sem = db.semesterDao().byId(semesterId)!!
        assertEquals("备份学期", sem.name)
        assertEquals("2027-03-01", sem.startDate)
        assertEquals(18, sem.totalWeeks)
        val slots = db.timeSlotDao().bySemesterOnce(semesterId).sortedBy { it.orderIndex }
        assertEquals(listOf("08:30", "09:25"), slots.map { it.startTime })
        assertEquals("数据结构", db.courseDao().withLessonsOnce(semesterId).single().course.name)
    }

    @Test
    fun 完整恢复无节次数据时保留现有节次表() = runBlocking {
        val before = slotCount()
        repo.restoreDoc(
            semesterId,
            backupDoc(slots = emptyList(), entries = listOf(ExportEntry.fromWeeks("线性代数", "", "Z1", 1, 1, 2, (1..10).toSet()))),
            replaceAll = true,
        )
        assertEquals(before, slotCount())
        assertEquals(1, courseCount())
    }

    @Test
    fun 恢复失败时事务回滚原有数据不动() = runBlocking {
        repo.importEntries(
            semesterId,
            listOf(entry("高等数学", teacher = "王老师"), entry("大学英语", teacher = "李老师", day = 2)),
            clearExisting = false,
        )
        try {
            repo.restoreDoc(999L, backupDoc(), replaceAll = true)
            fail("目标学期不存在应当抛异常")
        } catch (expected: IllegalStateException) {
            // 目标学期不存在 → 整个事务回滚
        }
        assertEquals(2, courseCount())
    }
}
