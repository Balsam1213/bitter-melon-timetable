package com.balsam.timetable.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.balsam.timetable.Graph
import com.balsam.timetable.remind.ReminderPlanner.Occurrence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 课前提醒调度（纯通知模式）：
 * - 应排哪些提醒由 [ReminderPlanner]（纯函数）决策，这里只负责取数与真正排布闹钟；
 * - 优先用 setAlarmClock：系统视为用户闹钟，Doze 深度休眠与厂商省电都不会推迟
 *   （setExactAndAllowWhileIdle 在 Doze 下会被合批到维护窗口，隔夜后的早八提醒
 *    可能晚到几十分钟）；无精确闹钟权限时降级 setExactAndAllowWhileIdle；
 * - 数据变更 / 开机 / 应用启动 / 设置变更 / 每次触发后全量重排（幂等）。
 */
class ReminderScheduler {

    suspend fun reschedule() = withContext(Dispatchers.IO) {
        val context = Graph.appContext
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return@withContext
        cancelAll(am, context)

        val settings = Graph.settingsRepository.once()
        if (!settings.reminderEnabled) return@withContext
        val holidayCal = Graph.settingsRepository.holidayCalendarOnce()
        val semester = Graph.repository.currentSemesterOnce() ?: return@withContext
        val slots = Graph.repository.slotsOnce(semester.id)
        if (slots.isEmpty()) return@withContext
        val courses = Graph.repository.coursesOnce(semester.id)

        val occurrences = ReminderPlanner.plan(
            now = LocalDateTime.now(),
            semester = semester,
            slots = slots,
            courses = courses,
            holidayCal = holidayCal,
            skipHolidays = settings.holidaySkipEnabled,
            leadMinutes = settings.leadMinutes,
        )

        val showIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, com.balsam.timetable.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        occurrences.forEachIndexed { index, occ ->
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
            putExtra(AlarmReceiver.EXTRA_NOTIF_ID, requestCode)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelAll(am: AlarmManager, context: Context) {
        val dummy = Intent(context, AlarmReceiver::class.java)
        for (i in 0 until ReminderPlanner.MAX_ALARMS) {
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
