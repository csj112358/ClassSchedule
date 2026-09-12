package com.classschedule.data.parser

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * 单条可落库的课次（已展开到连续周次区间 + 单双周标记）。
 * weekParity: 0=每周, 1=仅单周, 2=仅双周
 */
data class ParsedOccurrence(
    val name: String,
    val room: String,
    val teacher: String,
    val dayOfWeek: Int,        // 1-7
    val startPeriod: Int,      // 开始节次
    val endPeriod: Int,        // 结束节次
    val weekStart: Int,
    val weekEnd: Int,
    val weekParity: Int
)

/**
 * 解析一份教务整学期课表 JSON 的最终结果
 */
data class TimetableParseResult(
    val occurrences: List<ParsedOccurrence>,
    val sectionTimes: Map<Int, Pair<String, String>>, // 节次 -> (开始时间, 结束时间)
    val maxWeek: Int,
    val distinctCourseCount: Int
)

/**
 * 教务系统整学期课表 JSON 解析器。
 *
 * 结构：data.AdjustDays[7]（周一~周日）-> 各 AM/PM/EV__TimePieces ->
 * 每个 TimePiece(连续节次区间 + 起止时间) -> Dtos 课程 ->
 * Content[{Key: Lesson/Teacher/Room/Time, Name:...}]。
 *
 * 该结构是固定可枚举的，因此用本地代码解析（不依赖 AI），解析规则见项目内对齐文档。
 */
object SemesterJsonParser {

    private val gson = Gson()

    /** 默认学期周数上界（时间字符串里没给周次时的兜底） */
    private const val DEFAULT_MAX_WEEK = 25

    // ===== 教务原始 JSON 结构 =====

    private data class RawSchedule(
        val state: Int? = null,
        val message: String? = null,
        val data: RawData? = null
    )

    private data class RawData(
        val AdjustDays: List<RawDay>? = null
    )

    private data class RawDay(
        val WIndex: Int? = null,
        val FullTitle: String? = null,
        val MN__TimePieces: List<RawTimePiece>? = null,
        val AM__TimePieces: List<RawTimePiece>? = null,
        val AF__TimePieces: List<RawTimePiece>? = null,
        val PM__TimePieces: List<RawTimePiece>? = null,
        val EV__TimePieces: List<RawTimePiece>? = null
    )

    private data class RawTimePiece(
        val Dtos: List<RawDto>? = null,
        val StartTime: String? = null,
        val EndTime: String? = null,
        val Title: String? = null,
        val StartSection: Int? = null,
        val EndSection: Int? = null,
        val Section: String? = null,
        val IsTimeConflit: Boolean? = null
    )

    private data class RawDto(
        val Content: List<RawContentItem>? = null
    )

    private data class RawContentItem(
        val Key: String? = null,
        val Name: String? = null
    )

    // ===== 公开入口 =====

