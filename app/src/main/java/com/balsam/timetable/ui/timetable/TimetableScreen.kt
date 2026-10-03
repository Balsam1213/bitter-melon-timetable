package com.balsam.timetable.ui.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Lesson
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.db.TimeSlot
import com.balsam.timetable.data.model.DAY_LABELS
import com.balsam.timetable.data.model.WeekLogic
import com.balsam.timetable.data.model.WeekPattern
import com.balsam.timetable.ui.course.CourseEditSheet
import com.balsam.timetable.ui.course.CourseEditViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    onOpenImport: () -> Unit,
    onOpenSlots: () -> Unit,
    onOpenSemesters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCourseList: () -> Unit,
) {
    val vm: TimetableViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is TimetableViewModel.UiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        is TimetableViewModel.UiState.Empty -> EmptyHome(onOpenImport, onOpenSemesters)

        is TimetableViewModel.UiState.Ready -> ReadyHome(
            state = s,
            vm = vm,
            onPrevWeek = { vm.selectWeek((s.week - 1).coerceAtLeast(1)) },
            onNextWeek = { vm.selectWeek((s.week + 1).coerceAtMost(s.semester.totalWeeks)) },
            onSelectWeek = { vm.selectWeek(it) },
            onBackToToday = { vm.selectWeek(null) },
            onOpenImport = onOpenImport,
            onOpenSlots = onOpenSlots,
            onOpenSemesters = onOpenSemesters,
            onOpenSettings = onOpenSettings,
            onOpenCourseList = onOpenCourseList,
        )
    }
}

@Composable
private fun EmptyHome(onOpenImport: () -> Unit, onOpenSemesters: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("欢迎来到苦瓜课表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "完全离线、无需登录。先创建一个学期开始使用，或直接导入课表文件。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenSemesters) { Text("创建学期") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenImport) { Text("导入课表") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyHome(
    state: TimetableViewModel.UiState.Ready,
    vm: TimetableViewModel,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectWeek: (Int) -> Unit,
    onBackToToday: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSlots: () -> Unit,
    onOpenSemesters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCourseList: () -> Unit,
) {
    val semester = state.semester
    val semesters by vm.semesters.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var weekMenuOpen by remember { mutableStateOf(false) }
    var semesterDialog by remember { mutableStateOf(false) }
    var sheetCourseId by remember { mutableStateOf<Long?>(null) }
    var sheetPrefill by remember { mutableStateOf<CourseEditViewModel.LessonPrefill?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(semester.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        when {
                            state.todayWeek == null -> Text(
                                "假期 / 未开学",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.todayWeek == state.week -> Text(
                                "第${state.week}周 · 本周",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // 浏览非本周时，顶栏直接提供醒目的「回到本周」入口
                            else -> Text(
                                "↩ 回到本周",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = onBackToToday),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { semesterDialog = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "切换学期")
                    }
                },
                actions = {
                    IconButton(onClick = onPrevWeek) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一周")
                    }
                    Box {
                        Text(
                            "第${state.week}周",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .clickable { weekMenuOpen = true }
                                .padding(vertical = 12.dp),
                        )
                        DropdownMenu(
                            expanded = weekMenuOpen,
                            onDismissRequest = { weekMenuOpen = false },
                        ) {
                            (1..semester.totalWeeks).forEach { w ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (w == state.todayWeek) "第${w}周（本周）" else "第${w}周")
                                    },
                                    onClick = {
                                        weekMenuOpen = false
                                        onSelectWeek(w)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onNextWeek) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一周")
                    }
                    Box {
                    IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("导入课表") }, onClick = { menuOpen = false; onOpenImport() })
                            DropdownMenuItem(
                                text = { Text("添加课程") },
                                onClick = {
                                    menuOpen = false
                                    sheetPrefill = null
                                    sheetCourseId = -1L
                                },
                            )
                            DropdownMenuItem(text = { Text("全部课程") }, onClick = { menuOpen = false; onOpenCourseList() })
                            DropdownMenuItem(text = { Text("学期管理") }, onClick = { menuOpen = false; onOpenSemesters() })
                            DropdownMenuItem(text = { Text("节次时间") }, onClick = { menuOpen = false; onOpenSlots() })
                            DropdownMenuItem(text = { Text("设置") }, onClick = { menuOpen = false; onOpenSettings() })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    sheetPrefill = null
                    sheetCourseId = -1L
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加课程") },
            )
        },
    ) { padding ->
        if (state.slots.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有节次时间")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenSlots) { Text("去设置节次时间") }
            }
        } else {
            TimetableGrid(
                state = state,
                modifier = Modifier.padding(padding),
                onPrevWeek = onPrevWeek,
                onNextWeek = onNextWeek,
                onOpenCourse = { id ->
                    sheetPrefill = null
                    sheetCourseId = id
                },
                onAddAt = { prefill ->
                    sheetPrefill = prefill
                    sheetCourseId = -1L
                },
            )
        }
    }

    if (semesterDialog) {
        SemesterSwitchDialog(
            semesters = semesters,
            currentId = semester.id,
            onSelect = {
                vm.setCurrentSemester(it)
                semesterDialog = false
            },
            onBackToToday = {
                onBackToToday()
                semesterDialog = false
            },
            onManage = {
                semesterDialog = false
                onOpenSemesters()
            },
            onDismiss = { semesterDialog = false },
        )
    }

    sheetCourseId?.let { cid ->
        CourseEditSheet(
            courseId = cid,
            prefill = sheetPrefill,
            onDismiss = {
                sheetCourseId = null
                sheetPrefill = null
            },
        )
    }
}

