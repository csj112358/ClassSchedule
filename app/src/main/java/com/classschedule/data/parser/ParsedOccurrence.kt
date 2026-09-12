package com.classschedule.data.parser

/**
 * 单条可落库的课次（已展开到连续周次区间 + 单双周标记）。
 *
 * @param weekParity 0=每周, 1=仅单周, 2=仅双周
 */
data class ParsedOccurrence(
    val name: String,
    val room: String,
    val teacher: String,
    val dayOfWeek: Int,        // 1=周一 … 7=周日
    val startPeriod: Int,      // 开始节次
    val endPeriod: Int,        // 结束节次
    val weekStart: Int,
    val weekEnd: Int,
    val weekParity: Int
)

/** 解析一份课表 JSON 的最终结果 */
data class TimetableParseResult(
    val occurrences: List<ParsedOccurrence>,
    val sectionTimes: Map<Int, Pair<String, String>>, // 节次 -> (开始时间, 结束时间)
    val maxWeek: Int,
    val distinctCourseCount: Int
)
