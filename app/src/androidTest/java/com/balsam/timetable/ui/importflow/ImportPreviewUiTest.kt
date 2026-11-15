package com.balsam.timetable.ui.importflow

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.balsam.timetable.Graph
import com.balsam.timetable.data.db.AppDatabase
import com.balsam.timetable.data.importer.ParsedEntry
import com.balsam.timetable.data.model.ColorTheme
import com.balsam.timetable.ui.theme.TimetableTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 导入预览页（确认课程步骤）的 Compose UI 测试：无效条目标注、条目间冲突标注，
 * 以及 font_scale 1.5（10-26 大字体教训）下的自动化回归线——关键节点仍可达、不崩。
 * 走 vm.loadExternal 注入，不依赖文件/OCR/教务网来源。
 */
@RunWith(AndroidJUnit4::class)
class ImportPreviewUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var vm: ImportViewModel

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        Graph.swapForTest(db)
        runBlocking {
            // loadExternal 的默认周次取当前学期总周数
            Graph.repository.createSemester("当前学期", "2026-09-07", totalWeeks = 16, setCurrent = true)
        }
        vm = ImportViewModel()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 正常×1 + 无效×1（星期 0）+ 互相冲突×2（周三 1-2 节 与 2-3 节） */
    private fun entries() = listOf(
        ParsedEntry(name = "高等数学", teacher = "王老师", location = "X302", dayOfWeek = 1, startSlot = 1, endSlot = 2),
        ParsedEntry(name = "坏星期", dayOfWeek = 0),
        ParsedEntry(name = "大学英语", teacher = "李老师", location = "X101", dayOfWeek = 3, startSlot = 1, endSlot = 2),
        ParsedEntry(name = "大学物理", teacher = "周老师", location = "X201", dayOfWeek = 3, startSlot = 2, endSlot = 3),
    )

    private fun setContent(fontScale: Float = 1f) {
        compose.setContent {
            TimetableTheme(colorTheme = ColorTheme.PURPLE) {
                val d = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = d.density, fontScale = fontScale),
                ) {
                    ImportScreen(freshSemester = true, onBack = {}, onManualAdd = {}, onOpenWebImport = {}, vm = vm)
                }
            }
        }
    }

    @Test
    fun 预览页标注无效与冲突条目且无效默认不勾选() {
        // 先 setContent 再注入：ImportScreen 首帧有 LaunchedEffect(Unit){vm.reset()}，
        // 注入若发生在其之前会被清空（时序竞态）
        setContent()
        vm.loadExternal(entries())
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("共 4 条记录", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // 无效条目：红字原因 + 默认不勾选 → 按钮计数只剩 3 门
        compose.onNodeWithText("已默认不勾选", substring = true).assertExists()
        // 冲突条目：两条各有一条精确标注（onNodeWithText 要求唯一匹配）
        compose.onNodeWithText("⊗ 与 大学英语（1-16周） 时间冲突").assertExists()
        compose.onNodeWithText("⊗ 与 大学物理（1-16周） 时间冲突").assertExists()
        compose.onNodeWithText(
            "⚠ 有 2 条记录与其他所选课程时间冲突（同一天且节次、周次重叠），请确认是否真的要同时保留：",
            substring = true,
        ).assertExists()
        compose.onNodeWithText("导入所选 3 门课程").assertExists()
        // 入库前状态一致
        assertEquals(3, vm.ui.value.entries.count { it.include })
    }

    @Test
    fun 大字体1点5倍下预览关键元素仍可达() {
        setContent(fontScale = 1.5f)
        vm.loadExternal(entries())
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("共 4 条记录", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("已默认不勾选", substring = true).performScrollTo().assertExists()
        // 精确文本：子串「时间冲突」在汇总行与两条标注上共 3 处，无法唯一定位
        compose.onNodeWithText("⊗ 与 大学英语（1-16周） 时间冲突").performScrollTo().assertExists()
        compose.onNodeWithText("⊗ 与 大学物理（1-16周） 时间冲突").performScrollTo().assertExists()
        compose.onNodeWithText("导入所选 3 门课程").performScrollTo().assertExists()
    }

    @Test
    fun 一键平移超界周次后红字消失并自动勾选() {
        setContent()
        vm.loadExternal(listOf(ParsedEntry(name = "错位课", dayOfWeek = 1, startSlot = 1, endSlot = 2, weeks = (17..20).toSet())))
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("一键平移超出学期总周数的周次").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("一键平移超出学期总周数的周次").performClick()
        compose.waitUntil(5_000) { !vm.hasOutOfRangeWeeks() }
        compose.onNodeWithText("一键平移超出学期总周数的周次").assertDoesNotExist()
        // 平移进学期内（17-20、16 周学期 → 13-16）且自动勾选
        assertEquals((13..16).toSet(), vm.ui.value.entries[0].weeks)
        assertEquals(true, vm.ui.value.entries[0].include)
    }
}
