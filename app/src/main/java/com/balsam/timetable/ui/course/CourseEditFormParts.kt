package com.balsam.timetable.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balsam.timetable.data.model.CourseColors
import com.balsam.timetable.data.model.WeeksCodec
import com.balsam.timetable.ui.components.DayDropdown
import com.balsam.timetable.ui.components.SlotDropdown
import com.balsam.timetable.ui.components.WeeksPicker

/** 10 色预设调色板圆点选择 */
@Composable
internal fun ColorPalette(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CourseColors.PALETTE.forEachIndexed { i, c ->
            val color = Color(c.toInt())
            val isSelected = i == selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(i) },
            )
        }
    }
}

/** 课程编辑表单里的「时间段」卡片：星期/节次/周次/地点 */
@Composable
internal fun LessonCard(
    index: Int,
    lesson: CourseEditViewModel.FormLesson,
    slotCount: Int,
    totalWeeks: Int,
    onChange: (CourseEditViewModel.FormLesson) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
) {
    Card(colors = CardDefaults.cardColors()) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("时间段 ${index + 1}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除此时间段",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayDropdown(lesson.dayOfWeek) { onChange(lesson.copy(dayOfWeek = it)) }
                SlotDropdown(lesson.startSlot, slotCount) { v ->
                    onChange(lesson.copy(startSlot = v, endSlot = maxOf(v, lesson.endSlot)))
                }
                Text("—")
                SlotDropdown(lesson.endSlot, slotCount) { v ->
                    onChange(lesson.copy(endSlot = v, startSlot = minOf(v, lesson.startSlot)))
                }
            }
            Spacer(Modifier.height(8.dp))
            var weeksExpanded by rememberSaveable(index) {
                mutableStateOf(false)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "周次：" + (WeeksCodec.label(lesson.weekSet, totalWeeks) ?: "全学期"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lesson.weekSet.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { weeksExpanded = !weeksExpanded }) {
                    Text(if (weeksExpanded) "收起" else "编辑")
                }
            }
            if (weeksExpanded) {
                WeeksPicker(
                    selected = lesson.weekSet,
                    totalWeeks = totalWeeks,
                    onChange = { onChange(lesson.copy(weekSet = it)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = lesson.location,
                onValueChange = { v -> onChange(lesson.copy(location = v)) },
                label = { Text("地点") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterChipStatic(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}
