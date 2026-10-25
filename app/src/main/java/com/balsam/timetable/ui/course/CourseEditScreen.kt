package com.balsam.timetable.ui.course

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balsam.timetable.data.model.ReminderMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(courseId: Long, onBack: () -> Unit) {
    val vm: CourseEditViewModel = viewModel(key = "course_edit_$courseId")
    val state by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(courseId) { vm.load(courseId) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "添加课程" else "编辑课程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.semester == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有当前学期，请先在「学期管理」中创建", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> CourseForm(state, vm, padding, onRequestDelete = { showDeleteConfirm = true })
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除课程") },
            text = { Text("确定删除「${state.courseName}」及其全部时间段吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.delete(onBack)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
        )
    }
}

/** 课程编辑表单：全屏路由页与底部弹层共用 */
@Composable
internal fun CourseForm(
    state: CourseEditViewModel.UiState,
    vm: CourseEditViewModel,
    padding: PaddingValues,
    onRequestDelete: () -> Unit,
) {
    val nameFocus = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = state.courseName,
            onValueChange = { v -> vm.update { it.copy(courseName = v) } },
            label = { Text("课程名称") },
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            singleLine = true,
        )
        LaunchedEffect(Unit) {
            if (state.isNew) nameFocus.requestFocus()
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.teacher,
            onValueChange = { v -> vm.update { it.copy(teacher = v) } },
            label = { Text("教师") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Text("课程颜色", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        ColorPalette(state.colorIdx) { i -> vm.update { it.copy(colorIdx = i) } }
        Spacer(Modifier.height(16.dp))
        Text("上课时间", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))

        state.lessons.forEachIndexed { index, lesson ->
            LessonCard(
                index = index,
                lesson = lesson,
                slotCount = state.slotCount,
                totalWeeks = state.semester?.totalWeeks ?: 16,
                onChange = { nl ->
                    vm.update { s ->
                        s.copy(lessons = s.lessons.mapIndexed { i, l -> if (i == index) nl else l })
                    }
                },
                onRemove = {
                    vm.update { s ->
                        if (s.lessons.size > 1) s.copy(lessons = s.lessons.filterIndexed { i, _ -> i != index }) else s
                    }
                },
                canRemove = state.lessons.size > 1,
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = {
            vm.update { s ->
                s.copy(
                    lessons = s.lessons + CourseEditViewModel.FormLesson(
                        weekSet = (1..(s.semester?.totalWeeks ?: 16)).toSet(),
                    )
                )
            }
        }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(" 添加时间段")
        }
        Spacer(Modifier.height(16.dp))
        Text("课前提醒", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            ReminderMode.entries.forEach { m ->
                val selected = state.reminderMode == m
                FilterChipStatic(
                    label = m.label,
                    selected = selected,
                    onClick = { vm.update { it.copy(reminderMode = m) } },
                )
            }
        }
        Text(
            when (state.reminderMode) {
                ReminderMode.GLOBAL -> "跟随设置里的全局提醒"
                ReminderMode.OFF -> "这门课不提醒"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.note,
            onValueChange = { v -> vm.update { it.copy(note = v) } },
            label = { Text("备注") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.save() },
            enabled = state.courseName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("保存", fontSize = 16.sp) }
        if (!state.isNew) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRequestDelete, modifier = Modifier.fillMaxWidth()) {
                Text("删除课程", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (state.conflictWith.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.update { it.copy(conflictWith = emptyList()) } },
            title = { Text("检测到时间冲突") },
            text = {
                Column {
                    Text("该时间段与以下课程重叠：", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    state.conflictWith.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.forceSave() }) { Text("仍然保存") }
            },
            dismissButton = {
                TextButton(onClick = { vm.update { it.copy(conflictWith = emptyList()) } }) { Text("返回修改") }
            },
        )
    }
}
