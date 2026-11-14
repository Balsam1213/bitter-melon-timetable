package com.balsam.timetable.data.model

import java.time.LocalDate

/**
 * 学期默认值：首启向导、学期管理、无学期导入兜底共用同一套口径，
 * 避免多处私有实现随时间漂移（名称口径变了、别处还按旧规则预填）。
 */
object SemesterDefaults {

    const val TOTAL_WEEKS = 20

    /** 学期名称建议：8 月起算新学年；2-7 月为第二学期，其余第一学期 */
    fun nameSuggestion(today: LocalDate = LocalDate.now()): String {
        val year = today.year
        val month = today.monthValue
        val academicYear = if (month >= 8) "$year-${year + 1}" else "${year - 1}-$year"
        val term = if (month in 2..7) "第二学期" else "第一学期"
        return "${academicYear}学年 $term"
    }

    /** 开学日建议：本周一（第 1 周周一，与周次计算口径一致） */
    fun startSuggestion(today: LocalDate = LocalDate.now()): String = WeekLogic.mondayOf(today).toString()
}
