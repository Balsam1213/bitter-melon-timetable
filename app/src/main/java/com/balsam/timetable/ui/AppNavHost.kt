package com.balsam.timetable.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.balsam.timetable.ui.course.CourseEditScreen
import com.balsam.timetable.ui.importflow.ImportScreen
import com.balsam.timetable.ui.semester.SemesterScreen
import com.balsam.timetable.ui.settings.SettingsScreen
import com.balsam.timetable.ui.slots.TimeSlotsScreen
import com.balsam.timetable.ui.timetable.TimetableScreen

object Routes {
    const val HOME = "home"
    const val COURSE = "course?courseId={courseId}"
    const val COURSES = "courses"
    const val SLOTS = "slots"
    const val SEMESTERS = "semesters"
    const val SETTINGS = "settings"
    const val IMPORT = "import?fresh={fresh}"
    const val WEB_IMPORT = "webimport"

    fun course(courseId: Long = -1L) = "course?courseId=$courseId"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            TimetableScreen(
                onOpenImport = { nav.navigate(Routes.IMPORT) },
                onOpenSlots = { nav.navigate(Routes.SLOTS) },
                onOpenSemesters = { nav.navigate(Routes.SEMESTERS) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenCourseList = { nav.navigate(Routes.COURSES) },
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
            )
        }
        composable(Routes.WEB_IMPORT) {
            com.balsam.timetable.ui.webimport.WebImportScreen(
                onBack = { nav.popBackStack() },
                onParsed = {
                    nav.navigate(Routes.IMPORT.replace("import?fresh={fresh}", "import?fresh=false")) {
                        popUpTo(Routes.HOME)
                    }
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
            )
        }
    }
}
