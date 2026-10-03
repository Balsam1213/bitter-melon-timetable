package com.balsam.timetable.remind

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.balsam.timetable.R
import com.balsam.timetable.ui.MainActivity

object Notifier {

    // v2：v1 渠道创建后重要性不可改，升级渠道 id 以启用横幅+震动
    const val CHANNEL_REMINDER = "course_reminder_v2"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "课前提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "课前若干分钟的通知提醒（横幅 + 声音 + 震动）"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                enableLights(true)
            }
        )
        // 旧版本渠道降级为静默，避免双渠道并存时 v1 继续发无声通知
        nm.getNotificationChannel("course_reminder")?.let {
            it.setSound(null, null)
            it.enableVibration(false)
        }
    }

    fun postCourseReminder(
        context: Context,
        courseName: String,
        slotText: String,
        location: String,
        teacher: String,
    ) {
        ensureChannels(context)
        val content = buildString {
            append(slotText)
            if (location.isNotBlank()) append(" · $location")
            if (teacher.isNotBlank()) append(" · $teacher")
        }
        val tapPi = PendingIntent.getActivity(
            context, 10, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_stat_timetable)
            .setContentTitle("即将上课：$courseName")
            .setContentText(content)
            .setContentIntent(tapPi)
            .setWhen(System.currentTimeMillis())
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(courseName.hashCode(), notification)
    }

    fun cancelCourseReminder(context: Context, courseName: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(courseName.hashCode())
    }
}
