package com.balsam.timetable.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balsam.timetable.data.db.Semester

/** 左上角图标：学期切换 + 回到本周 */
@Composable
internal fun SemesterSwitchDialog(
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
