package com.balsam.timetable.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.SizeMode
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.balsam.timetable.Graph
import com.balsam.timetable.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「下一节课」桌面小组件：课程名 + 地点 + 开课/下课时间与粗粒度倒计时。
 * 状态由 [NextClassLogic] 计算；翻面由 [WidgetRefreshScheduler] 的定点闹钟驱动
 * （当前课结束时刻与下一课开课时刻各排一次，触发后 updateAll 重建数据并自动排下一次），
 * 另有 30 分钟系统兜底、数据变更与回前台三条触发链与现有组件一致。
 */
class NextClassWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 任何异常都不能让小组件永远停在加载动画：降级为错误文案
        val data = try {
            withContext(Dispatchers.IO) { buildData(context) }
        } catch (_: Exception) {
            NextClassData(
                NextClassState.NO_CLASS, "数据加载失败", "点这里重试", "", "", "",
            )
        }
        val openIntent = Intent(context, MainActivity::class.java)
        val theme = Graph.settingsRepository.once().colorTheme
        provideContent {
            GlanceTheme(colors = widgetColors(theme)) {
                Content(data, openIntent)
            }
        }
    }

    private suspend fun buildData(context: Context): NextClassData {
        val now = java.time.LocalDateTime.now()
        val semester = Graph.repository.currentSemesterOnce()
        val slots = semester?.let { Graph.repository.slotsOnce(it.id) }.orEmpty()
        val courses = semester?.let { Graph.repository.coursesOnce(it.id) }.orEmpty()
        val holidayCal = Graph.settingsRepository.holidayCalendarOnce()
        val data = NextClassLogic.nextClassData(now, semester, slots, courses, holidayCal)
        // 自续链：排下一次翻面闹钟（上课结束 / 下节课开始）
        NextClassLogic.nextFlipInstant(now, semester, slots, courses, holidayCal)
            ?.let { WidgetRefreshScheduler.schedule(context, it) }
        return data
    }

    @Composable
    private fun Content(data: NextClassData, openIntent: Intent) {
        val compact = LocalSize.current.height.value < 120f
        if (compact) {
            CompactContent(data, openIntent)
        } else {
            LargeContent(data, openIntent)
        }
    }

    /** 大尺寸（≥2 格高）：课程名与倒计时为主视觉，弹性空间放在中段 */
    @Composable
    private fun LargeContent(data: NextClassData, openIntent: Intent) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .clickable(onClick = actionStartActivity(openIntent)),
        ) {
            Text(
                data.header,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.primary,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                data.courseName,
                style = TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
                maxLines = 2,
            )
            if (data.location.isNotBlank()) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    data.location,
                    style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
            }
            if (data.relativeLabel.isNotBlank()) {
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    data.relativeLabel,
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary,
                    ),
                    maxLines = 1,
                )
            }
            if (data.timeLabel.isNotBlank()) {
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    data.timeLabel,
                    style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
            }
        }
    }

    /** 紧凑尺寸（约 1 格高）：只保留课程名与开课时间，字号比完整版小一档 */
    @Composable
    private fun CompactContent(data: NextClassData, openIntent: Intent) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .padding(10.dp)
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .clickable(onClick = actionStartActivity(openIntent)),
        ) {
            Text(
                data.header,
                style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.primary),
                maxLines = 1,
            )
            Text(
                data.courseName,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
                maxLines = 1,
            )
            if (data.timeLabel.isNotBlank()) {
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    data.timeLabel,
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
            }
        }
    }

    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(NextClassWidget::class.java).forEach { update(context, it) }
    }
}

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}
