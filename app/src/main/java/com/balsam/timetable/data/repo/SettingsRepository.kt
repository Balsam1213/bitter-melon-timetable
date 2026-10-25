package com.balsam.timetable.data.repo

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.balsam.timetable.data.holiday.HolidaySyncClient
import com.balsam.timetable.data.model.AppSettings
import com.balsam.timetable.data.model.ColorTheme
import com.balsam.timetable.data.model.DayStatus
import com.balsam.timetable.data.model.HolidayCalendar
import com.balsam.timetable.data.model.HolidayFetch
import com.balsam.timetable.data.model.HolidayLogic
import com.balsam.timetable.data.model.HolidayStore
import com.balsam.timetable.data.model.TextScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.settingsStore by preferencesDataStore(name = "settings")

const val DEFAULT_WEB_IMPORT_URL = "https://yhxt.swjtu.edu.cn/"

/** 2026 年法定节假日（国办发明电〔2025〕7号），ISO 日期；另含 2027 元旦当天 */
val DEFAULT_HOLIDAYS: Set<String> = buildSet {
    val ranges = listOf(
        "2026-01-01" to "2026-01-03", // 元旦
        "2026-02-15" to "2026-02-23", // 春节
        "2026-04-04" to "2026-04-06", // 清明
        "2026-05-01" to "2026-05-05", // 劳动节
        "2026-06-19" to "2026-06-21", // 端午
        "2026-09-25" to "2026-09-27", // 中秋
        "2026-10-01" to "2026-10-07", // 国庆
        "2027-01-01" to "2027-01-01", // 2027 元旦（当天必休）
    )
    for ((startStr, endStr) in ranges) {
        var d = java.time.LocalDate.parse(startStr)
        val end = java.time.LocalDate.parse(endStr)
        while (!d.isAfter(end)) {
            add(d.toString())
            d = d.plusDays(1)
        }
    }
}

