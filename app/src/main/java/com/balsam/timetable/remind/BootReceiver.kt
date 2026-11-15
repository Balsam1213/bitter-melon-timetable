package com.balsam.timetable.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.balsam.timetable.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手机重启后系统闹钟全部丢失，开机广播里重新排布；
 * 应用被覆盖安装后同样需要重排（MY_PACKAGE_REPLACED）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Graph.reminderScheduler.reschedule()
                // 小组件的翻面闹钟同样会丢；updateAll 重建数据时自动排下一次翻面
                com.balsam.timetable.widget.Widgets.updateAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
