package com.balsam.timetable.ui.holidays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balsam.timetable.data.model.DayStatus
import com.balsam.timetable.data.model.HolidayCalendar
import com.balsam.timetable.ui.theme.makeupDayBadgeColors
import com.balsam.timetable.ui.theme.offDayBadgeColors
import java.time.LocalDate
import java.time.YearMonth

private val DOW_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private val WEEK_HEADERS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidayCalendarScreen(onBack: () -> Unit) {
    val vm: HolidayViewModel = viewModel()
    val cal by vm.calendar.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(syncState) {
        val msg = when (val s = syncState) {
            SyncState.Idle, SyncState.Running -> null
            is SyncState.Done -> if (s.changed) "已同步官方安排" else "官方安排无变化"
            SyncState.Failed -> "同步失败，请检查网络后重试"
        }
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.consumeSyncState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节假日与调休") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("立即同步官方安排") },
                            onClick = { menuOpen = false; vm.syncHolidays() },
                        )
                        DropdownMenuItem(
                            text = { Text("恢复默认列表") },
                            onClick = { menuOpen = false; showRestoreDialog = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            MonthSwitcher(month = month, onChange = { month = it })

            Row(Modifier.fillMaxWidth()) {
                WEEK_HEADERS.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            val leading = month.atDay(1).dayOfWeek.value - 1
            val cells: List<LocalDate?> =
                List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                            date?.let {
                                DayCell(
                                    date = it,
                                    calendar = cal,
                                    onClick = { selected = it },
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    selected?.let { date ->
        DayEditSheet(
            date = date,
            calendar = cal,
            vm = vm,
            onDismiss = { selected = null },
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("恢复默认列表？") },
            text = { Text("将清空你手动添加/删除/修改过的节假日与调休标记，回到预置官方安排。") },
            confirmButton = {
                TextButton(onClick = { showRestoreDialog = false; vm.restoreDefaults() }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上个月")
        }
        Text(
            "${month.year}年${month.monthValue}月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下个月")
        }
        TextButton(onClick = { onChange(YearMonth.now()) }) { Text("今天") }
    }
}

@Composable
private fun DayCell(date: LocalDate, calendar: HolidayCalendar, onClick: () -> Unit) {
    val today = remember { LocalDate.now() }
    val status = calendar.statusOf(date)
    val badge = when (status) {
        DayStatus.Off -> offDayBadgeColors()
        is DayStatus.Makeup -> makeupDayBadgeColors()
        DayStatus.Normal -> null
    }
    val bg = badge?.bg ?: Color.Transparent
    val fg = badge?.fg ?: MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(
                if (date == today) Modifier.border(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(10.dp),
                ) else Modifier,
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = fg,
        )
        val label = when (status) {
            DayStatus.Off -> "休"
            is DayStatus.Makeup ->
                if (status.dow != null) "班·${DOW_LABELS[status.dow - 1]}" else "班"
            DayStatus.Normal -> ""
        }
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
        } else {
            Spacer(Modifier.height(14.dp)) // 与带标记的格子基线对齐
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DayEditSheet(
    date: LocalDate,
    calendar: HolidayCalendar,
    vm: HolidayViewModel,
    onDismiss: () -> Unit,
) {
    val status = calendar.statusOf(date)
    val key = date.toString()
    val dateTitle = "${date.monthValue}月${date.dayOfMonth}日 · ${WEEK_HEADERS[date.dayOfWeek.value - 1]}"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(dateTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val statusLine = when (status) {
                DayStatus.Off -> "法定放假日：当天不发课前提醒，小组件显示「今天放假」"
                is DayStatus.Makeup ->
                    if (status.dow != null) "调休上班日：按${WEEK_HEADERS[status.dow - 1]}的课表提醒与显示"
                    else "调休上班日：默认按当天（${WEEK_HEADERS[date.dayOfWeek.value - 1]}）的课表提醒与显示"
                DayStatus.Normal -> "普通日期"
            }
            Text(
                statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))

            if (status is DayStatus.Off) {
                Button(onClick = { vm.setDayStatus(key, DayStatus.Normal); onDismiss() }) {
                    Text("取消放假")
                }
            } else {
                OutlinedButton(onClick = { vm.setDayStatus(key, DayStatus.Off); onDismiss() }) {
                    Text("标记为放假日")
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("调休上班", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "周末补班日。选择按周几的课表上课（默认按当天），提醒与小组件都按该周几走：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val isMakeup = status is DayStatus.Makeup
            val currentDow = (status as? DayStatus.Makeup)?.dow
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = isMakeup && currentDow == null,
                    label = { Text("默认") },
                    onClick = {
                        if (isMakeup) vm.setMakeupDow(key, null) else vm.setDayStatus(key, DayStatus.Makeup(null))
                    },
                )
                (1..7).forEach { dow ->
                    FilterChip(
                        selected = isMakeup && currentDow == dow,
                        label = { Text(WEEK_HEADERS[dow - 1]) },
                        onClick = { vm.setDayStatus(key, DayStatus.Makeup(dow)) },
                    )
                }
            }
            if (isMakeup) {
                TextButton(onClick = { vm.setDayStatus(key, DayStatus.Normal); onDismiss() }) {
                    Text("取消调休标记")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "手动改动不会被在线同步覆盖；官方安排联网时自动更新（每年国务院发布后数日内）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
