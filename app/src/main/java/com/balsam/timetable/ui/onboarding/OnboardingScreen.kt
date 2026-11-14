package com.balsam.timetable.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balsam.timetable.data.model.WeekLogic
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 首启向导（仅新装且无学期时出现）：
 * 1 创建学期（预填可改）→ 2 选导入方式（跳转复用 ImportScreen / 教务网 / 恢复页）
 * → 3-4 识别与确认在 ImportScreen 内完成 → 5 就绪页（课表已生成 + 提醒引导）。
 * 任意一步都可「跳过」，跳过/完成都会落 onboarding_done 标记。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenWebImport: () -> Unit,
    onOpenRestore: () -> Unit,
    onOpenCourseEdit: () -> Unit,
) {
    val vm: OnboardingViewModel = viewModel()
    val state by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("欢迎使用苦瓜课表") })
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // 从导入页返回时自动就绪（课程数 > 0 说明已导入）
            LaunchedEffect(state.step) {
                if (state.step == OnboardingViewModel.Step.IMPORT) vm.goToReadyIfImported()
            }

            when (state.step) {
                OnboardingViewModel.Step.SEMESTER -> SemesterStep(vm, state, onFinished)
                OnboardingViewModel.Step.IMPORT -> ImportStep(
                    state = state,
                    onOpenImport = onOpenImport,
                    onOpenWebImport = onOpenWebImport,
                    onOpenRestore = onOpenRestore,
                    onOpenCourseEdit = onOpenCourseEdit,
                    onReady = { vm.goToReady() },
                    onSkip = { vm.finish(onFinished) },
                )
                OnboardingViewModel.Step.READY -> ReadyStep(
                    state = state,
                    onToggleReminder = { vm.setReminderEnabled(it) },
                    onFinish = { vm.finish(onFinished) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------- 第 1 步：创建学期 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemesterStep(vm: OnboardingViewModel, state: OnboardingViewModel.UiState, onFinished: () -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }

    Text(
        "三步开始：创建学期 → 导入课表 → 查看本周课表。",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("① 创建学期", fontWeight = FontWeight.SemiBold)
            Text(
                "已按今天日期预填，请核对修改（开学日期决定「今天是第几周」）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.semesterName,
                onValueChange = { vm.updateName(it) },
                label = { Text("学期名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("第1周周一：", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { showDatePicker = true }) { Text(state.startDate) }
            }
            Text(
                "开学那天或其所在周的周一为第1周起点",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("总周数：", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = state.totalWeeks,
                    onValueChange = { vm.updateTotalWeeks(it) },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                )
            }
        }
    }
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { vm.createSemester() },
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text(if (state.busy) "创建中…" else "创建学期，下一步") }
    TextButton(onClick = { vm.finish(onFinished) }, modifier = Modifier.fillMaxWidth()) {
        Text("跳过，先看看课表")
    }

    if (showDatePicker) {
        val initial = runCatching { WeekLogic.parseDate(state.startDate) }.getOrDefault(LocalDate.now())
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateStartDate(
                        pickerState.selectedDateMillis?.let { ms ->
                            WeekLogic.mondayOf(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()).toString()
                        } ?: state.startDate,
                    )
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// ---------------- 第 2 步：选择导入方式（识别与确认在 ImportScreen 内） ----------------

@Composable
private fun ImportStep(
    state: OnboardingViewModel.UiState,
    onOpenImport: () -> Unit,
    onOpenWebImport: () -> Unit,
    onOpenRestore: () -> Unit,
    onOpenCourseEdit: () -> Unit,
    onReady: () -> Unit,
    onSkip: () -> Unit,
) {
    Text(
        if (state.semesterCreated) "学期「${state.semesterDisplayName}」已创建。选择一种方式把课表装进来，所有结果都会先预览确认再写入。"
        else "还没有学期：直接导入也可以，没有学期时会先弹窗帮你建一个。",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    SourceCard(
        icon = { Icon(Icons.Default.List, null) },
        title = "Excel / CSV 文件",
        desc = "支持 .xlsx 和 .csv，可先用模板核对列格式",
        onClick = onOpenImport,
    )
    SourceCard(
        icon = { Icon(Icons.Default.Search, null) },
        title = "课表截图识别",
        desc = "离线识别中文课表截图（需包含「周一~周日」表头）",
        onClick = onOpenImport,
    )
    SourceCard(
        icon = { Icon(Icons.Default.DateRange, null) },
        title = "教务网导入（需联网）",
        desc = "登录教务网后一键解析选课结果页面",
        onClick = onOpenWebImport,
    )
    SourceCard(
        icon = { Icon(Icons.Default.DateRange, null) },
        title = "JSON 备份恢复",
        desc = "换机场景：恢复之前导出的备份文件",
        onClick = onOpenRestore,
    )
    SourceCard(
        icon = { Icon(Icons.Default.Add, null) },
        title = "手动添加课程",
        desc = "逐门添加，适合课程很少的情况",
        onClick = onOpenCourseEdit,
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = onReady, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text("完成导入，进入课表")
    }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text("跳过，先看看课表")
    }
}

// ---------------- 第 5 步：就绪 + 提醒引导 ----------------

@Composable
private fun ReadyStep(
    state: OnboardingViewModel.UiState,
    onToggleReminder: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    Text(
        "一切就绪！",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            if (state.semesterCreated) {
                Text("学期：${state.semesterDisplayName}")
                Text(
                    if (state.courseCount > 0) "已导入 $state.courseCount 门课程，本周课表已生成"
                    else "本周课表已生成（还没有课程，可稍后导入或手动添加）",
                )
            } else {
                Text("已跳过创建学期，进入课表后可随时在「学期管理」新建。")
            }
        }
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("开启课前提醒", fontWeight = FontWeight.SemiBold)
                    Text(
                        "上课前提前几分钟提醒你（默认 15 分钟，可在设置中调整）。如果系统弹窗询问通知权限，请选择允许。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.reminderEnabled, onCheckedChange = onToggleReminder)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text("完成，进入课表")
    }
}

@Composable
private fun SourceCard(
    icon: @Composable () -> Unit,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.padding(start = 12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
