package com.balsam.timetable.ui.holidays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balsam.timetable.Graph
import com.balsam.timetable.data.model.DayStatus
import com.balsam.timetable.data.model.HolidayCalendar
import com.balsam.timetable.data.repo.DEFAULT_HOLIDAYS
import com.balsam.timetable.data.repo.DEFAULT_MAKEUP_DAYS
import com.balsam.timetable.widget.TodayWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 手动同步的结果反馈（旧版设置页同步按钮无任何反馈） */
sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Done(val changed: Boolean) : SyncState
    data object Failed : SyncState
}

class HolidayViewModel : ViewModel() {
    private val settingsRepo = Graph.settingsRepository

    val calendar: StateFlow<HolidayCalendar> = settingsRepo.holidayCalendar
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HolidayCalendar(offDays = DEFAULT_HOLIDAYS, makeupDays = DEFAULT_MAKEUP_DAYS),
        )

    val syncState = MutableStateFlow<SyncState>(SyncState.Idle)

    fun setDayStatus(date: String, status: DayStatus) = viewModelScope.launch {
        settingsRepo.setDayStatus(date, status)
        onDataChanged()
    }

    fun setMakeupDow(date: String, dow: Int?) = viewModelScope.launch {
        settingsRepo.setMakeupDow(date, dow)
        onDataChanged()
    }

    fun restoreDefaults() = viewModelScope.launch {
        settingsRepo.restoreDefaultHolidays()
        onDataChanged()
    }

    /** 手动触发一次同步（绕过每日限次），结果经 [syncState] 反馈 */
    fun syncHolidays() = viewModelScope.launch {
        syncState.value = SyncState.Running
        syncState.value = runCatching { settingsRepo.syncHolidayData(force = true) }
            .fold({ SyncState.Done(it) }, { SyncState.Failed })
        onDataChanged()
    }

    fun consumeSyncState() {
        syncState.value = SyncState.Idle
    }

    /** 节假日影响提醒排布与小组件显示，写操作后统一刷新 */
    private suspend fun onDataChanged() {
        Graph.reminderScheduler.reschedule()
        TodayWidget().updateAll(Graph.appContext)
    }
}
