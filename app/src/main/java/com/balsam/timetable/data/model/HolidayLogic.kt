package com.balsam.timetable.data.model

import java.time.LocalDate

/** 某个日期的节日状态 */
sealed interface DayStatus {
    /** 普通日 */
    data object Normal : DayStatus

    /** 法定放假日（不提醒、课表标记「休」） */
    data object Off : DayStatus

    /**
     * 调休上班日。dow = 按周几的课表上课（1..7），null 表示未设置、按当天自然星期。
     * holiday-cn 数据源只给日期不给「按周几」，需要用户在日历里手动指定。
     */
    data class Makeup(val dow: Int?) : DayStatus
}

/**
 * 节假日/调休的生效视图。日期一律为 yyyy-MM-dd 字符串；周几 1..7（周一..周日）。
 * makeupDays 已与 offDays 求差：同一日期两种标记冲突时，放假优先。
 */
data class HolidayCalendar(
    val offDays: Set<String> = emptySet(),
    val makeupDays: Set<String> = emptySet(),
    val makeupDow: Map<String, Int> = emptyMap(),
) {
    fun statusOf(date: LocalDate): DayStatus {
        val key = date.toString()
        return when {
            key in offDays -> DayStatus.Off
            key in makeupDays -> DayStatus.Makeup(makeupDow[key])
            else -> DayStatus.Normal
        }
    }

    fun isOff(date: LocalDate): Boolean = date.toString() in offDays

    /** 提醒/展示用的有效星期：调休日返回映射周几（未设置则当天自然星期），其余为自然星期 */
    fun effectiveDayOfWeek(date: LocalDate): Int {
        val key = date.toString()
        return if (key in makeupDays) makeupDow[key] ?: date.dayOfWeek.value
        else date.dayOfWeek.value
    }
}

/**
 * DataStore 里节日相关 6 个集合 + 周几映射的原始快照。
 * 放假与调休各走一套「官方 ∪ 手动添加 − 手动删除」差集，同步只重写官方集合，
 * 因此用户手动改过的日期永远不会被同步覆盖。
 */
data class HolidayStore(
    val offOfficial: Set<String> = emptySet(),
    val offAdded: Set<String> = emptySet(),
    val offRemoved: Set<String> = emptySet(),
    val makeupOfficial: Set<String> = emptySet(),
    val makeupAdded: Set<String> = emptySet(),
    val makeupRemoved: Set<String> = emptySet(),
    val makeupDow: Map<String, Int> = emptyMap(),
)

object HolidayLogic {

    /** 差集推导出生效视图；同一日期既是放假又是调休时，放假优先 */
    fun effective(store: HolidayStore): HolidayCalendar {
        val off = (store.offOfficial + store.offAdded) - store.offRemoved
        val makeup = ((store.makeupOfficial + store.makeupAdded) - store.makeupRemoved) - off
        return HolidayCalendar(offDays = off, makeupDays = makeup, makeupDow = store.makeupDow)
    }

    /**
     * 用户在日历里改某个日期的状态后，新的差集快照：
     * - 标记放假：调休一侧让位并清除其周几映射；
     * - 标记调休：放假一侧让位；dow=null 表示按当天星期（清除已有映射）；
     * - 恢复普通日：两侧标记按「手动加的撤加、官方的记删」规则移除。
     */
    fun setDayStatus(store: HolidayStore, date: String, status: DayStatus): HolidayStore {
        var s = store
        if (status !is DayStatus.Off && date in effective(s).offDays) {
            s = removeOff(s, date)
        }
        if (status !is DayStatus.Makeup && date in effective(s).makeupDays) {
            s = removeMakeup(s, date)
        }
        return when (status) {
            DayStatus.Normal -> s
            DayStatus.Off ->
                if (date in s.offRemoved) s.copy(offRemoved = s.offRemoved - date)
                else s.copy(offAdded = s.offAdded + date)
            is DayStatus.Makeup -> {
                val t = if (date in s.makeupRemoved) s.copy(makeupRemoved = s.makeupRemoved - date)
                else s.copy(makeupAdded = s.makeupAdded + date)
                if (status.dow != null) t.copy(makeupDow = t.makeupDow + (date to status.dow))
                else t.copy(makeupDow = t.makeupDow - date)
            }
        }
    }

    /** 单独改某个调休日「按周几上课」；dow=null 表示清除映射、回到按当天星期 */
    fun setMakeupDow(store: HolidayStore, date: String, dow: Int?): HolidayStore =
        if (dow == null) store.copy(makeupDow = store.makeupDow - date)
        else store.copy(makeupDow = store.makeupDow + (date to dow))

    private fun removeOff(s: HolidayStore, date: String): HolidayStore =
        if (date in s.offAdded) s.copy(offAdded = s.offAdded - date)
        else s.copy(offRemoved = s.offRemoved + date)

    private fun removeMakeup(s: HolidayStore, date: String): HolidayStore =
        (
            if (date in s.makeupAdded) s.copy(makeupAdded = s.makeupAdded - date)
            else s.copy(makeupRemoved = s.makeupRemoved + date)
            ).copy(makeupDow = s.makeupDow - date)
}

/** holiday-cn 数据源的解析结果：放假日与调休上班日两个集合 */
data class HolidayFetch(val offDays: Set<String>, val makeupDays: Set<String>)

/**
 * 兼容多种数据源的宽松解析：递归遍历 JSON，凡是带 "yyyy-MM-dd" 日期字符串字段、
 * 且布尔标记（holiday / isOffDay）明确的对象视为一条节假日安排：
 * true → 放假日，false → 调休上班日；没有布尔标记的日期对象忽略。
 */
object HolidayJsonParser {

    private val DATE_RE = Regex("\\d{4}-\\d{2}-\\d{2}")

    fun parse(body: String, year: Int): HolidayFetch {
        val el = kotlinx.serialization.json.Json.parseToJsonElement(body)
        val off = mutableSetOf<String>()
        val makeup = mutableSetOf<String>()
        walk(el, year, off, makeup)
        return HolidayFetch(off, makeup)
    }

    private fun walk(
        el: kotlinx.serialization.json.JsonElement,
        year: Int,
        off: MutableSet<String>,
        makeup: MutableSet<String>,
    ) {
        when (el) {
            is kotlinx.serialization.json.JsonObject -> {
                var date: String? = null
                var flag: Boolean? = null
                for ((k, v) in el) {
                    if (v is kotlinx.serialization.json.JsonPrimitive) {
                        if (v.isString) {
                            val s = v.content
                            if (s.length == 10 && s.matches(DATE_RE)) date = s
                        } else if (k == "holiday" || k == "isOffDay") {
                            flag = v.content == "true"
                        }
                    }
                }
                val d = date
                if (d != null && flag != null && d.startsWith("$year-")) {
                    if (flag == true) off.add(d) else makeup.add(d)
                }
                for (v in el.values) walk(v, year, off, makeup)
            }
            is kotlinx.serialization.json.JsonArray -> for (v in el) walk(v, year, off, makeup)
            else -> Unit
        }
    }
}
