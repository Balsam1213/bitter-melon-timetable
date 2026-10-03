package com.balsam.timetable.ui.settings

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balsam.timetable.Graph
import com.balsam.timetable.data.model.AppSettings
import com.balsam.timetable.data.model.ReminderMode
import com.balsam.timetable.data.repo.DEFAULT_HOLIDAYS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {
    private val settingsRepo = Graph.settingsRepository
    private val repo = Graph.repository

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // 提醒相关设置变更后立即重排闹钟，否则要等下次数据变更/重启才生效
    fun setReminderEnabled(v: Boolean) = viewModelScope.launch {
        settingsRepo.setReminderEnabled(v)
        Graph.reminderScheduler.reschedule()
    }

    fun setLeadMinutes(v: Int) = viewModelScope.launch {
        settingsRepo.setLeadMinutes(v.coerceIn(0, 120))
        Graph.reminderScheduler.reschedule()
    }

    fun setDefaultMode(m: ReminderMode) = viewModelScope.launch {
        settingsRepo.setDefaultMode(m)
        Graph.reminderScheduler.reschedule()
    }

    fun setShowWeekend(v: Boolean) = viewModelScope.launch { settingsRepo.setShowWeekend(v) }

    val holidayDates: StateFlow<Set<String>> = settingsRepo.holidayDates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_HOLIDAYS)

    fun setHolidaySkipEnabled(v: Boolean) = viewModelScope.launch {
        settingsRepo.setHolidaySkipEnabled(v)
        Graph.reminderScheduler.reschedule()
    }

    fun addHolidayDate(date: String) = viewModelScope.launch {
        settingsRepo.addHolidayDate(date)
        Graph.reminderScheduler.reschedule()
    }

    fun removeHolidayDate(date: String) = viewModelScope.launch {
        settingsRepo.removeHolidayDate(date)
        Graph.reminderScheduler.reschedule()
    }

    fun restoreDefaultHolidays() = viewModelScope.launch {
        settingsRepo.restoreDefaultHolidays()
        Graph.reminderScheduler.reschedule()
    }

    /** 手动触发一次同步（设置页用，绕过每日限次）；返回是否生效列表有变化 */
    fun syncHolidays() = viewModelScope.launch {
        runCatching {
            if (settingsRepo.syncHolidayData(force = true)) {
                Graph.reminderScheduler.reschedule()
            }
        }
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return Graph.appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun requestExactAlarmIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${Graph.appContext.packageName}"))

    fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, Graph.appContext.packageName)

    /** 导出当前学期 JSON；返回错误信息（成功返回 null） */
    suspend fun exportToUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val semester = repo.currentSemesterOnce() ?: return@withContext "没有当前学期"
            val doc = repo.exportDoc(semester.id) ?: return@withContext "导出失败"
            val json = kotlinx.serialization.json.Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }.encodeToString(
                com.balsam.timetable.data.importer.ExportDoc.serializer(), doc,
            )
            Graph.appContext.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
            } ?: return@withContext "无法写入所选文件"
            null
        } catch (e: Exception) {
            "导出失败：${e.message}"
        }
    }
}
