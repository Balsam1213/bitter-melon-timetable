package com.balsam.timetable.data.model

/**
 * 周次集合的编解码与展示。存储格式为升序逗号分隔（"1,2,...,8,11,..."），
 * 业务里一律用 Set<Int>；解析容错，非法片段丢弃。
 */
object WeeksCodec {

    /** "1,2, 11" → {1,2,11}；空串/全非法 → 空集 */
    fun parse(raw: String?): Set<Int> =
        raw?.split(',', '，', ' ', '\t')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?.toSortedSet()
            ?: emptySet()

    /** {11,1,2} → "1,2,11"；空集 → "" */
    fun format(weeks: Set<Int>): String = weeks.filter { it > 0 }.sorted().joinToString(",")

    /** 旧数据「区间 + 每周/单周/双周」展开为集合 */
    fun fromRange(startWeek: Int, endWeek: Int, pattern: WeekPattern): Set<Int> {
        val lo = minOf(startWeek, endWeek).coerceAtLeast(1)
        val hi = maxOf(startWeek, endWeek).coerceAtLeast(lo)
        return when (pattern) {
            WeekPattern.EVERY -> (lo..hi).toSet()
            WeekPattern.ODD -> (lo..hi).filter { it % 2 == 1 }.toSet()
            WeekPattern.EVEN -> (lo..hi).filter { it % 2 == 0 }.toSet()
        }
    }

    /**
     * 集合若恰好是「区间 + 每周/单周/双周」形态则反推三元组，否则返回 null。
     * 用于 JSON 导出的旧字段兼容与「全学期」判断。
     */
    fun toRange(weeks: Set<Int>): Triple<Int, Int, WeekPattern>? {
        if (weeks.isEmpty()) return null
        val sorted = weeks.sorted()
        val lo = sorted.first()
        val hi = sorted.last()
        return when {
            sorted == (lo..hi).toList() -> Triple(lo, hi, WeekPattern.EVERY)
            sorted == (lo..hi).filter { it % 2 == 1 } -> Triple(lo, hi, WeekPattern.ODD)
            sorted == (lo..hi).filter { it % 2 == 0 } -> Triple(lo, hi, WeekPattern.EVEN)
            else -> null
        }
    }

    /** 游程分段：{1..8, 11..16} → [(1..8), (11..16)] */
    fun segments(weeks: Set<Int>): List<IntRange> {
        if (weeks.isEmpty()) return emptyList()
        val sorted = weeks.sorted()
        val out = mutableListOf<IntRange>()
        var start = sorted.first()
        var prev = start
        for (w in sorted.drop(1)) {
            if (w == prev + 1) {
                prev = w
            } else {
                out.add(start..prev)
                start = w
                prev = w
            }
        }
        out.add(start..prev)
        return out
    }

    private fun segmentsLabel(segs: List<IntRange>): String =
        segs.joinToString(",") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }

    /**
     * 展示标签。覆盖整个学期（1..totalWeeks）返回 null（与网格现有行为一致：全学期不标周次）；
     * 单双周整形态 → "3-17周 单周"；任意集合 → "1-8,11-16周"；单周数 → "3周"。
     */
    fun label(weeks: Set<Int>, totalWeeks: Int): String? {
        if (weeks.isEmpty()) return "不上课"
        if (totalWeeks > 0 && weeks == (1..totalWeeks).toSet()) return null
        val lo = weeks.min()
        val hi = weeks.max()
        val odd = weeks == (lo..hi).filter { it % 2 == 1 }.toSet()
        val even = weeks == (lo..hi).filter { it % 2 == 0 }.toSet()
        return when {
            odd && lo != hi -> "${lo}-${hi}周 单周"
            even && lo != hi -> "${lo}-${hi}周 双周"
            else -> "${segmentsLabel(segments(weeks))}周"
        }
    }
}
