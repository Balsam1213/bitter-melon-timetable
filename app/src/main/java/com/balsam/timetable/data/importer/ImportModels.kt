package com.balsam.timetable.data.importer

import com.balsam.timetable.data.model.WeekPattern
import com.balsam.timetable.data.model.WeeksCodec
import kotlinx.serialization.Serializable

@Serializable
data class ExportSemester(val name: String, val startDate: String, val totalWeeks: Int)

@Serializable
data class ExportSlot(val start: String, val end: String)

/**
 * 导出条目。v2 起周次本体是 [weeks] 集合字符串；startWeek/endWeek/pattern 为兼容
 * v1 版本的推导字段（任意集合无法精确反推时用 min/max + EVERY 超集近似），
 * 导入时优先读 weeks。
 */
@Serializable
data class ExportEntry(
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val day: Int,
    val startSlot: Int,
    val endSlot: Int,
    val startWeek: Int = 1,
    val endWeek: Int = 16,
    val pattern: String = "EVERY",
    val weeks: String? = null,
) {
    fun toParsedEntry() = ParsedEntry(
        name = name, teacher = teacher, location = location,
        dayOfWeek = day, startSlot = startSlot, endSlot = endSlot,
        weeks = weeks?.let { WeeksCodec.parse(it) }?.takeIf { it.isNotEmpty() }
            ?: WeeksCodec.fromRange(startWeek, endWeek, WeekPattern.from(pattern)),
    )

    companion object {
        /** 从周次集合构造导出条目（旧字段由集合推导） */
        fun fromWeeks(
            name: String, teacher: String, location: String,
            day: Int, startSlot: Int, endSlot: Int, weekSet: Set<Int>,
        ) = ExportEntry(
            name = name, teacher = teacher, location = location,
            day = day, startSlot = startSlot, endSlot = endSlot,
            startWeek = weekSet.minOrNull() ?: 1,
            endWeek = weekSet.maxOrNull() ?: 1,
            pattern = (WeeksCodec.toRange(weekSet)?.third ?: WeekPattern.EVERY).name,
            weeks = WeeksCodec.format(weekSet),
        )
    }
}

@Serializable
data class ExportDoc(
    val version: Int = 2,
    val semester: ExportSemester,
    val slots: List<ExportSlot> = emptyList(),
    val entries: List<ExportEntry> = emptyList(),
)

/** 解析中间态：导入预览页里逐条展示、可编辑、可勾选 */
data class ParsedEntry(
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int = 1,
    val startSlot: Int = 1,
    val endSlot: Int = 1,
    val weeks: Set<Int> = (1..16).toSet(),
    val include: Boolean = true,
)
