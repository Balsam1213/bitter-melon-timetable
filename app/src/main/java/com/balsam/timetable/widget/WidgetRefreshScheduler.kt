package com.balsam.timetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 「下一节课」小组件的定点翻面：在「当前课结束 / 下一节开课」时刻触发一次 updateAll。
 * 需求码用独立区间（1001），与 ReminderScheduler 的 0..49 互不干扰；
 * 每次小组件重建数据后自动排下一个翻面时刻（自续链），重启后由 BootReceiver 的
 * Widgets.updateAll 兜底重排（alarm 丢失后 30 分钟系统兜底 + 回前台刷新补位）。
 */
object WidgetRefreshScheduler {

    private const val REQUEST_CODE = 1001

    /** 排下一次翻面；时刻早于现在（理论不该发生）则忽略 */
    fun schedule(context: Context, instant: LocalDateTime) {
        val triggerAt = instant.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(context)
        // setAlarmClock 通道不会被 Doze 合批（与课前提醒同源）；无权限时降级 setExact
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pi), pi)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WidgetRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** 闹钟触发：刷新全部小组件（数据重建后 NextClassWidget 会自动排下一次翻面） */
class WidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Widgets.updateAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
