package com.balsam.timetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Lesson
import com.balsam.timetable.data.model.WeeksCodec

/** 周网格里的课程色块卡片：颜色自适应文字、单节小卡压缩排版；字号随显示设置 */
@Composable
internal fun LessonCard(
    cwl: CourseWithLessons,
    lesson: Lesson,
    totalWeeks: Int,
    metrics: GridMetrics,
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
    val weekLabel = WeeksCodec.label(lesson.weekSet, totalWeeks)
    Column(
        modifier
            .clip(RoundedCornerShape(if (compact) 7.dp else 8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = metrics.cardPaddingH, vertical = metrics.cardPaddingV),
    ) {
        Text(
            cwl.course.name,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) metrics.nameCompactSp else metrics.nameSp,
            lineHeight = if (compact) metrics.nameCompactLineHeight else metrics.nameLineHeight,
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
                fontSize = metrics.subSp,
                lineHeight = metrics.subLineHeight,
                color = onColor.copy(alpha = 0.88f),
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 单节小卡只在带单双周信息时才挤得下周次标签
        val showWeek = weekLabel != null &&
            (!compact || weekLabel.contains("单周") || weekLabel.contains("双周"))
        if (showWeek) {
            Text(
                weekLabel,
                fontSize = metrics.subSp,
                lineHeight = metrics.subLineHeight,
                color = onColor.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hint != null) {
            Text(
                hint,
                fontSize = metrics.subSp,
                lineHeight = metrics.subLineHeight,
                fontWeight = FontWeight.SemiBold,
                color = onColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
