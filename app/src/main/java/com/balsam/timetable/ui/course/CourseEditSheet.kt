package com.balsam.timetable.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balsam.timetable.data.model.CourseColors
import com.balsam.timetable.data.model.DAY_LABELS
import com.balsam.timetable.data.model.WeeksCodec

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
            val totalWeeks = state.semester?.totalWeeks ?: 0
            state.lessons.forEach { l ->
                Text(
                    buildString {
                        append("${DAY_LABELS.getOrElse(l.dayOfWeek - 1) { "" }} 第${l.startSlot}-${l.endSlot}节")
                        append(" · " + (WeeksCodec.label(l.weekSet, totalWeeks) ?: "全学期"))
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
