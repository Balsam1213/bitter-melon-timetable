package com.balsam.timetable

import android.content.Context
import com.balsam.timetable.data.db.AppDatabase
import com.balsam.timetable.data.repo.SettingsRepository
import com.balsam.timetable.data.repo.TimetableRepository
import com.balsam.timetable.remind.ReminderScheduler
import com.balsam.timetable.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 轻量的全局依赖容器（无账号无联网，全部本机） */
object Graph {

    lateinit var appContext: Context
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var repository: TimetableRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var reminderScheduler: ReminderScheduler
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        appContext = context.applicationContext
        database = AppDatabase.build(appContext)
        repository = TimetableRepository(database)
        settingsRepository = SettingsRepository(appContext)
        reminderScheduler = ReminderScheduler()

        repository.onDataChanged = {
            reminderScheduler.reschedule()
            TodayWidget().updateAll(appContext)
        }
    }

    /** 在合适时机（页面回到前台等）刷新提醒与小组件 */
    fun refreshRemindersAndWidget() {
        appScope.launch {
            repository.onDataChanged?.invoke()
        }
    }
}
