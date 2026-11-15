package com.balsam.timetable.ui.restore

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.balsam.timetable.Graph
import com.balsam.timetable.data.backup.AutoBackup
import com.balsam.timetable.data.db.AppDatabase
import com.balsam.timetable.data.importer.ExportDoc
import com.balsam.timetable.data.importer.ExportEntry
import com.balsam.timetable.data.importer.ExportSemester
import com.balsam.timetable.data.importer.ExportSlot
import com.balsam.timetable.data.importer.ParsedEntry
import com.balsam.timetable.data.model.ColorTheme
import com.balsam.timetable.ui.theme.TimetableTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 从备份恢复页的 Compose UI 测试（内存库 + Graph.swapForTest，不经过 MainActivity）。
 * 备份列表依赖设备真实 MediaStore，内容不确定——用例一律走 vm.select 注入备份进入预览，
 * 断言只依赖内存库与注入内容；完整恢复不真执行（快照副作用由事务级 androidTest 覆盖）。
 */
@RunWith(AndroidJUnit4::class)
class RestoreScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var vm: RestoreViewModel

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        Graph.swapForTest(db)
        runBlocking {
            val id = Graph.repository.createSemester("当前学期", "2026-09-07", totalWeeks = 16, setCurrent = true)
            Graph.repository.importEntries(
                id,
                // 显式 startSlot/endSlot：与 backupMeta() 的备份指纹（1-2 节）一致，否则判重不命中
                listOf(
                    ParsedEntry(
                        name = "现有课程", teacher = "张老师", location = "X101",
                        dayOfWeek = 1, startSlot = 1, endSlot = 2,
                    ),
                ),
                clearExisting = false,
            )
        }
        // 构造即触发 init{refresh()}（扫描真实 MediaStore + 内存库学期列表），随后 select 注入
        vm = RestoreViewModel()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 备份含两条：一条与库内「现有课程」完全重复，一条新增 */
    private fun backupMeta() = AutoBackup.BackupMeta(
        fileName = "课程表备份-测试.json",
        timestampMillis = System.currentTimeMillis(),
        sizeBytes = 600,
        doc = ExportDoc(
            semester = ExportSemester("备份学期", "2027-03-01", 18),
            slots = listOf(ExportSlot("08:30", "09:15"), ExportSlot("09:25", "10:10")),
            entries = listOf(
                ExportEntry.fromWeeks(
                    "现有课程", "张老师", "X101", day = 1, startSlot = 1, endSlot = 2, weekSet = (1..16).toSet(),
                ),
                ExportEntry.fromWeeks(
                    "数据结构", "赵老师", "Y202", day = 2, startSlot = 1, endSlot = 2, weekSet = (1..18).toSet(),
                ),
            ),
        ),
        parseError = null,
    )

    private fun setContent() {
        compose.setContent {
            TimetableTheme(colorTheme = ColorTheme.PURPLE) {
                RestoreScreen(onBack = {}, vm = vm)
            }
        }
    }

    @Test
    fun 恢复预览展示备份内容两种模式与目标学期() {
        vm.select(backupMeta())
        setContent()
        compose.onNodeWithText("备份内容").assertExists()
        compose.onNodeWithText("学期：备份学期").assertExists()
        compose.onNodeWithText("开学日期：2027-03-01（第 1 周周一）").assertExists()
        compose.onNodeWithText("总周数：18").assertExists()
        compose.onNodeWithText("课程记录：2 条").assertExists()
        compose.onNodeWithText("节次时间：2 节").assertExists()
        compose.onNodeWithText("合并恢复").assertExists()
        compose.onNodeWithText("完整恢复").assertExists()
        compose.onNodeWithText("当前学期（当前）").assertExists()
        compose.onNodeWithText("合并恢复到「当前学期」").assertExists()
    }

    @Test
    fun 合并恢复执行后显示结果卡且重复跳过() {
        vm.select(backupMeta())
        setContent()
        compose.onNodeWithText("合并恢复到「当前学期」").performClick()
        // 先等执行完成（结果或错误任一），失败时断言消息会带上 error 内容便于诊断
        compose.waitUntil(10_000) {
            vm.ui.value.result != null || vm.ui.value.error != null
        }
        assertEquals(null, vm.ui.value.error)
        assertEquals(1, vm.ui.value.result?.importedCourses) // 重复 1 条被跳过，只新增数据结构
        assertEquals(1, vm.ui.value.result?.skippedDuplicates)
        compose.onNodeWithText("✅ 恢复完成，已写入 1 门课程", substring = true).assertExists()
        compose.onNodeWithText("↺ 跳过 1 条与现有课程完全重复的条目", substring = true).assertExists()
        compose.onNodeWithText("返回备份列表").assertExists()
        // 内存库核对：合并后 2 门课、学期字段未被改动
        runBlocking {
            assertEquals(2, Graph.repository.coursesOnce(semesterIdForCurrent()).size)
            assertEquals("当前学期", Graph.repository.semesterById(semesterIdForCurrent())!!.name)
        }
    }

    @Test
    fun 完整恢复需二次确认且可取消() {
        vm.select(backupMeta())
        setContent()
        compose.onNodeWithText("完整恢复").performClick()
        compose.onNodeWithText(
            "⚠ 完整恢复将清空「当前学期」现有课程并按备份重建，学期名称、开学日期、总周数与节次时间也会被替换。",
        ).assertExists()
        compose.onNodeWithText("完整恢复到「当前学期」").performClick()
        compose.onNodeWithText("确认完整恢复").assertExists()
        compose.onNodeWithText("清空并恢复").assertExists()
        compose.onNodeWithText("取消").performClick()
        assertEquals(0, compose.onAllNodesWithText("清空并恢复").fetchSemanticsNodes().size)
        compose.onNodeWithText("备份内容").assertExists() // 回到预览
    }

    private fun semesterIdForCurrent(): Long = runBlocking {
        Graph.repository.currentSemesterOnce()!!.id
    }
}