    /**
     * 完整解析整学期课表 JSON。
     * @throws IllegalArgumentException 无法识别为教务课表 JSON 时抛出
     */
    fun parse(rawText: String): TimetableParseResult {
        val schedule = parseRaw(rawText)
        val days = schedule?.data?.AdjustDays
        if (days.isNullOrEmpty()) {
            throw IllegalArgumentException("无法识别为教务课表JSON（缺少 data.AdjustDays）")
        }

        val dayKeys = listOf(
            "MN__TimePieces", "AM__TimePieces", "AF__TimePieces", "PM__TimePieces", "EV__TimePieces"
        )

        val occurrences = LinkedHashSet<ParsedOccurrence>()

        for ((index, day) in days.withIndex()) {
            // 优先用 WIndex；缺失时按 AdjustDays 顺序兜底（索引0=周一）
            val weekday = day.WIndex ?: (index + 1)
            if (weekday !in 1..7) continue
            val pieces = dayKeys.flatMap { key ->
                when (key) {
                    "MN__TimePieces" -> day.MN__TimePieces.orEmpty()
                    "AM__TimePieces" -> day.AM__TimePieces.orEmpty()
                    "AF__TimePieces" -> day.AF__TimePieces.orEmpty()
                    "PM__TimePieces" -> day.PM__TimePieces.orEmpty()
                    else -> day.EV__TimePieces.orEmpty()
                }
            }
            for (piece in pieces) {
                val fallbackStart = piece.StartSection ?: 1
                val fallbackEnd = piece.EndSection ?: fallbackStart
                for (dto in piece.Dtos.orEmpty()) {
                    val content = dto.Content.orEmpty()
                    val contentMap = content.associate { (it.Key ?: "") to (it.Name ?: "") }
                    val name = contentMap["Lesson"] ?: ""
                    if (name.isBlank()) continue
                    val room = contentMap["Room"] ?: ""
                    val teacher = contentMap["Teacher"] ?: ""
                    val timeStr = contentMap["Time"]

                    val (sp, ep) = parsePeriods(timeStr, fallbackStart, fallbackEnd)
                    if (sp <= 0 || ep < sp) continue

                    toOccurrences(
                        dayOfWeek = weekday,
                        name = name,
                        room = room,
                        teacher = teacher,
                        startPeriod = sp,
                        endPeriod = ep,
                        timeText = timeStr
                    ).forEach { occurrences.add(it) }
                }
            }
        }
        if (occurrences.isEmpty()) {
            throw IllegalArgumentException("解析完成但没有提取到任何课程，请检查内容是否为完整课表JSON")
        }

        val sectionTimes = collectSectionTimes(days, dayKeys)
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
     * 仅提取“节次 -> 真实时间”映射，用于自动补齐课时配置。
     * 解析失败时返回空 Map，不影响课程导入。
     */
    fun extractSectionTimes(rawText: String): Map<Int, Pair<String, String>> {
        return try {
            val schedule = parseRaw(rawText)
            val days = schedule?.data?.AdjustDays ?: return emptyMap()
            collectSectionTimes(days, listOf(
                "MN__TimePieces", "AM__TimePieces", "AF__TimePieces", "PM__TimePieces", "EV__TimePieces"
            ))
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ===== 内部实现 =====

    private fun parseRaw(rawText: String): RawSchedule? {
        return try {
            gson.fromJson(rawText.trim(), RawSchedule::class.java)
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    private fun allPieces(days: List<RawDay>, dayKeys: List<String>): List<RawTimePiece> {
        return days.flatMap { day ->
            dayKeys.flatMap { key ->
                when (key) {
                    "MN__TimePieces" -> day.MN__TimePieces.orEmpty()
                    "AM__TimePieces" -> day.AM__TimePieces.orEmpty()
                    "AF__TimePieces" -> day.AF__TimePieces.orEmpty()
                    "PM__TimePieces" -> day.PM__TimePieces.orEmpty()
                    else -> day.EV__TimePieces.orEmpty()
                }
            }
        }
    }

    /**
     * 每个连续节次区间 TimePiece 的 StartTime/EndTime 只覆盖整段（如 3-5节 = 10:10-12:20），
     * 这里把整段时间在其包含的各节之间等分，得到每节一个近似时间。
     */
    private fun collectSectionTimes(days: List<RawDay>, dayKeys: List<String>): Map<Int, Pair<String, String>> {
        val result = LinkedHashMap<Int, Pair<String, String>>()
        for (piece in allPieces(days, dayKeys)) {
            val startSection = piece.StartSection ?: continue
            val endSection = piece.EndSection ?: continue
            val startTime = piece.StartTime ?: continue
            val endTime = piece.EndTime ?: continue
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
                val section = startSection + k
                result[section] = minutesToTime(segStart) to minutesToTime(segEnd)
            }
        }
        return result
    }

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
     * 把一条课次记录展开成“连续周次区间”的若干条 ParsedOccurrence。
     * @param timeText Time 字段原文，如 "1-8,10-17周[1-2节][单周]"
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

    private fun parsePeriods(timeText: String?, fallbackStart: Int, fallbackEnd: Int): Pair<Int, Int> {
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

    /**
     * 解析 Time 字段：返回 (上课周集合, 单双周标记)。无法识别周次时 weeks 为 null。
     */
    private fun interpretTime(timeText: String?): Pair<Set<Int>?, Int> {
        if (timeText.isNullOrBlank()) return null to 0
        val text = timeText
        val parity = when {
            text.contains("单周") -> 1
            text.contains("双周") -> 2
            else -> 0
        }

        // 周次部分：形如 "1-8,10-17周"，兼容中文/英文逗号，兼容"第X-Y周"
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

    /** 工具：供 UI 预览等地方把单双周整数转成可读文本 */
    fun parityText(parity: Int): String = when (parity) {
        1 -> "单周"
        2 -> "双周"
        else -> ""
    }
}
