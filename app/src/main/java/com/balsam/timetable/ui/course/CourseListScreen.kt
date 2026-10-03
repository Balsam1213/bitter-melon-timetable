package com.balsam.timetable.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balsam.timetable.Graph
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.model.DAY_LABELS
import com.balsam.timetable.data.model.WeekPattern
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class CourseListViewModel : ViewModel() {
    val items: StateFlow<List<CourseWithLessons>> = Graph.repository.currentSemester()
        .flatMapLatest { s ->
            if (s == null) flowOf(emptyList()) else Graph.repository.coursesWithLessons(s.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** 当前学期全部课程列表：点击查看详情 / 编辑 / 删除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(onBack: () -> Unit) {
    val vm: CourseListViewModel = viewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    var sheetCourseId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全部课程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "当前学期还没有课程",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
                items(items, key = { it.course.id }) { cwl ->
                    Card(
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                            .clickable { sheetCourseId = cwl.course.id },
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .background(Color(cwl.course.colorArgb.toInt()), CircleShape),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        cwl.course.name,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (cwl.course.teacher.isNotBlank()) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            cwl.course.teacher,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                cwl.lessons.sortedBy { it.dayOfWeek }.forEach { l ->
                                    Text(
                                        buildString {
                                            append(DAY_LABELS.getOrElse(l.dayOfWeek - 1) { "" })
                                            append(" 第${l.startSlot}-${l.endSlot}节")
                                            append(" · ${l.startWeek}-${l.endWeek}周")
                                            if (l.pattern != WeekPattern.EVERY) append(" ${l.pattern.label}")
                                            if (l.location.isNotBlank()) append(" · ${l.location}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    sheetCourseId?.let { id ->
        CourseEditSheet(courseId = id, prefill = null, onDismiss = { sheetCourseId = null })
    }
}
