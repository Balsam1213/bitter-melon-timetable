package com.balsam.timetable.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.balsam.timetable.data.model.DAY_LABELS
import com.balsam.timetable.data.model.WeekPattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownPicker(
    display: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }) { Text(display) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        expanded = false
                        onSelect(i)
                    },
                )
            }
        }
    }
}

@Composable
fun DayDropdown(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    DropdownPicker(
        display = DAY_LABELS.getOrElse(selected - 1) { "周一" },
        options = DAY_LABELS,
        modifier = modifier,
        onSelect = { onSelect(it + 1) },
    )
}

@Composable
fun SlotDropdown(selected: Int, count: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    DropdownPicker(
        display = "第${selected}节",
        options = (1..count).map { "第${it}节" },
        modifier = modifier,
        onSelect = { onSelect(it + 1) },
    )
}

@Composable
fun PatternChips(selected: WeekPattern, onSelect: (WeekPattern) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WeekPattern.entries.forEach { p ->
            FilterChip(
                selected = selected == p,
                onClick = { onSelect(p) },
                label = { Text(p.label) },
            )
        }
    }
}

/** 整数输入框：内部维护文本状态，可先清空再输入新数字；越界自动钳制 */
@Composable
fun NumberField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..40,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            val filtered = v.filter { it.isDigit() }.take(2)
            text = filtered
            filtered.toIntOrNull()?.let { onChange(it.coerceIn(range.first, range.last)) }
        },
        label = { Text(label) },
        modifier = modifier.width(92.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
