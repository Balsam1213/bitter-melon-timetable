package com.balsam.timetable

import android.content.Context
import com.balsam.timetable.data.db.AppDatabase
import com.balsam.timetable.data.repo.SettingsRepository
import com.balsam.timetable.data.repo.TimetableRepository
import com.balsam.timetable.remind.ReminderScheduler
import com.balsam.timetable.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 轻量的全局依赖容器（无账号无联网，全部本机）；持有的是 applicationContext，无泄漏风险 */
@android.annotation.SuppressLint("StaticFieldLeak")
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
            Widgets.updateAll(appContext)
        }
    }

    /**
     * 仅供 instrumented 测试：把数据库换成内存库（UI 测试基座，模式照抄 ImportRepositoryTest）。
     * 必须在创建 ViewModel 之前调用（依赖在构造时从 Graph 快照）；
     * 新 repository 的 onDataChanged 为 null，测试中不会重排闹钟或刷新小组件。
     * appContext / settingsRepository / reminderScheduler 沿用真实 Application 初始化的实例。
     */
    @androidx.annotation.VisibleForTesting
    fun swapForTest(db: AppDatabase) {
        database = db
        repository = TimetableRepository(db)
    }

    /** 在合适时机（页面回到前台等）刷新提醒与小组件 */
    fun refreshRemindersAndWidget() {
        appScope.launch {
            repository.onDataChanged?.invoke()
        }
    }
}
