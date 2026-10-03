package com.balsam.timetable.ui.semester

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.model.WeekLogic
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterScreen(
    onBack: () -> Unit,
    onOpenImport: () -> Unit = {},
) {
    val vm: SemesterViewModel = viewModel()
    val semesters by vm.semesters.collectAsStateWithLifecycle()
    val loaded by vm.firstLoaded.collectAsStateWithLifecycle()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Semester?>(null) }
    var deleting by remember { mutableStateOf<Semester?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学期管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建学期") },
            )
        },
    ) { padding ->
        when {
            !loaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            semesters.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有学期，点击右下角新建", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                items(semesters, key = { it.id }) { sem ->
                    SemesterCard(
                        onOpen = { vm.setCurrent(sem.id); onBack() },
                        sem = sem,
                        onEdit = { editing = sem },
                        onSetCurrent = { vm.setCurrent(sem.id) },
                        onArchive = { vm.setArchived(sem.id, !sem.isArchived) },
                        onDelete = { deleting = sem },
                    )
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    if (creating) {
        SemesterEditDialog(
            title = "新建学期",
            initialName = semesterNameSuggestion(LocalDate.now()),
            initialStartDate = mondayOfThisWeek(),
            initialTotalWeeks = 20,
            allowImportAfter = true,
            semesters = semesters,
            onDismiss = { creating = false },
            onConfirm = { name, start, weeks, copyFrom, importAfter ->
                vm.create(name, start, weeks, copyFrom)
                creating = false
                if (importAfter) {
                    onBack()
                    onOpenImport()
                }
            },
        )
    }
    editing?.let { sem ->
        SemesterEditDialog(
            title = "编辑学期",
            initialName = sem.name,
            initialStartDate = sem.startDate,
            initialTotalWeeks = sem.totalWeeks,
            semesters = semesters,
            onDismiss = { editing = null },
            onConfirm = { name, start, weeks, _, _ ->
                vm.update(sem, name, start, weeks)
                editing = null
            },
        )
    }
    deleting?.let { sem ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除学期") },
            text = { Text("确定删除「${sem.name}」吗？其中的课程与节次时间会一并删除，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(sem.id); deleting = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SemesterCard(
    sem: Semester,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onSetCurrent: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onOpen() },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (sem.isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = {}, label = { Text("当前") })
                    }
                    if (sem.isArchived) {
                        Spacer(Modifier.width(6.dp))
                        AssistChip(onClick = {}, label = { Text("已归档") })
                    }
                }
                Text(
                    "第1周周一：${sem.startDate} · 共${sem.totalWeeks}周",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    if (!sem.isCurrent) {
                        DropdownMenuItem(
                            text = { Text("设为当前学期") },
                            onClick = { menuOpen = false; onSetCurrent() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (sem.isArchived) "取消归档" else "归档") },
                        onClick = { menuOpen = false; onArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemesterEditDialog(
    title: String,
    initialName: String,
    initialStartDate: String,
    initialTotalWeeks: Int,
    allowImportAfter: Boolean = false,
    semesters: List<Semester>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, startDate: String, totalWeeks: Int, copySlotsFrom: Long?, importAfter: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var startDate by remember { mutableStateOf(initialStartDate) }
    var totalWeeks by remember { mutableStateOf(initialTotalWeeks.toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var copySlots by remember { mutableStateOf(false) }
    var copyFrom by remember { mutableStateOf<Semester?>(null) }
    var copyMenuOpen by remember { mutableStateOf(false) }
    var importAfter by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("学期名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("第1周周一：", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { showDatePicker = true }) { Text(startDate) }
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
                        value = totalWeeks,
                        onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 2) totalWeeks = v },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                    )
                }
                if (title == "新建学期" && semesters.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = copySlots, onCheckedChange = { copySlots = it })
                        Text("复制已有学期的节次时间", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (copySlots) {
                        Box {
                            OutlinedButton(onClick = { copyMenuOpen = true }) {
                                Text(copyFrom?.name ?: semesters.first().name)
                            }
                            DropdownMenu(expanded = copyMenuOpen, onDismissRequest = { copyMenuOpen = false }) {
                                semesters.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.name) },
                                        onClick = { copyFrom = s; copyMenuOpen = false },
                                    )
                                }
                            }
                        }
                    }
                }
                if (title == "新建学期") {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = importAfter, onCheckedChange = { importAfter = it })
                        Text("创建后立即导入课表文件", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (importAfter) {
                        Text(
                            "确定后将打开文件选择器，课程会导入到这个新学期",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && totalWeeks.toIntOrNull()?.let { it in 1..40 } == true,
                onClick = {
                    onConfirm(
                        name, startDate, totalWeeks.toIntOrNull() ?: 20,
                        if (copySlots) copyFrom?.id else null,
                        importAfter,
                    )
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showDatePicker) {
        val initial = runCatching { WeekLogic.parseDate(startDate) }.getOrDefault(LocalDate.now())
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        startDate = WeekLogic.mondayOf(d).toString()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun mondayOfThisWeek(): String = WeekLogic.mondayOf(LocalDate.now()).toString()

private fun semesterNameSuggestion(today: LocalDate): String {
    val year = today.year
    val month = today.monthValue
    val academicYear = if (month >= 8) "$year-${year + 1}" else "${year - 1}-$year"
    val term = if (month in 2..7) "第二学期" else "第一学期"
    return "${academicYear}学年 $term"
}
