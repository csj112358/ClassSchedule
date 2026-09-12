package com.classschedule.data.parser

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * ============================================================================
 *  课表 JSON 解析器（通用模板）
 * ============================================================================
 *
 * 本文件是通用模板：不内置任何具体学校 / 教务系统的课表格式。
 *
 * 你只需要做一件事 —— 把「解析入口」里的 `parse()` 改成读取你自己课表
 * JSON 的字段，并把结果交给通用的 `toOccurrences(...)`。
 *
 * 结构固定、无需改动的部分：
 *   - 数据契约：ParsedOccurrence / TimetableParseResult（见 ParsedOccurrence.kt）
 *   - 通用工具：周次解析、单双周、节次区间、时间换算（本文件下半部分，已完整实现）
 *
 * 三步适配：
 *   1. 在「原始 JSON 结构」处按你课表 JSON 定义数据类；
 *   2. 在 `parse()` 里遍历它们，取出「星期几 / 课程名 / 教师 / 教室 / 节次 / 周次」；
 *   3. 调用 `toOccurrences(...)` 得到课次列表，收进 TimetableParseResult 返回。
 *
 * 适配完成后，在导入页粘贴课表 JSON 即可看到预览；改好一次，同校同学都能直接用。
 */
object SemesterJsonParser {

    private val gson = Gson()

    // ========================================================================
    // 一、解析入口  ← 只改这一节
    // ========================================================================

    /**
     * 【原始 JSON 结构】—— 按你自己的课表 JSON 定义。
     *
     * TODO(1/3)：把下面的数据类换成你课表 JSON 的真实结构。
     *
     * 下面给出的字段命名是一个「可能的结构示例」，仅用于说明写法，
     * 并非任何学校的真实格式；你只需把字段名换成你 JSON 里的真实字段名：
     *
     *   {
     *     "data": {
     *       "days": [                       // 7 个元素，索引 0=周一
     *         {
     *           "dayOfWeek": 1,
     *           "blocks": [                 // 时间段数组
     *             {
     *               "startSection": 1,
     *               "endSection": 2,
     *               "startTime": "08:30",
     *               "endTime": "09:55",
     *               "courses": [            // 该时间段的课程
     *                 { "name": "高等数学", "teacher": "张三",
     *                   "room": "A101", "time": "1-16周[1-2节]" }
     *               ]
     *             }
     *           ]
     *         }
     *       ]
     *     }
     *   }
     */
    private data class RawTimetable(
        val state: Int? = null,
        val message: String? = null,
        val data: RawData? = null
    )

    private data class RawData(
        // TODO(1/3)：改成你 JSON 里承载「按天划分的课表」的那个字段
        val days: List<RawDay>? = null
    )

    private data class RawDay(
        // TODO(1/3)：改成你 JSON 里表示「星期几」的字段
        val dayOfWeek: Int? = null,
        // TODO(1/3)：改成你 JSON 里「时间段数组」的字段
        val blocks: List<RawBlock>? = null
    )

    private data class RawBlock(
        // TODO(1/3)：节次区间
        val startSection: Int? = null,
        val endSection: Int? = null,
        // TODO(1/3)：该段的起止时间（用于自动补齐课时配置）
        val startTime: String? = null,
        val endTime: String? = null,
        // TODO(1/3)：该时间段内的课程数组
        val courses: List<RawCourse>? = null
    )

    private data class RawCourse(
        val name: String? = null,
        val teacher: String? = null,
        val room: String? = null,
        // TODO(1/3)：周次与节次的原文，如 "1-16周[1-2节]"、"1-8,10-17周[单周]"
        val time: String? = null
    )

