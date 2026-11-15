package com.balsam.timetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balsam.timetable.data.model.DAY_LABELS
import com.balsam.timetable.data.model.DayStatus
import com.balsam.timetable.data.model.LessonStatus
import com.balsam.timetable.data.model.TodayLesson
import com.balsam.timetable.data.model.TodayLogic
import com.balsam.timetable.ui.theme.makeupDayBadgeColors
import com.balsam.timetable.ui.theme.offDayBadgeColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 今日视图：状态卡（今天有什么 + 当前/下一节倒计时）+ 全天节次时间轴。
 * 数据全部来自 UiState.Ready（todayLessons 由 VM 的时钟 tick 每 30 秒重算）。
 * 大字体遵循 docs/03 第 7 节：长文案都在 weight(1f) 列内换行。
 */
@Composable
fun TodayHome(
    state: TimetableViewModel.UiState.Ready,
    onOpenCourse: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        StatusCard(state)
        Spacer(Modifier.height(10.dp))
        if (state.todayWeek != null) {
            Timeline(state, onOpenCourse)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusCard(state: TimetableViewModel.UiState.Ready) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.today.format(DateTimeFormatter.ofPattern("M月d日 EEEE").withLocale(Locale.CHINESE)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        weekSubtitle(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HolidayBadge(state)
            }
            Spacer(Modifier.height(8.dp))
            MainStatusLine(state)
        }
    }
}

private fun weekSubtitle(state: TimetableViewModel.UiState.Ready): String = when {
    state.todayWeek == null -> "假期 / 未开学"
    else -> "第${state.todayWeek}周" + if (state.todayWeek == state.week) " · 本周" else ""
}

@Composable
private fun HolidayBadge(state: TimetableViewModel.UiState.Ready) {
    when (val status = state.holidays.statusOf(state.today)) {
        DayStatus.Off -> {
            val c = offDayBadgeColors()
            Badge("休", c.bg, c.fg)
        }
        is DayStatus.Makeup -> {
            val c = makeupDayBadgeColors()
            Badge(if (status.dow != null) "班·${DAY_LABELS[status.dow - 1].removePrefix("周")}" else "班", c.bg, c.fg)
        }
        DayStatus.Normal -> Unit
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(
        Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MainStatusLine(state: TimetableViewModel.UiState.Ready) {
    val now = LocalDateTime.now()
    val off = state.holidays.isOff(state.today)
    val current = if (off || state.todayWeek == null) null else TodayLogic.currentOrNext(state.todayLessons)
    when {
        off -> {
            Text("今天放假", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("法定放假日，好好休息；调休上班日会按映射的课表提醒。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        current == null -> {
            Text(if (state.todayLessons.isEmpty()) "今天没有课" else "今天的课已结束", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("可在「本周」查看整周安排。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> {
            val t = current
            Column(Modifier.fillMaxWidth()) {
                Text(
                    t.cwl.course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                // 说明文案较长，weight(1f) 列内换行（docs/03 第 7 节）
                Column(Modifier.fillMaxWidth()) {
                    val where = if (t.lesson.location.isNotBlank()) " · ${t.lesson.location}" else ""
                    val label = if (t.status == LessonStatus.RUNNING) "上课中" else "下一节"
                    Text(
                        "$label · ${t.timeText}$where",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val anchor = if (t.status == LessonStatus.RUNNING) {
                        t.endAt?.let { now.toLocalDate().atTime(it) }
                    } else {
                        t.startAt?.let { now.toLocalDate().atTime(it) }
                    }
                    anchor?.let {
                        Text(
                            TodayLogic.relativeLabel(now, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Timeline(state: TimetableViewModel.UiState.Ready, onOpenCourse: (Long) -> Unit) {
    val rows = buildGridRows(state.slots)
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            when (row) {
                is GridRow.Break -> Text(
                    row.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                is GridRow.Slot -> TimelineRow(state, row.slot, onOpenCourse)
            }
        }
    }
}

@Composable
private fun TimelineRow(
    state: TimetableViewModel.UiState.Ready,
    slot: com.balsam.timetable.data.db.TimeSlot,
    onOpenCourse: (Long) -> Unit,
) {
    // 该节课 = 起始节次号等于本节的课（跨节次课只在起始行展示，时间文案自带区间）
    val items = state.todayLessons.filter { it.lesson.startSlot == slot.orderIndex }
    val running = items.any { it.status == LessonStatus.RUNNING }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(84.dp)) {
            Text(
                "第${slot.orderIndex}节",
                style = MaterialTheme.typography.labelMedium,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (running) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                "${slot.startTime}-${slot.endTime}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (items.isEmpty()) {
            Surface(
                Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ) {
                Box(contentAlignment = Alignment.CenterStart) {
                    Text(
                        "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        } else {
            Column(Modifier.weight(1f)) {
                items.forEach { item ->
                    LessonRow(item, onOpenCourse = { onOpenCourse(item.cwl.course.id) })
                }
            }
        }
    }
}

@Composable
private fun LessonRow(
    item: TodayLesson,
    onOpenCourse: () -> Unit,
) {
    val container = when (item.status) {
        LessonStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
        LessonStatus.DONE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        LessonStatus.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onOpenCourse),
        shape = RoundedCornerShape(8.dp),
        color = container,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.cwl.course.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.status == LessonStatus.DONE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                val sub = buildString {
                    if (item.lesson.location.isNotBlank()) append(item.lesson.location)
                    if (item.cwl.course.teacher.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(item.cwl.course.teacher)
                    }
                }
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.status == LessonStatus.RUNNING) {
                Text("上课中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
