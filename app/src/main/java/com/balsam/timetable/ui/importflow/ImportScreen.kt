package com.balsam.timetable.ui.importflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balsam.timetable.data.importer.ParsedEntry
import com.balsam.timetable.data.model.DAY_LABELS
import com.balsam.timetable.data.model.WeekPattern
import com.balsam.timetable.ui.components.DayDropdown
import com.balsam.timetable.ui.components.NumberField
import com.balsam.timetable.ui.components.PatternChips
import com.balsam.timetable.ui.components.SlotDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    freshSemester: Boolean = false,
    onBack: () -> Unit,
    onManualAdd: () -> Unit,
    onOpenWebImport: () -> Unit = {},
) {
    val vm: ImportViewModel = viewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(freshSemester) { vm.freshSemester = freshSemester }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.parseFileFromUri(it) } }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { vm.parseImage(it) } }

    LaunchedEffect(Unit) { vm.reset() }

    // 接收教务网导入的解析结果，进入统一的预览确认流程
    LaunchedEffect(Unit) {
        com.balsam.timetable.data.html.WebImportBus.pendingEntries?.let { entries ->
            vm.loadExternal(entries)
            com.balsam.timetable.data.html.WebImportBus.pendingEntries = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入课表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (state.importedCount != null) {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("✅ 已导入 ${state.importedCount} 门课程", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "如果学期开学日期不正确，请到「学期管理」中修改，否则周次和提醒会错位。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onBack) { Text("完成") }
                    }
                }
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            Text(
                "选择导入方式。所有解析结果都会先进入预览，确认无误后才写入课表。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SourceCard(
                icon = { Icon(Icons.Default.List, null) },
                title = "Excel / CSV 文件",
                desc = "支持 .xlsx 和 .csv，推荐先看导入模板",
                onClick = {
                    pickFile.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "text/csv", "text/comma-separated-values", "text/plain",
                            "application/json", "application/octet-stream",
                        )
                    )
                },
            )
            SourceCard(
                icon = { Icon(Icons.Default.DateRange, null) },
                title = "JSON 备份文件",
                desc = "本应用导出的备份，或按示例 JSON 格式准备",
                onClick = { pickFile.launch(arrayOf("application/json", "application/octet-stream")) },
            )
            SourceCard(
                icon = { Icon(Icons.Default.Search, null) },
                title = "课表截图识别",
                desc = "离线识别中文课表截图（需包含「周一~周日」表头与节次列）",
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
            SourceCard(
                icon = { Icon(Icons.Default.Add, null) },
                title = "手动添加",
                desc = "逐门添加课程",
                onClick = onManualAdd,
            )
            SourceCard(
                icon = { Icon(Icons.Default.List, null) },
                title = "教务网导入（需联网）",
                desc = "登录教务网后一键解析选课结果页面",
                onClick = onOpenWebImport,
            )

            if (state.busy) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(20.dp))
                    Spacer(Modifier.padding(start = 12.dp))
                    Text("正在解析 / 识别中…")
                }
            }
            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (state.entries.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    buildString {
                        append(state.sourceLabel)
                        append(" · 共 ${state.entries.size} 条记录，点击可修改")
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                var editingIndex by remember { mutableStateOf<Int?>(null) }
                state.entries.forEachIndexed { i, e ->
                    EntryRow(
                        entry = e,
                        onToggle = { vm.toggleInclude(i) },
                        onEdit = { editingIndex = i },
                    )
                }
                Spacer(Modifier.height(10.dp))
                val selected = state.entries.count { it.include }
                Button(
                    onClick = { vm.confirmImport() },
                    enabled = selected > 0 && !state.busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("导入所选 $selected 门课程")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "同名同教师的记录会合并为一门课；导入不会清空已有课程。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                editingIndex?.let { idx ->
                    EntryEditDialog(
                        entry = state.entries[idx],
                        onDismiss = { editingIndex = null },
                        onSave = {
                            vm.updateEntry(idx, it)
                            editingIndex = null
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
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

@Composable
private fun EntryRow(entry: ParsedEntry, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = entry.include, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(entry.name.ifBlank { "（未命名课程）" }, fontWeight = FontWeight.SemiBold)
            Text(
                buildString {
                    append(DAY_LABELS.getOrElse(entry.dayOfWeek - 1) { "周?" })
                    append(" 第${entry.startSlot}-${entry.endSlot}节")
                    append(" ${entry.startWeek}-${entry.endWeek}周")
                    if (entry.weekPattern != WeekPattern.EVERY) append(" ${entry.weekPattern.label}")
                    if (entry.location.isNotBlank()) append(" · ${entry.location}")
                    if (entry.teacher.isNotBlank()) append(" · ${entry.teacher}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryEditDialog(
    entry: ParsedEntry,
    onDismiss: () -> Unit,
    onSave: (ParsedEntry) -> Unit,
) {
    var name by remember { mutableStateOf(entry.name) }
    var teacher by remember { mutableStateOf(entry.teacher) }
    var location by remember { mutableStateOf(entry.location) }
    var day by remember { mutableStateOf(entry.dayOfWeek) }
    var startSlot by remember { mutableStateOf(entry.startSlot) }
    var endSlot by remember { mutableStateOf(entry.endSlot) }
    var startWeek by remember { mutableStateOf(entry.startWeek) }
    var endWeek by remember { mutableStateOf(entry.endWeek) }
    var pattern by remember { mutableStateOf(entry.weekPattern) }
    val slotCount = 16

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记录") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程") }, singleLine = true)
                OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("教师") }, singleLine = true)
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DayDropdown(day) { d -> day = d }
                    Spacer(Modifier.padding(start = 6.dp))
                    SlotDropdown(startSlot, slotCount) { v -> startSlot = v; if (endSlot < v) endSlot = v }
                    Text("~")
                    SlotDropdown(endSlot, slotCount) { v -> endSlot = v; if (startSlot > v) startSlot = v }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("第", style = MaterialTheme.typography.bodyMedium)
                    NumberField("起周", startWeek, { v -> startWeek = v })
                    Text("~", style = MaterialTheme.typography.bodyMedium)
                    NumberField("止周", endWeek, { v -> endWeek = v })
                    Text("周", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                PatternChips(pattern) { pattern = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    entry.copy(
                        name = name.trim(), teacher = teacher.trim(), location = location.trim(),
                        dayOfWeek = day, startSlot = startSlot, endSlot = endSlot,
                        startWeek = startWeek, endWeek = maxOf(startWeek, endWeek),
                        weekPattern = pattern,
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
