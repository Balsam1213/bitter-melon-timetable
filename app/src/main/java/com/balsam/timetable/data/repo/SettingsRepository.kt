package com.balsam.timetable.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.balsam.timetable.data.model.AppSettings
import com.balsam.timetable.data.model.ReminderMode
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

class SettingsRepository(private val context: Context) {

    private object Keys {
        val WEB_IMPORT_URL = stringPreferencesKey("web_import_url")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val LEAD_MINUTES = intPreferencesKey("lead_minutes")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val SHOW_WEEKEND = booleanPreferencesKey("show_weekend")
        val NOTIF_ASKED = booleanPreferencesKey("notification_asked")
        val HOLIDAY_SKIP = booleanPreferencesKey("holiday_skip_enabled")
        val HOLIDAY_OFFICIAL = stringSetPreferencesKey("holiday_official")
        val HOLIDAY_ADDED = stringSetPreferencesKey("holiday_added")
        val HOLIDAY_REMOVED = stringSetPreferencesKey("holiday_removed")
        val HOLIDAY_SYNC_DAY = longPreferencesKey("holiday_sync_day")
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
            defaultMode = ReminderMode.from(p[Keys.DEFAULT_MODE]),
            showWeekend = p[Keys.SHOW_WEEKEND] ?: false,
            holidaySkipEnabled = p[Keys.HOLIDAY_SKIP] ?: true,
        )
    }

    suspend fun once(): AppSettings = settings.first()

    suspend fun setReminderEnabled(v: Boolean) = context.settingsStore.edit { it[Keys.REMINDER_ENABLED] = v }
    suspend fun setLeadMinutes(v: Int) = context.settingsStore.edit { it[Keys.LEAD_MINUTES] = v }
    suspend fun setDefaultMode(v: ReminderMode) = context.settingsStore.edit { it[Keys.DEFAULT_MODE] = v.name }
    suspend fun setShowWeekend(v: Boolean) = context.settingsStore.edit { it[Keys.SHOW_WEEKEND] = v }

    // 节假日 = (官方列表 ∪ 用户手动添加) − 用户手动删除。
    // 官方列表：从未同步过时用预置的 2026 官方安排；联网同步成功后按年替换。
    // 用户动过的日期（增/删）永远不被同步覆盖。
    val holidayDates: Flow<Set<String>> = context.settingsStore.data.map { p ->
        val official = p[Keys.HOLIDAY_OFFICIAL] ?: DEFAULT_HOLIDAYS
        val added = p[Keys.HOLIDAY_ADDED] ?: emptySet()
        val removed = p[Keys.HOLIDAY_REMOVED] ?: emptySet()
        (official + added) - removed
    }

    suspend fun holidayDatesOnce(): Set<String> = holidayDates.first()

    suspend fun addHolidayDate(date: String) = context.settingsStore.edit { p ->
        val removed = p[Keys.HOLIDAY_REMOVED] ?: emptySet()
        if (date in removed) {
            p[Keys.HOLIDAY_REMOVED] = removed - date
        } else {
            p[Keys.HOLIDAY_ADDED] = (p[Keys.HOLIDAY_ADDED] ?: emptySet()) + date
        }
    }

    suspend fun removeHolidayDate(date: String) = context.settingsStore.edit { p ->
        val added = p[Keys.HOLIDAY_ADDED] ?: emptySet()
        if (date in added) {
            p[Keys.HOLIDAY_ADDED] = added - date
        } else {
            p[Keys.HOLIDAY_REMOVED] = (p[Keys.HOLIDAY_REMOVED] ?: emptySet()) + date
        }
    }

    /** 恢复默认 = 清空用户增删与已同步官方数据，回到预置 2026 列表（下次联网会再同步） */
    suspend fun restoreDefaultHolidays() = context.settingsStore.edit {
        it.remove(Keys.HOLIDAY_OFFICIAL)
        it.remove(Keys.HOLIDAY_ADDED)
        it.remove(Keys.HOLIDAY_REMOVED)
    }

    suspend fun holidaySkipEnabledOnce(): Boolean =
        context.settingsStore.data.first()[Keys.HOLIDAY_SKIP] ?: true

    suspend fun setHolidaySkipEnabled(v: Boolean) =
        context.settingsStore.edit { it[Keys.HOLIDAY_SKIP] = v }

    /**
     * 联网同步官方节假日安排（每年国务院 11 月左右发布次年安排后即会更新）。
     * 自动同步每天最多尝试一次；手动同步（force=true）绕过限次。
     * 单个年份拉取到数据才替换该年，拉不到保留原状。
     * 返回生效列表是否发生变化（变化则调用方需重排提醒）。
     */
    suspend fun syncHolidayData(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val today = java.time.LocalDate.now().toEpochDay()
        if (!force) {
            val lastSync = context.settingsStore.data.first()[Keys.HOLIDAY_SYNC_DAY] ?: -1L
            if (lastSync == today) return@withContext false
        }
        val before = holidayDatesOnce()
        android.util.Log.d("HolidaySync", "start")

        val year = java.time.LocalDate.now().year
        val fetched = mutableMapOf<Int, Set<String>>()
        for (y in intArrayOf(year, year + 1)) {
            fetchOfficialHolidays(y)?.let { fetched[y] = it }
        }
        android.util.Log.d("HolidaySync", "fetched years=${fetched.keys}")
        if (fetched.isNotEmpty()) {
            context.settingsStore.edit { p ->
                val old = p[Keys.HOLIDAY_OFFICIAL] ?: DEFAULT_HOLIDAYS
                val kept = old.filterNot { it.substringBefore('-').toIntOrNull() in fetched.keys }
                p[Keys.HOLIDAY_OFFICIAL] = (kept + fetched.values.flatten()).toSet()
                p[Keys.HOLIDAY_SYNC_DAY] = today
            }
            android.util.Log.d(
                "HolidaySync",
                "updated years=${fetched.keys} total=${(holidayDatesOnce()).size}"
            )
        }
        holidayDatesOnce() != before
    }

    /** 依次尝试公共节假日数据源（holiday-cn 项目，国务院通知发布后数日内更新）；失败返回 null */
    private fun fetchOfficialHolidays(year: Int): Set<String>? {
        val sources = listOf(
            "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json",
            "https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json",
            "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/$year.json",
        )
        for (url in sources) {
            val dates = runCatching { fetchAndParseHolidays(url, year) }
                .onFailure { android.util.Log.d("HolidaySync", "$url -> ${it.javaClass.simpleName}: ${it.message}") }
                .getOrNull()
            if (!dates.isNullOrEmpty()) {
                android.util.Log.d("HolidaySync", "$year from ${url.substringBefore('/')}: ${dates.size} days")
                return dates
            }
        }
        return null
    }

    private fun fetchAndParseHolidays(url: String, year: Int): Set<String>? {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "BitterMelonTimetable/1.0")
        }
        conn.connect()
        if (conn.responseCode != 200) return null
        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val dates = mutableSetOf<String>()
        walkHolidayJson(kotlinx.serialization.json.Json.parseToJsonElement(body), year, dates)
        return dates
    }

    /**
     * 兼容两种数据源的宽松解析：递归遍历 JSON，凡是带
     * "yyyy-MM-dd" 日期字符串字段且布尔标记（holiday / isOffDay）为 true 的对象
     * 视为一个法定放假日；false（调休上班日）跳过。
     */
    private fun walkHolidayJson(el: kotlinx.serialization.json.JsonElement, year: Int, out: MutableSet<String>) {
        when (el) {
            is kotlinx.serialization.json.JsonObject -> {
                var date: String? = null
                var off = false
                for ((k, v) in el) {
                    if (v is kotlinx.serialization.json.JsonPrimitive && v.isString) {
                        val s = v.content
                        if (s.length == 10 && s.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) date = s
                    } else if (v is kotlinx.serialization.json.JsonPrimitive && (k == "holiday" || k == "isOffDay")) {
                        off = v.content == "true"
                    }
                }
                val d = date
                if (d != null && off && d.startsWith("$year-")) out.add(d)
                for (v in el.values) walkHolidayJson(v, year, out)
            }
            is kotlinx.serialization.json.JsonArray -> for (v in el) walkHolidayJson(v, year, out)
            else -> Unit
        }
    }

    suspend fun notificationAsked(): Boolean =
        context.settingsStore.data.first()[Keys.NOTIF_ASKED] ?: false

    suspend fun markNotificationAsked() = context.settingsStore.edit { it[Keys.NOTIF_ASKED] = true }
}
