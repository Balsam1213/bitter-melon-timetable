package com.balsam.timetable.ui.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.balsam.timetable.Graph
import com.balsam.timetable.data.db.AppDatabase
import com.balsam.timetable.data.model.SemesterDefaults
import com.balsam.timetable.data.model.ColorTheme
import com.balsam.timetable.ui.theme.TimetableTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首启向导的 Compose UI 测试（内存库空学期，不经过 MainActivity）。
 * 不点「完成，进入课表」——那会写真实 DataStore 的 onboarding_done 标记。
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var vm: OnboardingViewModel

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        Graph.swapForTest(db) // 空库：无学期，向导应从第一步开始
        vm = OnboardingViewModel()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun setContent() {
        compose.setContent {
            TimetableTheme(colorTheme = ColorTheme.PURPLE) {
                OnboardingScreen(
                    onFinished = {},
                    onOpenImport = {},
                    onOpenWebImport = {},
                    onOpenRestore = {},
                    onOpenCourseEdit = {},
                    vm = vm,
                )
            }
        }
    }

    @Test
    fun 第一步预填默认值并可创建学期进入导入方式() {
        setContent()
        compose.onNodeWithText("① 创建学期").assertExists()
        compose.onNodeWithText("创建学期，下一步").assertExists()
        compose.onNodeWithText("跳过，先看看课表").assertExists()
        // 预填口径：名称按今天推学年学期，开学日为本周一，总周数 20（状态断言，避开输入框语义细节）
        assertEquals(SemesterDefaults.nameSuggestion(), vm.ui.value.semesterName)
        assertEquals(SemesterDefaults.startSuggestion(), vm.ui.value.startDate)
        assertEquals("20", vm.ui.value.totalWeeks)

        compose.onNodeWithText("创建学期，下一步").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Excel / CSV 文件").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("课表截图识别").assertExists()
        compose.onNodeWithText("教务网导入（需联网）").assertExists()
        compose.onNodeWithText("JSON 备份恢复").assertExists()
        compose.onNodeWithText("手动添加课程").assertExists()
        compose.onNodeWithText("完成导入，进入课表").assertExists()
        // 内存库核对：学期已建且设为当前
        runBlocking {
            val semesters = Graph.repository.semesters().first()
            assertEquals(1, semesters.size)
            assertEquals(true, semesters.single().isCurrent)
            assertEquals(SemesterDefaults.nameSuggestion(), semesters.single().name)
        }
    }

    @Test
    fun 就绪页展示课表与提醒引导() {
        setContent()
        vm.goToReady()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("一切就绪！").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("开启课前提醒").assertExists()
        compose.onNodeWithText("完成，进入课表").assertExists()
        // 空库：未建学期，就绪页走「已跳过创建学期」分支
        compose.onNodeWithText("已跳过创建学期，进入课表后可随时在「学期管理」新建。").assertExists()
    }
}