/** 左上角图标：学期切换 + 回到本周 */
@Composable
private fun SemesterSwitchDialog(
    semesters: List<Semester>,
    currentId: Long,
    onSelect: (Long) -> Unit,
    onBackToToday: () -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换学期") },
        text = {
            Column {
                if (semesters.isEmpty()) {
                    Text("还没有学期", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                semesters.forEach { sem ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(sem.id) }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = sem.id == currentId, onClick = { onSelect(sem.id) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                sem.name,
                                fontWeight = if (sem.id == currentId) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                "${sem.startDate} · 共${sem.totalWeeks}周",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (sem.isArchived) {
                            Text(
                                "已归档",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onBackToToday) { Text("回到本周") }
            }
        },
        confirmButton = { TextButton(onClick = onManage) { Text("管理学期") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 空白格选区：节次号（课程行顺序号） */
private data class Sel(val day: Int, val startNum: Int, val endNum: Int)

/** 一门课在课表里的渲染单元：课程 + 时间段 + 是否本周 */
private data class LessonRef(val cwl: CourseWithLessons, val lesson: Lesson, val active: Boolean)

/** 网格行：普通节次行，或由上下午课间空档推导出的午休/晚休带 */
private sealed interface GridRow {
    data class Slot(val slot: TimeSlot) : GridRow
    data class Break(val label: String, val range: String) : GridRow
}

/** 按开始时间划分上午(<12点)/下午(12-18点)/晚上(≥18点)，组间空档生成午休/晚休带 */
private fun buildGridRows(slots: List<TimeSlot>): List<GridRow> {
    val sorted = slots.sortedBy { it.orderIndex }
    fun startHour(s: TimeSlot) = s.startTime.substringBefore(':').toIntOrNull() ?: 8
    val morning = sorted.filter { startHour(it) < 12 }
    val afternoon = sorted.filter { val h = startHour(it); h in 12..17 }
    val evening = sorted.filter { startHour(it) >= 18 }

    val rows = mutableListOf<GridRow>()
    morning.forEach { rows.add(GridRow.Slot(it)) }
    if (morning.isNotEmpty() && afternoon.isNotEmpty()) {
        rows.add(GridRow.Break("午休", "${morning.last().endTime} - ${afternoon.first().startTime}"))
    }
    afternoon.forEach { rows.add(GridRow.Slot(it)) }
    if (afternoon.isNotEmpty() && evening.isNotEmpty()) {
        rows.add(GridRow.Break("晚休", "${afternoon.last().endTime} - ${evening.first().startTime}"))
    }
    evening.forEach { rows.add(GridRow.Slot(it)) }
    return rows
}

@Composable
private fun TimetableGrid(
    state: TimetableViewModel.UiState.Ready,
    modifier: Modifier = Modifier,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onOpenCourse: (Long) -> Unit,
    onAddAt: (CourseEditViewModel.LessonPrefill) -> Unit,
) {
    var dragAccum by remember { mutableStateOf(0f) }
    // pointerInput(Unit) 不会随重组重启，必须用 rememberUpdatedState 持有最新回调
    val currentPrev by rememberUpdatedState(onPrevWeek)
    val currentNext by rememberUpdatedState(onNextWeek)
    Column(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragAccum += amount
                    },
                    onDragEnd = {
                        when {
                            dragAccum < -110f -> currentNext()
                            dragAccum > 110f -> currentPrev()
                        }
                        dragAccum = 0f
                    },
                )
            },
    ) {
        AnimatedContent(
            targetState = state.week,
            transitionSpec = {
                val forward = targetState > initialState
                val spec = tween<androidx.compose.ui.unit.IntOffset>(260)
                (
                    if (forward) slideInHorizontally(spec) { it } else slideInHorizontally(spec) { -it }
                    ).togetherWith(
                    if (forward) slideOutHorizontally(spec) { -it } else slideOutHorizontally(spec) { it }
                )
            },
            label = "weekSwitch",
        ) { week ->
            WeekContent(state, week, onOpenCourse, onAddAt)
        }
    }
}

@Composable
private fun WeekContent(
    state: TimetableViewModel.UiState.Ready,
    week: Int,
    onOpenCourse: (Long) -> Unit,
    onAddAt: (CourseEditViewModel.LessonPrefill) -> Unit,
) {
    val slotHeight = 56.dp
    val bandHeight = 34.dp
    val timeColWidth = 44.dp
    val days = if (state.showWeekend) 7 else 5
    val weekStart: LocalDate = WeekLogic.weekMonday(state.semester.startDate, week)
    val todayDow = state.today.dayOfWeek.value
    val totalWeeks = state.semester.totalWeeks

    val rows = remember(state.slots) { buildGridRows(state.slots) }
    val layout = remember(rows) {
        val slotY = mutableMapOf<Int, Dp>()
        val bands = mutableListOf<Triple<Dp, Dp, GridRow.Break>>()
        var y = 0.dp
        rows.forEach { r ->
            when (r) {
                is GridRow.Slot -> {
                    slotY[r.slot.orderIndex] = y
                    y += slotHeight
                }
                is GridRow.Break -> {
                    bands.add(Triple(y, y + bandHeight, r))
                    y += bandHeight
                }
            }
        }
        Triple(slotY, bands, y)
    }
    val slotY = layout.first
    val bands = layout.second
    val totalHeight = layout.third
    val lastSlotNum = (rows.lastOrNull { it is GridRow.Slot } as? GridRow.Slot)?.slot?.orderIndex ?: 1

    var sel by remember { mutableStateOf<Sel?>(null) }
    var dragAnchor by remember { mutableStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var overlapShow by remember { mutableStateOf<List<LessonRef>?>(null) }

    fun slotNumberAt(y: Dp): Int? {
        rows.forEach { r ->
            if (r is GridRow.Slot) {
                val top = slotY[r.slot.orderIndex] ?: return@forEach
                if (y >= top && y < top + slotHeight) return r.slot.orderIndex
            }
        }
        return null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dayWidth = (maxWidth - timeColWidth) / days
        Column(Modifier.fillMaxSize()) {
            // 星期表头
            Row(Modifier.height(48.dp)) {
                Spacer(Modifier.width(timeColWidth))
                repeat(days) { i ->
                    val dow = i + 1
                    val date = weekStart.plusDays((dow - 1).toLong())
                    val isToday = state.todayWeek == week && dow == todayDow
                    Column(
                        Modifier.width(dayWidth).fillMaxHeight().padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            DAY_LABELS[i],
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "${date.monthValue}/${date.dayOfMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
            // 主体
            Row(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(Modifier.width(timeColWidth)) {
                    rows.forEach { r ->
                        when (r) {
                            is GridRow.Slot -> Column(
                                Modifier
                                    .height(slotHeight)
                                    .fillMaxWidth()
                                    .padding(horizontal = 1.dp, vertical = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "${r.slot.orderIndex}",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    r.slot.startTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Text(
                                    r.slot.endTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            is GridRow.Break -> Column(
                                Modifier
                                    .height(bandHeight)
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    r.label,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    r.range,
                                    fontSize = 8.sp,
                                    lineHeight = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                repeat(days) { i ->
                    val dow = i + 1
                    val isToday = state.todayWeek == week && dow == todayDow
                    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    val bandColor = MaterialTheme.colorScheme.surfaceVariant
                    val selColor = MaterialTheme.colorScheme.primary

                    Box(
                        Modifier
                            .width(dayWidth)
                            .height(totalHeight)
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                else Color.Transparent
                            )
                            .pointerInput(state.slots, dow) {
                                // 单击空白格：选中一节；再点已选区或午休/晚休带：取消。
                                // 这里只挂点按手势，垂直拖动交给外层滚动，保证任意区域都能滑
                                detectTapGestures { off ->
                                    val yDp = off.y.toDp()
                                    val n = slotNumberAt(yDp)
                                    val c = sel
                                    when {
                                        n == null -> sel = null
                                        c != null && c.day == dow && n in c.startNum..c.endNum -> sel = null
                                        else -> sel = Sel(dow, n, n)
                                    }
                                }
                            },
                    ) {
                        // 空格子占位：与课程卡同形状的圆角块
                        state.slots.forEach { slot ->
                            val top = slotY[slot.orderIndex] ?: return@forEach
                            Box(
                                Modifier
                                    .offset(x = 1.dp, y = top + 2.dp)
                                    .width(dayWidth - 2.dp)
                                    .height(slotHeight - 4.dp)
                                    .background(placeholderColor, RoundedCornerShape(8.dp)),
                            )
                        }
                        // 午休 / 晚休：每列画一段、首尾列带圆角，视觉上连成横穿整周的长条；
                        // 标签只出现在左侧时间列，不在每列重复
                        val bandShape = when {
                            days == 1 -> RoundedCornerShape(7.dp)
                            i == 0 -> RoundedCornerShape(topStart = 7.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 7.dp)
                            i == days - 1 -> RoundedCornerShape(topStart = 0.dp, topEnd = 7.dp, bottomEnd = 7.dp, bottomStart = 0.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                        bands.forEach { (top, _, _) ->
                            Box(
                                Modifier
                                    .offset(y = top + 2.dp)
                                    .width(dayWidth)
                                    .height(bandHeight - 4.dp)
                                    .background(bandColor, bandShape),
                            )
                        }
                        // 选区高亮：拖选扩选手势只挂在这个 Box 上——
                        // 无选区时整个课表自由滚动；有选区时从选区内拖动才扩选
                        val cur = sel
                        if (cur != null && cur.day == dow) {
                            val top = slotY[cur.startNum] ?: 0.dp
                            val bottom = (slotY[cur.endNum] ?: 0.dp) + slotHeight
                            Box(
                                Modifier
                                    .offset(x = 1.dp, y = top + 1.dp)
                                    .width(dayWidth - 2.dp)
                                    .height(bottom - top - 2.dp)
                                    .background(selColor.copy(alpha = 0.15f))
                                    .border(1.5.dp, selColor, RoundedCornerShape(8.dp))
                                    .pointerInput(state.slots, dow) {
                                        detectVerticalDragGestures(
                                            onDragStart = { off ->
                                                val c = sel ?: return@detectVerticalDragGestures
                                                dragging = true
                                                val yAbs = (slotY[c.startNum] ?: 0.dp) + off.y.toDp() + 1.dp
                                                dragAnchor = (slotNumberAt(yAbs) ?: c.startNum)
                                                    .coerceIn(c.startNum, c.endNum)
                                            },
                                            onVerticalDrag = { change, _ ->
                                                change.consume()
                                                val c = sel ?: return@detectVerticalDragGestures
                                                val yAbs = (slotY[c.startNum] ?: 0.dp) +
                                                    change.position.y.toDp() + 1.dp
                                                val n = slotNumberAt(yAbs)
                                                if (n != null) {
                                                    sel = Sel(dow, minOf(dragAnchor, n), maxOf(dragAnchor, n))
                                                }
                                            },
                                            onDragEnd = { dragging = false },
                                            onDragCancel = { dragging = false },
                                        )
                                    },
                            )
                        }
                        // 课程块：按时间段重叠分组。组内优先显示本周课程；
                        // 非本周灰显 + 底部「非本周」；重叠多门时底部「另有N门」，点击弹列表选择
                        val dayEntries = mutableListOf<LessonRef>()
                        state.courses.forEach { cwl ->
                            cwl.lessons.forEach { l ->
                                if (l.dayOfWeek == dow) {
                                    dayEntries.add(
                                        LessonRef(
                                            cwl, l,
                                            WeekLogic.lessonActive(week, l.startWeek, l.endWeek, l.pattern),
                                        )
                                    )
                                }
                            }
                        }
                        val groups = mutableListOf<MutableList<LessonRef>>()
                        dayEntries.sortedBy { it.lesson.startSlot }.forEach { e ->
                            val g = groups.firstOrNull { grp ->
                                grp.any {
                                    it.lesson.startSlot <= e.lesson.endSlot &&
                                        e.lesson.startSlot <= it.lesson.endSlot
                                }
                            }
                            if (g != null) g.add(e) else groups.add(mutableListOf(e))
                        }
                        groups.forEach { group ->
                            // 代表课优先显示最近添加的（id 最大）的本周课程，新保存的课程立即可见
                            val rep = group.filter { it.active }.maxByOrNull { it.cwl.course.id } ?: group.first()
                            val startTop = slotY[rep.lesson.startSlot] ?: return@forEach
                            val endBottom = (slotY[rep.lesson.endSlot] ?: startTop) + slotHeight
                            val hint = buildList {
                                if (!rep.active) add("非本周")
                                if (group.size > 1) add("另有${group.size - 1}门")
                            }.joinToString(" · ")
                            LessonCard(
                                cwl = rep.cwl,
                                lesson = rep.lesson,
                                totalWeeks = totalWeeks,
                                grey = !rep.active,
                                hint = hint.ifEmpty { null },
                                modifier = Modifier
                                    .padding(horizontal = 1.dp)
                                    .offset(y = startTop + 1.dp)
                                    .width(dayWidth - 2.dp)
                                    .height(endBottom - startTop - 3.dp),
                                onClick = {
                                    if (group.size > 1) {
                                        overlapShow = group.toList()
                                    } else {
                                        onOpenCourse(rep.cwl.course.id)
                                    }
                                },
                            )
                        }
                        // 选区上的「添加课程」按钮
                        if (cur != null && cur.day == dow) {
                            val startTop = slotY[cur.startNum] ?: 0.dp
                            val endBottom = (slotY[cur.endNum] ?: 0.dp) + slotHeight
                            val buttonY =
                                if (cur.endNum < lastSlotNum) endBottom - 15.dp else startTop + 3.dp
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shadowElevation = 3.dp,
                                modifier = Modifier
                                    .offset(x = 2.dp, y = buttonY)
                                    .clickable {
                                        onAddAt(CourseEditViewModel.LessonPrefill(dow, cur.startNum, cur.endNum))
                                        sel = null
                                    },
                            ) {
                                Text(
                                    "＋ 添加课程",
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 时间重叠的多门课程选择列表
    overlapShow?.let { group ->
        AlertDialog(
            onDismissRequest = { overlapShow = null },
            title = { Text("时间重叠的课程") },
            text = {
                Column {
                    Text(
                        "同一时间段有多门课，点击选择要查看的那一门",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    group.forEach { ref ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    overlapShow = null
                                    onOpenCourse(ref.cwl.course.id)
                                }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .background(
                                        Color(ref.cwl.course.colorArgb.toInt()).copy(
                                            alpha = if (ref.active) 1f else 0.35f
                                        ),
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ref.cwl.course.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${DAY_LABELS.getOrElse(ref.lesson.dayOfWeek - 1) { "" }} " +
                                        "第${ref.lesson.startSlot}-${ref.lesson.endSlot}节 · " +
                                        "${ref.lesson.startWeek}-${ref.lesson.endWeek}周 ${ref.lesson.pattern.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (ref.active) "本周" else "非本周",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ref.active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { overlapShow = null }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun LessonCard(
    cwl: CourseWithLessons,
    lesson: Lesson,
    totalWeeks: Int,
    grey: Boolean = false,
    hint: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = Color(cwl.course.colorArgb.toInt())
    val bg = if (grey) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    } else {
        color.copy(alpha = 0.92f)
    }
    val onColor = if (grey) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else if (color.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
    val spans = lesson.endSlot - lesson.startSlot + 1
    val compact = spans <= 1
    val weekLabel = when {
        lesson.pattern == WeekPattern.EVERY && lesson.startWeek <= 1 && lesson.endWeek >= totalWeeks -> null
        else -> buildString {
            if (lesson.startWeek > 1 || lesson.endWeek < totalWeeks) {
                append("${lesson.startWeek}-${lesson.endWeek}周")
            }
            when (lesson.pattern) {
                WeekPattern.ODD -> append(" 单周")
                WeekPattern.EVEN -> append(" 双周")
                WeekPattern.EVERY -> Unit
            }
        }.trim()
    }
    Column(
        modifier
            .clip(RoundedCornerShape(if (compact) 7.dp else 8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 4.dp else 5.dp, vertical = 3.dp),
    ) {
        Text(
            cwl.course.name,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 10.sp else 12.sp,
            lineHeight = if (compact) 12.sp else 14.sp,
            color = onColor,
            maxLines = when {
                hint != null && compact -> 2
                hint != null -> 3
                compact -> 3
                else -> 4
            },
            overflow = TextOverflow.Ellipsis,
        )
        if (lesson.location.isNotBlank() && (!compact || cwl.course.name.length <= 5)) {
            Text(
                lesson.location,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                color = onColor.copy(alpha = 0.88f),
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (weekLabel != null && (!compact || lesson.pattern != WeekPattern.EVERY)) {
            Text(
                weekLabel,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                color = onColor.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hint != null) {
            Text(
                hint,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = onColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
