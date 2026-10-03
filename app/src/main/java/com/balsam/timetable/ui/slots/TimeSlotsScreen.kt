package com.balsam.timetable.ui.slots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balsam.timetable.data.db.TimeSlot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSlotsScreen(onBack: () -> Unit) {
    val vm: TimeSlotsViewModel = viewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TimeSlot?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节次时间") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { vm.addSlot() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加节次") },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.semester == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有当前学期", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item {
                    Text(
                        "每节课的起止时间。上午、下午、晚上之间的空档会自动作为午休 / 晚休显示在课表中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(state.slots, key = { it.id }) { slot ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("第${slot.orderIndex}节", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(16.dp))
                            TextButton(onClick = { editing = slot }) {
                                Text("${slot.startTime} — ${slot.endTime}")
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { vm.removeSlot(slot) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    editing?.let { slot ->
        TimeEditDialog(
            slot = slot,
            onDismiss = { editing = null },
            onConfirm = { start, end ->
                vm.updateSlot(slot, start, end)
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeEditDialog(
    slot: TimeSlot,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    val startState = rememberTimePickerState(
        initialHour = slot.startTime.substringBefore(':').toIntOrNull() ?: 8,
        initialMinute = slot.startTime.substringAfter(':').toIntOrNull() ?: 0,
        is24Hour = true,
    )
    val endState = rememberTimePickerState(
        initialHour = slot.endTime.substringBefore(':').toIntOrNull() ?: 8,
        initialMinute = slot.endTime.substringAfter(':').toIntOrNull() ?: 45,
        is24Hour = true,
    )
    // 同一时间只渲染一个全尺寸 TimePicker，用芯片切换开始/结束——
    // 两个叠放会被屏幕高度压缩，导致结束时间的钟面变小、高亮与数字错位、按钮被顶出屏幕
    var editingStart by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("第${slot.orderIndex}节 时间", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = editingStart,
                        onClick = { editingStart = true },
                        label = { Text("开始 %02d:%02d".format(startState.hour, startState.minute)) },
                    )
                    FilterChip(
                        selected = !editingStart,
                        onClick = { editingStart = false },
                        label = { Text("结束 %02d:%02d".format(endState.hour, endState.minute)) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (editingStart) {
                    TimePicker(state = startState)
                } else {
                    TimePicker(state = endState)
                }
                Row {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = {
                        onConfirm(
                            "%02d:%02d".format(startState.hour, startState.minute),
                            "%02d:%02d".format(endState.hour, endState.minute),
                        )
                    }) { Text("确定") }
                }
            }
        }
    }
}
