package com.balsam.timetable.data.importer

import com.balsam.timetable.data.model.WeeksCodec

/**
 * 导入计划：把预览确认后的条目整理成可入库的形态。
 * 校验、周次收敛、与已有课程去重都是纯函数逻辑（单测覆盖），
 * TimetableRepository 只负责在事务里执行计划结果。
 */
object ImportPlanner {

    const val REASON_NO_NAME = "课程名为空"
    const val REASON_BAD_DAY = "星期值无效（应为 1-7，周一到周日）"
    const val REASON_BAD_SLOT = "节次号无效"
    const val REASON_WEEKS_EMPTY = "周次为空"
    const val REASON_WEEKS_OUT_OF_RANGE = "周次全部超出学期总周数"

    data class InvalidEntry(val entry: ParsedEntry, val reason: String)

    /** 已有课程时间段的比对指纹：完全重复判定只看这些字段 */
    data class LessonFingerprint(
        val dayOfWeek: Int,
        val startSlot: Int,
        val endSlot: Int,
        val weeks: Set<Int>,
        val location: String,
    )

    data class ExistingCourse(
        val name: String,
        val teacher: String,
        val lessons: List<LessonFingerprint>,
    )

    data class PlannedLesson(
        val dayOfWeek: Int,
        val startSlot: Int,
        val endSlot: Int,
        val weeks: Set<Int>,
        val location: String,
    ) {
        fun fingerprint() = LessonFingerprint(dayOfWeek, startSlot, endSlot, weeks, location)
    }

    data class PlannedCourse(
        val name: String,
        val teacher: String,
        val lessons: List<PlannedLesson>,
    )

    data class Plan(
        /** 待入库课程（同名同教师合并、已剔除与现有课程完全重复的条目） */
        val courses: List<PlannedCourse>,
        /** 合法条目里最大的节次号（节次表不足时据此补行；0 = 无） */
        val maxSlotNeeded: Int,
        /** 因与已有课程完全重复而跳过的条目数 */
        val skippedDuplicates: Int,
        /** 无效条目及原因（不写库，完成反馈里告知用户） */
        val invalid: List<InvalidEntry>,
    )

    // 条目分流循环的多出口 continue 是预览标注的自然写法
    @Suppress("LoopWithTooManyJumpStatements")
    fun plan(
        entries: List<ParsedEntry>,
        existing: List<ExistingCourse>,
        totalWeeks: Int,
    ): Plan {
        val invalid = mutableListOf<InvalidEntry>()
        val planned = mutableListOf<Pair<ParsedEntry, PlannedLesson>>()
        var skippedDuplicates = 0

        for (e in entries) {
            if (!e.include) continue
            val reason = validate(e, totalWeeks)
            if (reason != null) {
                invalid.add(InvalidEntry(e, reason))
                continue
            }
            val weeks = if (totalWeeks > 0) e.weeks.filter { it <= totalWeeks }.toSet() else e.weeks
            val lesson = PlannedLesson(
                dayOfWeek = e.dayOfWeek,
                startSlot = e.startSlot,
                endSlot = e.endSlot,
                weeks = weeks,
                location = e.location.trim(),
            )
            // 与已有课程逐条比对：该条目已被完全覆盖则跳过，不重复插入
            val duplicate = existing.any { ex ->
                ex.name == e.name.trim() && ex.teacher == e.teacher.trim() &&
                    ex.lessons.contains(lesson.fingerprint())
            }
            if (duplicate) skippedDuplicates++ else planned.add(e to lesson)
        }

        // 同名同教师合并为一门课（沿用既有口径）；组内完全相同的行再去一层重
        val courses = planned
            .groupBy { it.first.name.trim() to it.first.teacher.trim() }
            .map { (key, pairs) ->
                PlannedCourse(
                    name = key.first,
                    teacher = key.second,
                    lessons = pairs.map { it.second }.distinctBy { it.fingerprint() },
                )
            }
        return Plan(
            courses = courses,
            maxSlotNeeded = courses.flatMap { it.lessons }.maxOfOrNull { it.endSlot } ?: 0,
            skippedDuplicates = skippedDuplicates,
            invalid = invalid,
        )
    }

    /** 返回 null 表示合法（预览页也用它逐条标注问题）。周次全超界判无效，不再像旧逻辑那样原样保留（永不发生的死课） */
    @Suppress("ReturnCount")
    fun validate(e: ParsedEntry, totalWeeks: Int): String? {
        if (e.name.isBlank()) return REASON_NO_NAME
        if (e.dayOfWeek !in 1..7) return REASON_BAD_DAY
        if (e.startSlot < 1 || e.endSlot < e.startSlot) return REASON_BAD_SLOT
        if (e.weeks.isEmpty()) return REASON_WEEKS_EMPTY
        if (totalWeeks > 0 && e.weeks.none { it <= totalWeeks }) return REASON_WEEKS_OUT_OF_RANGE
        return null
    }

    /**
     * 「一键平移超界周次」（预览页对全部超界条目的自动修复）：把最大周对齐到学期总周数、
     * 整体平移并保持区间形状（如 17-20 周、16 周学期 → 13-16 周）。非超界条目原样返回；
     * 平移后恒非空（最大周必落在 totalWeeks 上），修复即合法。
     */
    fun shiftWeeksIntoRange(e: ParsedEntry, totalWeeks: Int): ParsedEntry {
        if (totalWeeks <= 0 || e.weeks.isEmpty() || e.weeks.any { it <= totalWeeks }) return e
        val shift = e.weeks.max() - totalWeeks
        return e.copy(weeks = e.weeks.map { it - shift }.filter { it >= 1 }.toSet())
    }

    /**
     * 预览阶段的条目间时间冲突检测（只比本次要导入的条目之间，不与库中已有课程比对）：
     * 同天 + 节次区间相交 + 周次集合相交即冲突。返回「条目下标 → 与之冲突的课程描述列表」，
     * 仅供预览页标注（不取消勾选、不拦导入），入库后编辑页仍有完整的冲突检测。
     */
    @Suppress("LoopWithTooManyJumpStatements")
    fun conflictsAmong(entries: List<ParsedEntry>, totalWeeks: Int): Map<Int, List<String>> {
        val result = mutableMapOf<Int, MutableList<String>>()
        val valid = entries.withIndex().filter { (_, e) -> validate(e, totalWeeks) == null && e.include }
        for (a in valid) {
            for (b in valid) {
                if (b.index <= a.index) continue
                if (a.value.dayOfWeek != b.value.dayOfWeek) continue
                if (a.value.startSlot > b.value.endSlot || b.value.startSlot > a.value.endSlot) continue
                val overlap = a.value.weeks intersect b.value.weeks
                if (overlap.isEmpty()) continue
                val weeksLabel = WeeksCodec.label(overlap, 0) ?: "全学期"
                result.getOrPut(a.index) { mutableListOf() }
                    .add("${b.value.name.ifBlank { "未命名课程" }}（$weeksLabel）")
                result.getOrPut(b.index) { mutableListOf() }
                    .add("${a.value.name.ifBlank { "未命名课程" }}（$weeksLabel）")
            }
        }
        return result
    }
}

/** 导入执行结果；三个数字都应出现在完成反馈里 */
data class ImportResult(
    val importedCourses: Int,
    val skippedDuplicates: Int,
    val invalid: List<ImportPlanner.InvalidEntry> = emptyList(),
)