/** 2026 年调休上班日（国办发明电〔2025〕7号，与 holiday-cn 2026.json 核对一致） */
val DEFAULT_MAKEUP_DAYS: Set<String> = setOf(
    "2026-01-04", // 周日 · 元旦调休
    "2026-02-14", // 周六 · 春节调休
    "2026-02-28", // 周六 · 春节调休
    "2026-05-09", // 周六 · 劳动节调休
    "2026-09-20", // 周日 · 国庆中秋调休
    "2026-10-10", // 周六 · 国庆中秋调休
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val WEB_IMPORT_URL = stringPreferencesKey("web_import_url")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val LEAD_MINUTES = intPreferencesKey("lead_minutes")
        val SHOW_WEEKEND = booleanPreferencesKey("show_weekend")
        val NOTIF_ASKED = booleanPreferencesKey("notification_asked")
        val HOLIDAY_SKIP = booleanPreferencesKey("holiday_skip_enabled")
        val TEXT_SCALE = stringPreferencesKey("text_scale")
        val COMPACT_GRID = booleanPreferencesKey("compact_grid")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")
        val LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
        val HOLIDAY_OFFICIAL = stringSetPreferencesKey("holiday_official")
        val HOLIDAY_ADDED = stringSetPreferencesKey("holiday_added")
        val HOLIDAY_REMOVED = stringSetPreferencesKey("holiday_removed")
        val HOLIDAY_SYNC_DAY = longPreferencesKey("holiday_sync_day")
        val MAKEUP_OFFICIAL = stringSetPreferencesKey("makeup_official")
        val MAKEUP_ADDED = stringSetPreferencesKey("makeup_added")
        val MAKEUP_REMOVED = stringSetPreferencesKey("makeup_removed")
        /** 元素格式 "yyyy-MM-dd=4"（按周四上课）；仅由用户设置，同步永不触碰 */
        val MAKEUP_DOW = stringSetPreferencesKey("makeup_weekdays")
    }

    val webImportUrl: Flow<String> = context.settingsStore.data.map { p ->
        p[Keys.WEB_IMPORT_URL] ?: DEFAULT_WEB_IMPORT_URL
    }

    suspend fun webImportUrlOnce(): String = webImportUrl.first()

    suspend fun setWebImportUrl(v: String) = context.settingsStore.edit { it[Keys.WEB_IMPORT_URL] = v }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            reminderEnabled = p[Keys.REMINDER_ENABLED] ?: true,
            leadMinutes = p[Keys.LEAD_MINUTES] ?: 15,
            showWeekend = p[Keys.SHOW_WEEKEND] ?: false,
            holidaySkipEnabled = p[Keys.HOLIDAY_SKIP] ?: true,
            textScale = TextScale.from(p[Keys.TEXT_SCALE]),
            compactGrid = p[Keys.COMPACT_GRID] ?: false,
            colorTheme = ColorTheme.from(p[Keys.COLOR_THEME]),
            autoBackupEnabled = p[Keys.AUTO_BACKUP] ?: true,
        )
    }

    suspend fun once(): AppSettings = settings.first()

    suspend fun setReminderEnabled(v: Boolean) = context.settingsStore.edit { it[Keys.REMINDER_ENABLED] = v }
    suspend fun setLeadMinutes(v: Int) = context.settingsStore.edit { it[Keys.LEAD_MINUTES] = v }
    suspend fun setShowWeekend(v: Boolean) = context.settingsStore.edit { it[Keys.SHOW_WEEKEND] = v }
    suspend fun setTextScale(v: TextScale) = context.settingsStore.edit { it[Keys.TEXT_SCALE] = v.name }
    suspend fun setCompactGrid(v: Boolean) = context.settingsStore.edit { it[Keys.COMPACT_GRID] = v }
    suspend fun setColorTheme(v: ColorTheme) = context.settingsStore.edit { it[Keys.COLOR_THEME] = v.name }
    suspend fun setAutoBackupEnabled(v: Boolean) = context.settingsStore.edit { it[Keys.AUTO_BACKUP] = v }

    /** 最近一次自动/手动备份完成的时刻（epoch 毫秒）；从未备份返回 null */
    val lastBackupTime: Flow<Long?> = context.settingsStore.data.map { p -> p[Keys.LAST_BACKUP_TIME] }

    suspend fun markBackupDone() = context.settingsStore.edit {
        it[Keys.LAST_BACKUP_TIME] = System.currentTimeMillis()
    }

    // 节假日 = (官方列表 ∪ 用户手动添加) − 用户手动删除；调休同理，且与放假冲突时放假优先。
    // 官方列表：从未同步过时用预置的 2026 官方安排；联网同步成功后按年替换。
    // 用户动过的日期（增/删）永远不被同步覆盖。
    val holidayCalendar: Flow<HolidayCalendar> = context.settingsStore.data.map { p ->
        HolidayLogic.effective(snapshot(p))
    }

    suspend fun holidayCalendarOnce(): HolidayCalendar = holidayCalendar.first()

    val holidayDates: Flow<Set<String>> = holidayCalendar.map { it.offDays }

    suspend fun holidayDatesOnce(): Set<String> = holidayDates.first()

    /** 日历页标记/清除某天：放假、调休上班（可带「按周几」）、恢复普通日 */
    suspend fun setDayStatus(date: String, status: DayStatus) = context.settingsStore.edit { p ->
        writeStore(p, HolidayLogic.setDayStatus(snapshot(p), date, status))
    }

    /** 单独改调休日「按周几上课」；null = 清除映射、按当天星期 */
    suspend fun setMakeupDow(date: String, dow: Int?) = context.settingsStore.edit { p ->
        writeStore(p, HolidayLogic.setMakeupDow(snapshot(p), date, dow))
    }

    /** 恢复默认 = 清空用户增删与已同步官方数据，回到预置 2026 列表（下次联网会再同步） */
    suspend fun restoreDefaultHolidays() = context.settingsStore.edit {
        it.remove(Keys.HOLIDAY_OFFICIAL)
        it.remove(Keys.HOLIDAY_ADDED)
        it.remove(Keys.HOLIDAY_REMOVED)
        it.remove(Keys.MAKEUP_OFFICIAL)
        it.remove(Keys.MAKEUP_ADDED)
        it.remove(Keys.MAKEUP_REMOVED)
        it.remove(Keys.MAKEUP_DOW)
    }

    private fun snapshot(p: Preferences): HolidayStore = HolidayStore(
        offOfficial = p[Keys.HOLIDAY_OFFICIAL] ?: DEFAULT_HOLIDAYS,
        offAdded = p[Keys.HOLIDAY_ADDED] ?: emptySet(),
        offRemoved = p[Keys.HOLIDAY_REMOVED] ?: emptySet(),
        makeupOfficial = p[Keys.MAKEUP_OFFICIAL] ?: DEFAULT_MAKEUP_DAYS,
        makeupAdded = p[Keys.MAKEUP_ADDED] ?: emptySet(),
        makeupRemoved = p[Keys.MAKEUP_REMOVED] ?: emptySet(),
        makeupDow = (p[Keys.MAKEUP_DOW] ?: emptySet()).mapNotNull(::parseDowEntry).toMap(),
    )

    /** "yyyy-MM-dd=4" → (date, 4)；非法条目丢弃 */
    private fun parseDowEntry(entry: String): Pair<String, Int>? {
        val i = entry.lastIndexOf('=')
        if (i <= 0) return null
        val dow = entry.substring(i + 1).toIntOrNull() ?: return null
        if (dow !in 1..7) return null
        return entry.substring(0, i) to dow
    }

    private fun writeStore(p: MutablePreferences, s: HolidayStore) {
        p[Keys.HOLIDAY_OFFICIAL] = s.offOfficial
        p[Keys.HOLIDAY_ADDED] = s.offAdded
        p[Keys.HOLIDAY_REMOVED] = s.offRemoved
        p[Keys.MAKEUP_OFFICIAL] = s.makeupOfficial
        p[Keys.MAKEUP_ADDED] = s.makeupAdded
        p[Keys.MAKEUP_REMOVED] = s.makeupRemoved
        p[Keys.MAKEUP_DOW] = s.makeupDow.entries.map { "${it.key}=${it.value}" }.toSet()
    }

    suspend fun holidaySkipEnabledOnce(): Boolean =
        context.settingsStore.data.first()[Keys.HOLIDAY_SKIP] ?: true

    suspend fun setHolidaySkipEnabled(v: Boolean) =
        context.settingsStore.edit { it[Keys.HOLIDAY_SKIP] = v }

    /**
     * 联网同步官方节假日安排（每年国务院 11 月左右发布次年安排后即会更新）。
     * 自动同步每天最多尝试一次；手动同步（force=true）绕过限次。
     * 单个年份拉取到数据才替换该年（放假与调休两个官方集合分别替换），拉不到保留原状。
     * 返回生效日历是否发生变化（变化则调用方需重排提醒）。
     */
    suspend fun syncHolidayData(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val today = java.time.LocalDate.now().toEpochDay()
        if (!force) {
            val lastSync = context.settingsStore.data.first()[Keys.HOLIDAY_SYNC_DAY] ?: -1L
            if (lastSync == today) return@withContext false
        }
        val before = holidayCalendarOnce()

        val year = java.time.LocalDate.now().year
        val fetched = mutableMapOf<Int, HolidayFetch>()
        for (y in intArrayOf(year, year + 1)) {
            HolidaySyncClient.fetch(y)?.let { fetched[y] = it }
        }
        if (fetched.isNotEmpty()) {
            val years = fetched.keys
            context.settingsStore.edit { p ->
                val oldOff = p[Keys.HOLIDAY_OFFICIAL] ?: DEFAULT_HOLIDAYS
                p[Keys.HOLIDAY_OFFICIAL] = (
                    oldOff.filterNot { it.substringBefore('-').toIntOrNull() in years } +
                        fetched.values.flatMap { it.offDays }
                    ).toSet()
                val oldMakeup = p[Keys.MAKEUP_OFFICIAL] ?: DEFAULT_MAKEUP_DAYS
                p[Keys.MAKEUP_OFFICIAL] = (
                    oldMakeup.filterNot { it.substringBefore('-').toIntOrNull() in years } +
                        fetched.values.flatMap { it.makeupDays }
                    ).toSet()
                p[Keys.HOLIDAY_SYNC_DAY] = today
            }
        }
        holidayCalendarOnce() != before
    }

    suspend fun notificationAsked(): Boolean =
        context.settingsStore.data.first()[Keys.NOTIF_ASKED] ?: false

    suspend fun markNotificationAsked() = context.settingsStore.edit { it[Keys.NOTIF_ASKED] = true }
}
