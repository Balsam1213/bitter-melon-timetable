package com.balsam.timetable.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balsam.timetable.data.model.CourseColors
import com.balsam.timetable.data.model.ReminderMode
import com.balsam.timetable.data.model.DAY_LABELS
import com.balsam.timetable.data.model.WeekPattern
import com.balsam.timetable.ui.components.DayDropdown
import com.balsam.timetable.ui.components.NumberField
import com.balsam.timetable.ui.components.PatternChips
import com.balsam.timetable.ui.components.SlotDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(courseId: Long, onBack: () -> Unit) {    val vm: CourseEditViewModel = viewModel(key = "course_edit_$courseId")
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

@Composable
private fun CourseForm(
    state: CourseEditViewModel.UiState,
    vm: CourseEditViewModel,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onRequestDelete: () -> Unit,
) {
    val nameFocus = remember { androidx.compose.ui.focus.FocusRequester() }
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
                s.copy(lessons = s.lessons + CourseEditViewModel.FormLesson(endWeek = s.semester?.totalWeeks ?: 16))
            }
        }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(" 添加时间段")
        }
        Spacer(Modifier.height(16.dp))
        Text("课前提醒", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                ReminderMode.GLOBAL -> "跟随系统设置中的全局提醒方式"
                ReminderMode.NOTIFICATION -> "提前发送普通通知"
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

@Composable
private fun ColorPalette(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CourseColors.PALETTE.forEachIndexed { i, c ->
            val color = Color(c.toInt())
            val isSelected = i == selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(i) },
            )
        }
    }
}

@Composable
private fun LessonCard(
    index: Int,
    lesson: CourseEditViewModel.FormLesson,
    slotCount: Int,
    totalWeeks: Int,
    onChange: (CourseEditViewModel.FormLesson) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
) {
    Card(colors = CardDefaults.cardColors()) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("时间段 ${index + 1}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除此时间段",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayDropdown(lesson.dayOfWeek) { onChange(lesson.copy(dayOfWeek = it)) }
                SlotDropdown(lesson.startSlot, slotCount) { v ->
                    onChange(lesson.copy(startSlot = v, endSlot = maxOf(v, lesson.endSlot)))
                }
                Text("—")
                SlotDropdown(lesson.endSlot, slotCount) { v ->
                    onChange(lesson.copy(endSlot = v, startSlot = minOf(v, lesson.startSlot)))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("第", style = MaterialTheme.typography.bodyMedium)
                NumberField("起始周", lesson.startWeek, { v ->
                    onChange(lesson.copy(startWeek = v))
                }, range = 1..totalWeeks)
                Text("~", style = MaterialTheme.typography.bodyMedium)
                NumberField("结束周", lesson.endWeek, { v ->
                    onChange(lesson.copy(endWeek = v))
                }, range = 1..totalWeeks)
                Text("周", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            PatternChips(lesson.pattern) { onChange(lesson.copy(pattern = it)) }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = lesson.location,
                onValueChange = { v -> onChange(lesson.copy(location = v)) },
                label = { Text("地点") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipStatic(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

/**
 * 课程查看 / 编辑的底部小窗形态：不遮满全屏。
 * prefill 非空时为「从课表空白格选时间后快速添加」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditSheet(
    courseId: Long,
    prefill: CourseEditViewModel.LessonPrefill?,
    onDismiss: () -> Unit,
) {
    val vm: CourseEditViewModel = viewModel(
        key = "sheet_${courseId}_${prefill?.hashCode() ?: 0}",
    )
    val state by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(courseId, prefill) {
        vm.resetTransient()
        vm.load(courseId, prefill)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        when {
            state.loading ->
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.semester == null ->
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Text("还没有当前学期，请先在「学期管理」中创建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            else -> {
                var showDeleteConfirm by remember { mutableStateOf(false) }
                // 已有课程先展示只读详情，点「编辑」再进入表单；新建课程直接进表单
                var showDetail by remember(state.isNew) { mutableStateOf(!state.isNew && courseId > 0) }
                if (showDetail) {
                    CourseDetailContent(
                        state = state,
                        onEdit = { showDetail = false },
                        onDelete = { showDeleteConfirm = true },
                    )
                } else {
                    CourseForm(
                        state = state,
                        vm = vm,
                        padding = PaddingValues(0.dp),
                        onRequestDelete = { showDeleteConfirm = true },
                    )
                }
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("删除课程") },
                        text = { Text("确定删除「${state.courseName}」及其全部时间段吗？此操作不可撤销。") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteConfirm = false
                                vm.delete(onDismiss)
                            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
                    )
                }
            }
        }
        LaunchedEffect(state.saved) { if (state.saved) onDismiss() }
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}

/** 课程只读详情：名称 / 上课时间 / 教室 / 教师 / 提醒 / 备注，点「编辑」进入表单 */
@Composable
private fun CourseDetailContent(
    state: CourseEditViewModel.UiState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(CourseColors.PALETTE[state.colorIdx % CourseColors.PALETTE.size].toInt())),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                state.courseName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(14.dp))
        DetailSection("上课时间") {
            state.lessons.forEach { l ->
                Text(
                    buildString {
                        append("${DAY_LABELS.getOrElse(l.dayOfWeek - 1) { "" }} 第${l.startSlot}-${l.endSlot}节")
                        append(" · ${l.startWeek}-${l.endWeek}周")
                        if (l.pattern != WeekPattern.EVERY) append(" ${l.pattern.label}")
                        if (l.location.isNotBlank()) append(" · ${l.location}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
        DetailSection("教师") {
            Text(
                state.teacher.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        DetailSection("课前提醒") {
            Text(
                state.reminderMode.label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.note.isNotBlank()) {
            DetailSection("备注") {
                Text(state.note, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row {
            Button(
                onClick = onEdit,
                modifier = Modifier.weight(1f).height(44.dp),
            ) { Text("编辑") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).height(44.dp),
            ) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(2.dp))
    androidx.compose.foundation.layout.Column(content = content)
    Spacer(Modifier.height(10.dp))
}
