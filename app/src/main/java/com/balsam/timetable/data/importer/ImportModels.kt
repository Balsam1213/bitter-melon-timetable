package com.balsam.timetable.data.importer

import com.balsam.timetable.data.model.WeekPattern
import kotlinx.serialization.Serializable

@Serializable
data class ExportSemester(val name: String, val startDate: String, val totalWeeks: Int)

@Serializable
data class ExportSlot(val start: String, val end: String)

@Serializable
data class ExportEntry(
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val day: Int,
    val startSlot: Int,
    val endSlot: Int,
    val startWeek: Int,
    val endWeek: Int,
    val pattern: String = "EVERY",
) {
    fun toParsedEntry() = ParsedEntry(
        name = name, teacher = teacher, location = location,
        dayOfWeek = day, startSlot = startSlot, endSlot = endSlot,
        startWeek = startWeek, endWeek = endWeek,
        weekPattern = WeekPattern.from(pattern),
    )
}

@Serializable
data class ExportDoc(
    val version: Int = 1,
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
    val startWeek: Int = 1,
    val endWeek: Int = 16,
    val weekPattern: WeekPattern = WeekPattern.EVERY,
    val include: Boolean = true,
)
