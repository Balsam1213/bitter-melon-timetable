package com.balsam.timetable.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.balsam.timetable.Graph
import com.balsam.timetable.data.model.WeekLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 课前提醒调度（纯通知模式）：
 * - 计算当前学期未来 7 天内所有上课事件，按「上课时间 - 提前量」排布闹钟；
 * - 优先用 setAlarmClock：系统视为用户闹钟，Doze 深度休眠与厂商省电都不会推迟
 *   （setExactAndAllowWhileIdle 在 Doze 下会被合批到维护窗口，隔夜后的早八提醒
 *    可能晚到几十分钟）；无精确闹钟权限时降级 setExactAndAllowWhileIdle；
 * - 节假日（法定假日当天）不发提醒；调休上班日正常提醒；
 * - 数据变更 / 开机 / 应用启动 / 设置变更 / 每次触发后全量重排（幂等）。
 */
class ReminderScheduler {

    companion object {
        private const val MAX_ALARMS = 50
        private const val HORIZON_DAYS = 7L
    }

    data class Occurrence(
        val remindAt: LocalDateTime,
        val startAt: LocalDateTime,
        val courseName: String,
        val location: String,
        val teacher: String,
        val slotText: String,
    )

    suspend fun reschedule() = withContext(Dispatchers.IO) {
        val context = Graph.appContext
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return@withContext
        cancelAll(am, context)

        val settings = Graph.settingsRepository.once()
        if (!settings.reminderEnabled) return@withContext
        val skipHolidays = settings.holidaySkipEnabled
        val holidays = if (skipHolidays) Graph.settingsRepository.holidayDatesOnce() else emptySet()
        val semester = Graph.repository.currentSemesterOnce() ?: return@withContext
        val slots = Graph.repository.slotsOnce(semester.id)
        val numbered = slots
        if (numbered.isEmpty()) return@withContext
        val courses = Graph.repository.coursesOnce(semester.id)

        val now = LocalDateTime.now()
        val occurrences = mutableListOf<Occurrence>()
        var date: LocalDate = now.toLocalDate()
        val end = now.toLocalDate().plusDays(HORIZON_DAYS)
        while (!date.isAfter(end)) {
            if (date.toString() in holidays) {
                date = date.plusDays(1)
                continue
            }
            val week = WeekLogic.weekOf(date, semester.startDate, semester.totalWeeks)
            if (week != null) {
                for (cwl in courses) {
                    // 单课设置已简化：GLOBAL→跟随全局通知，OFF→不提醒
                    if (com.balsam.timetable.data.model.ReminderMode.from(cwl.course.reminderMode) ==
                        com.balsam.timetable.data.model.ReminderMode.OFF
                    ) continue
                    for (lesson in cwl.lessons) {
                        if (lesson.dayOfWeek != date.dayOfWeek.value) continue
                        if (!WeekLogic.lessonActive(week, lesson.startWeek, lesson.endWeek, lesson.pattern)) continue
                        val startSlot = numbered.getOrNull(lesson.startSlot - 1) ?: continue
                        val startAt = date.atTime(
                            runCatching { LocalTime.parse(startSlot.startTime) }.getOrDefault(LocalTime.of(8, 0))
                        )
                        val remindAt = startAt.minusMinutes(settings.leadMinutes.toLong())
                        if (remindAt.isBefore(now)) continue
                        val endSlot = numbered.getOrNull(lesson.endSlot - 1)
                        val range = if (lesson.endSlot > lesson.startSlot) "${lesson.startSlot}-${lesson.endSlot}" else "${lesson.startSlot}"
                        val timeRange = endSlot?.let { "${startSlot.startTime}-${it.endTime}" } ?: startSlot.startTime
                        occurrences.add(
                            Occurrence(
                                remindAt = remindAt,
                                startAt = startAt,
                                courseName = cwl.course.name,
                                location = lesson.location,
                                teacher = cwl.course.teacher,
                                slotText = "第${range}节 $timeRange",
                            )
                        )
                    }
                }
            }
            date = date.plusDays(1)
        }

        occurrences.sortBy { it.remindAt }
        val showIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, com.balsam.timetable.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        occurrences.take(MAX_ALARMS).forEachIndexed { index, occ ->
            val pi = pendingIntent(context, index, occ)
            val millis = toMillis(occ.remindAt)
            if (canExact) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showIntent), pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        }
        Unit
    }

    private fun pendingIntent(context: Context, requestCode: Int, occ: Occurrence): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_COURSE_NAME, occ.courseName)
            putExtra(AlarmReceiver.EXTRA_SLOT_TEXT, occ.slotText)
            putExtra(AlarmReceiver.EXTRA_LOCATION, occ.location)
            putExtra(AlarmReceiver.EXTRA_TEACHER, occ.teacher)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelAll(am: AlarmManager, context: Context) {
        val dummy = Intent(context, AlarmReceiver::class.java)
        for (i in 0 until MAX_ALARMS) {
            val pi = PendingIntent.getBroadcast(
                context, i, dummy,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) am.cancel(pi)
        }
    }

    private fun toMillis(dt: LocalDateTime): Long =
        dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