    /**
     * 完整解析整学期课表 JSON。
     *
     * TODO(2/3)：把「遍历 → 取值 → toOccurrences」这段改成你自己的字段。
     *
     * @throws IllegalArgumentException 无法识别为课表 JSON 时抛出（提示会展示给用户）
     */
    fun parse(rawText: String): TimetableParseResult {
        val timetable = parseRaw(rawText)
        val days = timetable?.data?.days
        if (days.isNullOrEmpty()) {
            throw IllegalArgumentException(
                "无法识别为课表 JSON：请先在 SemesterJsonParser.parse() 里适配你课表的字段结构"
            )
        }

        val occurrences = LinkedHashSet<ParsedOccurrence>()

        for ((index, day) in days.withIndex()) {
            // 优先用 JSON 给的星期字段；缺失时按数组顺序兜底（索引 0 = 周一）
            val weekday = day.dayOfWeek ?: (index + 1)
            if (weekday !in 1..7) continue

            for (block in day.blocks.orEmpty()) {
                val fallbackStart = block.startSection ?: 1
                val fallbackEnd = block.endSection ?: fallbackStart
                for (course in block.courses.orEmpty()) {
                    val name = course.name.orEmpty()
                    if (name.isBlank()) continue

                    val (sp, ep) = parsePeriods(course.time, fallbackStart, fallbackEnd)
                    if (sp <= 0 || ep < sp) continue

                    toOccurrences(
                        dayOfWeek = weekday,
                        name = name,
                        room = course.room.orEmpty(),
                        teacher = course.teacher.orEmpty(),
                        startPeriod = sp,
                        endPeriod = ep,
                        timeText = course.time
                    ).forEach { occurrences.add(it) }
                }
            }
        }

        if (occurrences.isEmpty()) {
            throw IllegalArgumentException(
                "解析完成但没有提取到任何课程，请检查 JSON 是否为完整课表，以及 parse() 中的字段是否对应"
            )
        }

        val sectionTimes = collectSectionTimes(days)
        val maxWeek = occurrences.maxOf { it.weekEnd }
        val distinctCount = occurrences.map { it.name }.distinct().size

        return TimetableParseResult(
            occurrences = occurrences.toList(),
            sectionTimes = sectionTimes,
            maxWeek = maxWeek,
            distinctCourseCount = distinctCount
        )
    }

