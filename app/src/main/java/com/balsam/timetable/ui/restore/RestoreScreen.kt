package com.balsam.timetable.ui.restore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.balsam.timetable.data.backup.AutoBackup
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 从备份恢复：自动扫描备份目录 → 选备份 → 预览与模式选择 → 确认 → 事务恢复。
 * 两种模式：合并恢复（并入目标学期）/ 完整恢复（替换目标学期，自动先做快照）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(
    onBack: () -> Unit,
    vm: RestoreViewModel = viewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.pickFile(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("从备份恢复") },
                navigationIcon = {
                    IconButton(onClick = { if (state.selected != null) vm.backToList() else onBack() }) {
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
            state.error?.let {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
                }
            }
            state.info?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            when {
                state.result != null -> ResultCard(state, onBackToList = { vm.backToList() })
                state.selected != null -> RestorePreview(
                    state = state,
                    onMode = vm::setMode,
                    onTarget = vm::setTarget,
                    onConfirm = vm::confirmRestore,
                )
                else -> BackupList(
                    state = state,
                    onSelect = vm::select,
                    onPickFile = {
                        pickFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                    },
                    onRetry = vm::refresh,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BackupList(
    state: RestoreViewModel.UiState,
    onSelect: (AutoBackup.BackupMeta) -> Unit,
    onPickFile: () -> Unit,
    onRetry: () -> Unit,
) {
    if (state.loading) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.height(24.dp))
        }
        return
    }
    Text(
        "自动扫描「文档 / 苦瓜课表」备份目录（每日自动备份保存在这里）。也可以选择任意位置的 JSON 备份文件，例如微信 / 网盘传来的换机备份。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    TextButton(onClick = onPickFile) { Text("从文件选择备份…") }

    if (state.backups.isEmpty()) {
        Text(
            "还没有备份文件。可以先在设置里「立即备份」，或选择上面任意导入方式建立课表。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        TextButton(onClick = onRetry) { Text("重新扫描") }
        return
    }
    state.backups.forEach { meta ->
        Card(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .then(if (meta.doc != null) Modifier.clickable { onSelect(meta) } else Modifier),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    formatBackupTime(meta.timestampMillis),
                    fontWeight = FontWeight.SemiBold,
                )
                val err = meta.parseError
                if (err != null) {
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    val doc = meta.doc
                    Text(
                        buildString {
                            append(doc?.semester?.name.orEmpty())
                            doc?.semester?.startDate?.let { append(" · 开学 $it") }
                            doc?.let {
                                append(" · ${it.entries.size} 条记录")
                                if (it.slots.isNotEmpty()) append(" · ${it.slots.size} 节")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${meta.fileName}（${formatSize(meta.sizeBytes)}）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun RestorePreview(
    state: RestoreViewModel.UiState,
    onMode: (RestoreViewModel.Mode) -> Unit,
    onTarget: (Long) -> Unit,
    onConfirm: () -> Unit,
) {
    val meta = state.selected ?: return
    val doc = meta.doc ?: return
    var showConfirm by remember { mutableStateOf(false) }
    val target = state.semesters.firstOrNull { it.id == state.targetId }

    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("备份内容", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text("学期：${doc.semester.name}")
            Text("开学日期：${doc.semester.startDate}（第 1 周周一）")
            Text("总周数：${doc.semester.totalWeeks}")
            Text("课程记录：${doc.entries.size} 条")
            Text("节次时间：${if (doc.slots.isEmpty()) "备份中无节次数据" else "${doc.slots.size} 节"}")
            Text(
                meta.fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("恢复方式", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            ModeRow(
                selected = state.mode == RestoreViewModel.Mode.MERGE,
                title = "合并恢复",
                desc = "把备份课程合并到目标学期，与现有课程完全重复的条目自动跳过；学期信息与节次不变。",
                onClick = { onMode(RestoreViewModel.Mode.MERGE) },
            )
            Spacer(Modifier.height(8.dp))
            ModeRow(
                selected = state.mode == RestoreViewModel.Mode.FULL,
                title = "完整恢复",
                desc = "用备份替换目标学期：课程全部清空后按备份重建，学期名称、开学日期、总周数与节次时间也以备份为准。恢复前会自动把该学期当前数据存一份快照到备份目录。",
                onClick = { onMode(RestoreViewModel.Mode.FULL) },
            )
            Spacer(Modifier.height(10.dp))
            // 目标学期选择；没有任何学期时（新装直接恢复）自动按备份创建
            if (state.semesters.isEmpty()) {
                Text(
                    "还没有学期：恢复时会自动按备份里的学期信息创建。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("目标学期：", style = MaterialTheme.typography.bodyMedium)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }) {
                            Text(
                                (target?.name ?: "请选择") + if (target?.isCurrent == true) "（当前）" else "",
                                maxLines = 1, // 长学期名在大字体下截断优于压缩变形
                            )
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            state.semesters.forEach { sem ->
                                DropdownMenuItem(
                                    text = { Text(sem.name + if (sem.isCurrent) "（当前）" else "") },
                                    onClick = {
                                        onTarget(sem.id)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (state.mode == RestoreViewModel.Mode.FULL && target != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ 完整恢复将清空「${target.name}」现有课程并按备份重建，学期名称、开学日期、总周数与节次时间也会被替换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    val noSemester = state.semesters.isEmpty()
    Button(
        onClick = { if (state.mode == RestoreViewModel.Mode.FULL && !noSemester) showConfirm = true else onConfirm() },
        enabled = !state.busy && (state.targetId != null || noSemester),
        // heightIn 而非固定 height：大字体下按钮文案折两行不被裁切
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(
            when {
                noSemester -> "恢复并创建学期"
                state.mode == RestoreViewModel.Mode.FULL -> "完整恢复到「${target?.name ?: "…"}」"
                else -> "合并恢复到「${target?.name ?: "…"}」"
            },
        )
    }
    if (state.busy) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.height(20.dp))
            Spacer(Modifier.padding(start = 12.dp))
            Text("正在恢复…")
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认完整恢复") },
            text = {
                Text(
                    "「${target?.name.orEmpty()}」现有的全部课程将被清空，替换为备份「${doc.semester.name}」的数据。\n\n" +
                        "执行前会先自动创建当前数据的快照（保存在「文档 / 苦瓜课表」），万一恢复有误，仍可从快照找回。",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showConfirm = false
                    onConfirm()
                }) { Text("清空并恢复") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ModeRow(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        // weight(1f)：说明文案很长，不按剩余宽度约束测量的话大字体下会单行溢出卡片
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultCard(state: RestoreViewModel.UiState, onBackToList: () -> Unit) {
    val r = state.result ?: return
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("✅ 恢复完成，已写入 ${r.importedCourses} 门课程", fontWeight = FontWeight.SemiBold)
            if (r.skippedDuplicates > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "↺ 跳过 ${r.skippedDuplicates} 条与现有课程完全重复的条目",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (r.invalid.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "⚠ ${r.invalid.size} 条无效已忽略（" +
                        r.invalid.groupingBy { it.reason }.eachCount()
                            .entries.joinToString("、") { (reason, n) -> "$reason $n 条" } + "）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "提醒与桌面小组件已按恢复后的数据重新安排。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBackToList) { Text("返回备份列表") }
        }
    }
}

private fun formatBackupTime(millis: Long): String = if (millis <= 0) {
    "未知时间"
} else {
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
