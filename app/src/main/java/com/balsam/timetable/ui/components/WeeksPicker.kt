package com.balsam.timetable.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 周次多选：1..totalWeeks 的 chip 网格 + 快捷操作。
 * 「单周/双周」作用于当前所选 min..max 区间（未选时作用于全学期范围）。
 * 空集由保存入口拦截（至少选一周）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeksPicker(
    selected: Set<Int>,
    totalWeeks: Int,
    onChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spanLo = selected.minOrNull() ?: 1
    val spanHi = selected.maxOrNull() ?: totalWeeks
    Column(modifier) {
        Row {
            TextButton(onClick = { onChange((1..totalWeeks).toSet()) }) { Text("全学期") }
            TextButton(onClick = { onChange((spanLo..spanHi).filter { it % 2 == 1 }.toSet()) }) { Text("单周") }
            TextButton(onClick = { onChange((spanLo..spanHi).filter { it % 2 == 0 }.toSet()) }) { Text("双周") }
            TextButton(onClick = { onChange(emptySet()) }) { Text("清空") }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (w in 1..totalWeeks) {
                FilterChip(
                    selected = w in selected,
                    onClick = { onChange(if (w in selected) selected - w else selected + w) },
                    label = { Text(w.toString()) },
                )
            }
        }
    }
}
