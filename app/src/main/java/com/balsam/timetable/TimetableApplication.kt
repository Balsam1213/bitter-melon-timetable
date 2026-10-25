package com.balsam.timetable

import android.app.Application
import com.balsam.timetable.data.backup.AutoBackup
import com.balsam.timetable.remind.Notifier
import kotlinx.coroutines.launch

class TimetableApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifier.ensureChannels(this)
        // 应用启动时兜底重排提醒（覆盖安装、进程被杀等场景）
        Graph.appScope.launch { Graph.reminderScheduler.reschedule() }
        // 每日自动备份当前学期（KEEP 策略：已排期则不动）
        Graph.appScope.launch {
            if (Graph.settingsRepository.once().autoBackupEnabled) {
                AutoBackup.ensureScheduled(this@TimetableApplication)
            }
        }
        // 联网时自动同步官方节假日安排（每天最多一次；失败静默保留现有列表）
        Graph.appScope.launch {
            runCatching {
                if (Graph.settingsRepository.syncHolidayData()) {
                    Graph.reminderScheduler.reschedule()
                }
            }
        }
    }
}
