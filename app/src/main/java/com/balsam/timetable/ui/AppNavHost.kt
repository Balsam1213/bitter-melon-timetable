package com.balsam.timetable.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.balsam.timetable.Graph
import com.balsam.timetable.ui.course.CourseEditScreen
import com.balsam.timetable.ui.importflow.ImportScreen
import com.balsam.timetable.ui.onboarding.OnboardingScreen
import com.balsam.timetable.ui.semester.SemesterScreen
import com.balsam.timetable.ui.settings.SettingsScreen
import com.balsam.timetable.ui.slots.TimeSlotsScreen
import com.balsam.timetable.ui.timetable.TimetableScreen
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

object Routes {
    const val HOME = "home"
    const val COURSE = "course?courseId={courseId}"
    const val COURSES = "courses"
    const val SLOTS = "slots"
    const val SEMESTERS = "semesters"
    const val SETTINGS = "settings"
    const val HOLIDAYS = "holidays"
    const val IMPORT = "import?fresh={fresh}"
    const val WEB_IMPORT = "webimport"
    const val RESTORE = "restore"
    const val ONBOARDING = "onboarding"

    fun course(courseId: Long = -1L) = "course?courseId=$courseId"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    // 起始路由：新装用户（未完成首启向导且还没有任何学期）先进引导，其余直接进课表。
    // 只判定一次；中途从引导返回/杀进程重启都会因为「已有学期或已标记」自然落回 HOME。
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        // DataStore 读与 Room 首发射并行：串行会把两者之和算进首帧等待
        coroutineScope {
            val settingsDeferred = async { Graph.settingsRepository.onboardingDone() }
            val semestersDeferred = async { Graph.repository.semesters().first().isEmpty() }
            val goOnboarding = !settingsDeferred.await() && semestersDeferred.await()
            start = if (goOnboarding) Routes.ONBOARDING else Routes.HOME
        }
    }
    val currentStart = start
    if (currentStart == null) {
        // DataStore 首读只有几毫秒，首帧空白可接受（主题同样异步加载）
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
        return
    }
    NavHost(navController = nav, startDestination = currentStart) {
        composable(Routes.HOME) {
            TimetableScreen(
                onOpenImport = { nav.navigate(Routes.IMPORT) },
                onOpenSlots = { nav.navigate(Routes.SLOTS) },
                onOpenSemesters = { nav.navigate(Routes.SEMESTERS) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenCourseList = { nav.navigate(Routes.COURSES) },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onOpenImport = { nav.navigate(Routes.IMPORT.replace("import?fresh={fresh}", "import?fresh=true")) },
                onOpenWebImport = { nav.navigate(Routes.WEB_IMPORT) },
                onOpenRestore = { nav.navigate(Routes.RESTORE) },
                onOpenCourseEdit = { nav.navigate(Routes.course()) },
            )
        }
        composable(Routes.COURSES) {
            com.balsam.timetable.ui.course.CourseListScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.COURSE,
            arguments = listOf(navArgument("courseId") {
                type = NavType.LongType
                defaultValue = -1L
            }),
        ) { entry ->
            CourseEditScreen(
                courseId = entry.arguments?.getLong("courseId") ?: -1L,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SLOTS) { TimeSlotsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SEMESTERS) {
            com.balsam.timetable.ui.semester.SemesterScreen(
                onBack = { nav.popBackStack() },
                onOpenImport = { nav.navigate(Routes.IMPORT.replace("import?fresh={fresh}", "import?fresh=true")) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenImport = { nav.navigate(Routes.IMPORT) },
                onOpenHolidays = { nav.navigate(Routes.HOLIDAYS) },
                onOpenRestore = { nav.navigate(Routes.RESTORE) },
            )
        }
        composable(Routes.RESTORE) {
            com.balsam.timetable.ui.restore.RestoreScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.HOLIDAYS) {
            com.balsam.timetable.ui.holidays.HolidayCalendarScreen(
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.WEB_IMPORT) {
            com.balsam.timetable.ui.webimport.WebImportScreen(
                onBack = { nav.popBackStack() },
                onParsed = { entries ->
                    // 解析结果经导航 SavedStateHandle 一次性传递（v1.6 起，替代 WebImportBus 全局变量）
                    nav.navigate(Routes.IMPORT.replace("import?fresh={fresh}", "import?fresh=false")) {
                        popUpTo(Routes.HOME)
                    }
                    nav.currentBackStackEntry?.savedStateHandle?.set("import_handoff", entries)
                },
            )
        }
        composable(
            route = Routes.IMPORT,
            arguments = listOf(navArgument("fresh") {
                type = NavType.BoolType
                defaultValue = false
            }),
        ) { entry ->
            ImportScreen(
                freshSemester = entry.arguments?.getBoolean("fresh") ?: false,
                onBack = { nav.popBackStack() },
                onManualAdd = { nav.navigate(Routes.course()) },
                onOpenWebImport = { nav.navigate(Routes.WEB_IMPORT) },
                handoff = entry.savedStateHandle.remove<List<com.balsam.timetable.data.importer.ParsedEntry>>("import_handoff"),
            )
        }
    }
}
