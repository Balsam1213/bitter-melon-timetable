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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Lesson
import com.balsam.timetable.data.db.TimeSlot
import com.balsam.timetable.data.model.DAY_LABELS
import com.balsam.timetable.data.model.DayStatus
import com.balsam.timetable.data.model.WeekLogic
import com.balsam.timetable.data.model.WeeksCodec
import com.balsam.timetable.ui.course.CourseEditViewModel
import com.balsam.timetable.ui.theme.makeupDayBadgeColors
import com.balsam.timetable.ui.theme.offDayBadgeColors
import java.time.LocalDate

/** 空白格选区：节次号（课程行顺序号） */
internal data class Sel(val day: Int, val startNum: Int, val endNum: Int)

/** 一门课在课表里的渲染单元：课程 + 时间段 + 是否本周 */
internal data class LessonRef(val cwl: CourseWithLessons, val lesson: Lesson, val active: Boolean)

/** 网格行：普通节次行，或由上下午课间空档推导出的午休/晚休带 */
internal sealed interface GridRow {
    data class Slot(val slot: TimeSlot) : GridRow
    data class Break(val label: String) : GridRow
}

/** 按开始时间划分上午(<12点)/下午(12-18点)/晚上(≥18点)，组间空档生成午休/晚休带 */
internal fun buildGridRows(slots: List<TimeSlot>): List<GridRow> {
    val sorted = slots.sortedBy { it.orderIndex }
    fun startHour(s: TimeSlot) = s.startTime.substringBefore(':').toIntOrNull() ?: 8
    val morning = sorted.filter { startHour(it) < 12 }
    val afternoon = sorted.filter { val h = startHour(it); h in 12..17 }
    val evening = sorted.filter { startHour(it) >= 18 }

    val rows = mutableListOf<GridRow>()
    morning.forEach { rows.add(GridRow.Slot(it)) }
    if (morning.isNotEmpty() && afternoon.isNotEmpty()) {
        rows.add(GridRow.Break("午休"))
    }
    afternoon.forEach { rows.add(GridRow.Slot(it)) }
    if (afternoon.isNotEmpty() && evening.isNotEmpty()) {
        rows.add(GridRow.Break("晚休"))
    }
    evening.forEach { rows.add(GridRow.Slot(it)) }
    return rows
}

/** 周网格：左右滑切周（带滑动动画）+ 星期表头 + 节次列 + 课程块 */
@Composable
internal fun TimetableGrid(
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
    val metrics = GridMetrics.of(state.textScale, state.compactGrid)
    val slotHeight = metrics.slotHeight
    val bandHeight = metrics.bandHeight
    val timeColWidth = metrics.timeColWidth
    val days = if (state.showWeekend) 7 else 5
    val weekStart: LocalDate = WeekLogic.weekMonday(state.semester.startDate, week)
    val todayDow = state.today.dayOfWeek.value
    val totalWeeks = state.semester.totalWeeks

    val rows = remember(state.slots) { buildGridRows(state.slots) }
    // metrics 变化（切字号/紧凑）必须重建行高布局，否则网格不重排
    val layout = remember(rows, metrics) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${date.monthValue}/${date.dayOfMonth}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // 节假日「休」/ 调休「班」标记
                            when (val st = state.holidays.statusOf(date)) {
                                DayStatus.Off -> {
                                    val c = offDayBadgeColors()
                                    Text(
                                        "休",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.fg,
                                        modifier = Modifier
                                            .padding(start = 2.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(c.bg)
                                            .padding(horizontal = 3.dp),
                                    )
                                }
                                is DayStatus.Makeup -> {
                                    val c = makeupDayBadgeColors()
                                    Text(
                                        if (st.dow != null) "班·${DAY_LABELS[st.dow - 1].removePrefix("周")}" else "班",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.fg,
                                        modifier = Modifier
                                            .padding(start = 2.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(c.bg)
                                            .padding(horizontal = 3.dp),
                                    )
                                }
                                DayStatus.Normal -> Unit
                            }
                        }
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
                            // 节次号不独占一行：号在左垂直居中，起止时间在右两行，
                            // 保证紧凑模式（行高 46dp）下时间列也完整可见
                            is GridRow.Slot -> Row(
                                Modifier
                                    .height(slotHeight)
                                    .fillMaxWidth()
                                    .padding(horizontal = 1.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${r.slot.orderIndex}",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.width(2.dp))
                                Column {
                                    Text(
                                        r.slot.startTime,
                                        fontSize = 9.sp,
                                        lineHeight = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    Text(
                                        r.slot.endTime,
                                        fontSize = 9.sp,
                                        lineHeight = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                            is GridRow.Break -> Box(
                                Modifier
                                    .height(bandHeight)
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    r.label,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        val day_entries = mutableListOf<LessonRef>()
                        state.courses.forEach { cwl ->
                            cwl.lessons.forEach { l ->
                                if (l.dayOfWeek == dow) {
                                    day_entries.add(
                                        LessonRef(
                                            cwl, l,
                                            WeekLogic.lessonActive(week, l.weekSet),
                                        ),
                                    )
                                }
                            }
                        }
                        val groups = mutableListOf<MutableList<LessonRef>>()
                        day_entries.sortedBy { it.lesson.startSlot }.forEach { e ->
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
                                metrics = metrics,
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
                                        (WeeksCodec.label(ref.lesson.weekSet, totalWeeks) ?: "全学期"),
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
