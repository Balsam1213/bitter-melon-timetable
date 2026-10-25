package com.balsam.timetable.data.holiday

import com.balsam.timetable.data.model.HolidayFetch
import com.balsam.timetable.data.model.HolidayJsonParser

/**
 * holiday-cn 官方节假日安排的联网拉取（唯一网络职责：URL 兜底尝试 + 解析）。
 * 调用方负责线程（IO）与每日限次；本类无状态、无存储。
 */
object HolidaySyncClient {

    /** 依次尝试公共镜像（国务院通知发布后数日内更新）；失败返回 null */
    fun fetch(year: Int): HolidayFetch? {
        val sources = listOf(
            "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json",
            "https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json",
            "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/$year.json",
        )
        for (url in sources) {
            val result = runCatching { fetchAndParse(url, year) }.getOrNull()
            if (result != null && (result.offDays.isNotEmpty() || result.makeupDays.isNotEmpty())) {
                return result
            }
        }
        return null
    }

    private fun fetchAndParse(url: String, year: Int): HolidayFetch? {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "BitterMelonTimetable/1.2")
        }
        conn.connect()
        if (conn.responseCode != 200) return null
        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return HolidayJsonParser.parse(body, year)
    }
}
