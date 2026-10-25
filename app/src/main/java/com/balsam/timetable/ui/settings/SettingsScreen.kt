package com.balsam.timetable.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenHolidays: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var notifGranted by remember { mutableStateOf(vm.hasNotificationPermission()) }
    var exportError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { notifGranted = vm.hasNotificationPermission() }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            scope.launch { exportError = vm.exportToUri(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            SectionCard("课前提醒") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开启课前提醒", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings?.reminderEnabled ?: true,
                        onCheckedChange = { vm.setReminderEnabled(it) },
                    )
                }
                Text("提前", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = (settings?.leadMinutes ?: 15).toFloat(),
                        onValueChange = { vm.setLeadMinutes(it.toInt()) },
                        valueRange = 0f..120f,
                        steps = 23,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${settings?.leadMinutes ?: 15} 分钟", modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(6.dp))
                if (!notifGranted) {
                    Text(
                        "未授予通知权限，收不到提醒通知。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { notifLauncher.launch(vm.notificationSettingsIntent()) }) {
                        Text("去开启通知权限")
                    }
                }
                LaunchedEffect(Unit) {
                    notifGranted = vm.hasNotificationPermission()
                }
            }

            HolidaySection(vm, settings?.holidaySkipEnabled ?: true, onOpenCalendar = onOpenHolidays)

            SectionCard("显示") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("显示周末", fontWeight = FontWeight.SemiBold)
                        Text(
                            "关闭后周课表只显示周一到周五",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings?.showWeekend ?: true,
                        onCheckedChange = { vm.setShowWeekend(it) },
                    )
                }
            }

            SectionCard("数据") {
                TextButton(onClick = onOpenImport) { Text("导入课表（Excel / CSV / JSON / 图片识别）") }
                TextButton(onClick = { exportLauncher.launch("课程表备份.json") }) {
                    Text("导出当前学期为 JSON")
                }
                if (exportError != null) {
                    Text(exportError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "所有数据只保存在本机，不上传任何服务器。导出的 JSON 可用于换机迁移或再导入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("关于") {
                Text("苦瓜课表 v${com.balsam.timetable.BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                Text(
                    "完全离线 · 无需登录 · 数据本地存储",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HolidaySection(vm: SettingsViewModel, enabled: Boolean, onOpenCalendar: () -> Unit) {
    val cal by vm.holidayCalendar.collectAsStateWithLifecycle()

    SectionCard("节假日") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("节假日不提醒", fontWeight = FontWeight.SemiBold)
                Text(
                    "法定放假日当天不发课前提醒；调休上班日按你设置的周几的课表提醒。列表按国务院安排预置，联网时自动同步（手动改动不会被覆盖）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = { vm.setHolidaySkipEnabled(it) })
        }
        TextButton(onClick = onOpenCalendar) {
            Text("日历管理（放假 ${cal.offDays.size} 天 · 调休 ${cal.makeupDays.size} 天）")
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}
