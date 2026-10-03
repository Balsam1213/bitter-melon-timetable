package com.balsam.timetable.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.balsam.timetable.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: return@launch
                val slotText = intent.getStringExtra(EXTRA_SLOT_TEXT) ?: ""
                val location = intent.getStringExtra(EXTRA_LOCATION) ?: ""
                val teacher = intent.getStringExtra(EXTRA_TEACHER) ?: ""

                // 排闹钟后才开启节假日开关/修改节假日列表时，触发时刻再拦一道
                val settings = Graph.settingsRepository.once()
                if (settings.holidaySkipEnabled) {
                    val today = java.time.LocalDate.now().toString()
                    if (today in Graph.settingsRepository.holidayDatesOnce()) return@launch
                }

                Notifier.postCourseReminder(context, courseName, slotText, location, teacher)

                // 触发后重排后续提醒
                Graph.reminderScheduler.reschedule()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_COURSE_NAME = "course_name"
        const val EXTRA_SLOT_TEXT = "slot_text"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_TEACHER = "teacher"
    }
}
