package com.balsam.timetable.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balsam.timetable.ui.course.CourseEditSheet
import com.balsam.timetable.ui.course.CourseEditViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    onOpenImport: () -> Unit,
    onOpenSlots: () -> Unit,
    onOpenSemesters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCourseList: () -> Unit,
) {
    val vm: TimetableViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is TimetableViewModel.UiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        is TimetableViewModel.UiState.Empty -> EmptyHome(onOpenImport, onOpenSemesters)

        is TimetableViewModel.UiState.Ready -> ReadyHome(
            state = s,
            vm = vm,
            onPrevWeek = { vm.selectWeek((s.week - 1).coerceAtLeast(1)) },
            onNextWeek = { vm.selectWeek((s.week + 1).coerceAtMost(s.semester.totalWeeks)) },
            onSelectWeek = { vm.selectWeek(it) },
            onBackToToday = { vm.selectWeek(null) },
            onOpenImport = onOpenImport,
            onOpenSlots = onOpenSlots,
            onOpenSemesters = onOpenSemesters,
            onOpenSettings = onOpenSettings,
            onOpenCourseList = onOpenCourseList,
        )
    }
}

@Composable
private fun EmptyHome(onOpenImport: () -> Unit, onOpenSemesters: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("欢迎来到苦瓜课表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "完全离线、无需登录。三步开始使用：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "1. 导入课表（没有学期会先帮你建一个，开学日期可核对）\n" +
                "2. 核对开学日期与节次时间\n" +
                "3. 在设置里打开课前提醒",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenImport) { Text("导入课表") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSemesters) { Text("先创建学期") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyHome(
    state: TimetableViewModel.UiState.Ready,
    vm: TimetableViewModel,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectWeek: (Int) -> Unit,
    onBackToToday: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSlots: () -> Unit,
    onOpenSemesters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCourseList: () -> Unit,
) {
    val semester = state.semester
    val semesters by vm.semesters.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var weekMenuOpen by remember { mutableStateOf(false) }
    var semesterDialog by remember { mutableStateOf(false) }
    var sheetCourseId by remember { mutableStateOf<Long?>(null) }
    var sheetPrefill by remember { mutableStateOf<CourseEditViewModel.LessonPrefill?>(null) }
    var shareFile by remember { mutableStateOf<java.io.File?>(null) }
    val shareScope = rememberCoroutineScope()
    val shareContext = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(semester.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        when {
                            state.todayWeek == null -> Text(
                                "假期 / 未开学",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.todayWeek == state.week -> Text(
                                "第${state.week}周 · 本周",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // 浏览非本周时，顶栏直接提供醒目的「回到本周」入口
                            else -> Text(
                                "↩ 回到本周",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = onBackToToday),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { semesterDialog = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "切换学期")
                    }
                },
                actions = {
                    IconButton(onClick = onPrevWeek) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一周")
                    }
                    Box {
                        Text(
                            "第${state.week}周",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .clickable { weekMenuOpen = true }
                                .padding(vertical = 12.dp),
                        )
                        DropdownMenu(
                            expanded = weekMenuOpen,
                            onDismissRequest = { weekMenuOpen = false },
                        ) {
                            (1..semester.totalWeeks).forEach { w ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (w == state.todayWeek) "第${w}周（本周）" else "第${w}周")
                                    },
                                    onClick = {
                                        weekMenuOpen = false
                                        onSelectWeek(w)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onNextWeek) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一周")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("分享课表图片") },
                                onClick = {
                                    menuOpen = false
                                    shareScope.launch(Dispatchers.IO) {
                                        val bmp = TimetableImageExporter.render(
                                            state.semester, state.slots, state.courses,
                                            state.week, state.showWeekend, state.today,
                                        )
                                        shareFile = TimetableImageExporter.writePng(shareContext, bmp)
                                    }
                                },
                            )
                            DropdownMenuItem(text = { Text("导入课表") }, onClick = { menuOpen = false; onOpenImport() })
                            DropdownMenuItem(
                                text = { Text("添加课程") },
                                onClick = {
                                    menuOpen = false
                                    sheetPrefill = null
                                    sheetCourseId = -1L
                                },
                            )
                            DropdownMenuItem(text = { Text("全部课程") }, onClick = { menuOpen = false; onOpenCourseList() })
                            DropdownMenuItem(text = { Text("学期管理") }, onClick = { menuOpen = false; onOpenSemesters() })
                            DropdownMenuItem(text = { Text("节次时间") }, onClick = { menuOpen = false; onOpenSlots() })
                            DropdownMenuItem(text = { Text("设置") }, onClick = { menuOpen = false; onOpenSettings() })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    sheetPrefill = null
                    sheetCourseId = -1L
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加课程") },
            )
        },
    ) { padding ->
        if (state.slots.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有节次时间")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenSlots) { Text("去设置节次时间") }
            }
        } else {
            TimetableGrid(
                state = state,
                modifier = Modifier.padding(padding),
                onPrevWeek = onPrevWeek,
                onNextWeek = onNextWeek,
                onOpenCourse = { id ->
                    sheetPrefill = null
                    sheetCourseId = id
                },
                onAddAt = { prefill ->
                    sheetPrefill = prefill
                    sheetCourseId = -1L
                },
            )
        }
    }

    if (semesterDialog) {
        SemesterSwitchDialog(
            semesters = semesters,
            currentId = semester.id,
            onSelect = {
                vm.setCurrentSemester(it)
                semesterDialog = false
            },
            onBackToToday = {
                onBackToToday()
                semesterDialog = false
            },
            onManage = {
                semesterDialog = false
                onOpenSemesters()
            },
            onDismiss = { semesterDialog = false },
        )
    }

    sheetCourseId?.let { cid ->
        CourseEditSheet(
            courseId = cid,
            prefill = sheetPrefill,
            onDismiss = {
                sheetCourseId = null
                sheetPrefill = null
            },
        )
    }

    shareFile?.let { file ->
        ShareImageSheet(file = file, onDismiss = { shareFile = null })
    }
}