    /**
     * 仅提取「节次 -> 真实时间」映射，用于自动补齐课时配置。
     *
     * TODO(3/3)：改成遍历你自己的时间段字段（让 `allBlocks` 返回你的时间段列表即可）。
     * 解析失败时返回空 Map，不影响课程导入。
     */
    fun extractSectionTimes(rawText: String): Map<Int, Pair<String, String>> {
        return try {
            val days = parseRaw(rawText)?.data?.days ?: return emptyMap()
            collectSectionTimes(days)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ========================================================================
    // 三、通用工具（已完整实现，通常无需改动）
    // ========================================================================

    /**
     * 把一条课次记录展开成「连续周次区间」的若干条 ParsedOccurrence。
     *
     * 已处理：多段周次（1-8,10-17周）、单双周（[单周]/[双周]）、
     * 中文/英文逗号、"第X-Y周" 等常见写法。
     *
     * @param timeText 周次原文，如 "1-8,10-17周[1-2节][单周]"
     */
    fun toOccurrences(
        dayOfWeek: Int,
        name: String,
        room: String,
        teacher: String,
        startPeriod: Int,
        endPeriod: Int,
        timeText: String?,
        fallbackWeekStart: Int? = null,
        fallbackWeekEnd: Int? = null,
        fallbackParity: Int = 0
    ): List<ParsedOccurrence> {
        val (weeks, parity) = interpretTime(timeText)
        val weekSet = if (weeks != null) weeks else {
            if (fallbackWeekStart != null && fallbackWeekEnd != null) {
                (fallbackWeekStart..fallbackWeekEnd).filter { w ->
                    fallbackParity == 0 || w % 2 == (if (fallbackParity == 1) 1 else 0)
                }.toSet()
            } else {
                emptySet()
            }
        }
        if (weekSet.isEmpty()) return emptyList()
        val usedParity = if (weeks != null) parity else fallbackParity

        return collapseWeeks(weekSet, usedParity).map { (ws, we) ->
            ParsedOccurrence(
                name = name,
                room = room,
                teacher = teacher,
                dayOfWeek = dayOfWeek,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                weekStart = ws,
                weekEnd = we,
                weekParity = usedParity
            )
        }
    }

    /**
     * 解析 Time 字段：返回 (上课周集合, 单双周标记)。无法识别周次时 weeks 为 null。
     */
    fun interpretTime(timeText: String?): Pair<Set<Int>?, Int> {
        if (timeText.isNullOrBlank()) return null to 0
        val text = timeText
        val parity = when {
            text.contains("单周") || text.contains("奇") -> 1
            text.contains("双周") || text.contains("偶") -> 2
            else -> 0
        }

        // 周次部分：形如 "1-8,10-17周"，兼容中英文逗号、顿号，兼容"第X-Y周"
        val weekPart = Regex("""第?((?:\d+(?:-\d+)?)(?:[，,、]\s*\d+(?:-\d+)?)*)\s*周""").find(text)
            ?.groupValues?.get(1)
        if (weekPart == null) return null to parity

        val weeks = mutableSetOf<Int>()
        for (seg in weekPart.split(Regex("""[，,、]"""))) {
            val m = Regex("""(\d+)(?:-\s*(\d+))?""").find(seg.trim()) ?: continue
            val a = m.groupValues[1].toIntOrNull() ?: continue
            val b = m.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: a
            for (w in a..b) {
                if (parity == 0 || w % 2 == (if (parity == 1) 1 else 0)) weeks.add(w)
            }
        }
        if (weeks.isEmpty()) return null to parity
        return weeks to parity
    }

    /**
     * 解析节次区间。
     * 优先从 Time 原文里取（如 "[3-4节]"），取不到则用时间段自带的起止节次兜底。
     */
    fun parsePeriods(timeText: String?, fallbackStart: Int, fallbackEnd: Int): Pair<Int, Int> {
        if (timeText != null) {
            val bracket = Regex("""[\[（(]\s*第?\s*(\d+)\s*[-~～]\s*(\d+)\s*节?[\]）)]""").find(timeText)
            if (bracket != null) {
                val a = bracket.groupValues[1].toIntOrNull() ?: fallbackStart
                val b = bracket.groupValues[2].toIntOrNull() ?: fallbackEnd
                return a to b
            }
        }
        return fallbackStart to fallbackEnd
    }

    /** 工具：把单双周整数转成可读文本 */
    fun parityText(parity: Int): String = when (parity) {
        1 -> "单周"
        2 -> "双周"
        else -> ""
    }

    // -------- 以下为内部实现 --------

    private fun parseRaw(rawText: String): RawTimetable? {
        return try {
            gson.fromJson(rawText.trim(), RawTimetable::class.java)
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    /**
     * 每个连续节次区间只给了整段的起止时间（如 3-5 节 = 10:10-12:20），
     * 这里把整段时间在其包含的各节之间等分，得到每节一个近似时间。
     */
    private fun collectSectionTimes(days: List<RawDay>): Map<Int, Pair<String, String>> {
        val result = LinkedHashMap<Int, Pair<String, String>>()
        for (block in allBlocks(days)) {
            val startSection = block.startSection ?: continue
            val endSection = block.endSection ?: continue
            val startTime = block.startTime ?: continue
            val endTime = block.endTime ?: continue
            val startMin = toMinutes(startTime) ?: continue
            val endMin = toMinutes(endTime) ?: continue
            if (endMin <= startMin) continue
            val count = endSection - startSection + 1
            if (count <= 0) continue

            val total = endMin - startMin
            val per = total / count
            val rem = total % count
            var cursor = startMin
            for (k in 0 until count) {
                val len = per + if (k < rem) 1 else 0
                val segStart = cursor
                cursor += len
                val segEnd = cursor
                result[startSection + k] = minutesToTime(segStart) to minutesToTime(segEnd)
            }
        }
        return result
    }

    /** 把按天划分的课表摊平成「时间段」列表 */
    private fun allBlocks(days: List<RawDay>): List<RawBlock> =
        days.flatMap { it.blocks.orEmpty() }

    private fun toMinutes(time: String): Int? {
        val parts = time.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun minutesToTime(total: Int): String {
        val h = (total / 60).coerceIn(0, 23)
        val m = total % 60
        return String.format(java.util.Locale.US, "%02d:%02d", h, m)
    }

    /**
     * 把上课周集合压缩为连续区间。
     * 单双周时相邻合法周差 2（如 1,3,5,7 合并为 1-7[单周]）；否则按连续整数合并。
     */
    private fun collapseWeeks(weeks: Set<Int>, parity: Int): List<Pair<Int, Int>> {
        val sorted = weeks.sorted()
        if (sorted.isEmpty()) return emptyList()
        val step = if (parity != 0) 2 else 1
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = sorted[0]
        var prev = sorted[0]
        for (i in 1 until sorted.size) {
            val cur = sorted[i]
            if (cur - prev == step) {
                prev = cur
            } else {
                ranges.add(start to prev)
                start = cur
                prev = cur
            }
        }
        ranges.add(start to prev)
        return ranges
    }
}
