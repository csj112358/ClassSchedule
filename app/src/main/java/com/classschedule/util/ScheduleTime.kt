package com.classschedule.util

import com.classschedule.data.model.Course
import com.classschedule.data.model.PeriodConfig
import com.classschedule.data.model.Semester
import java.util.Calendar

/**
 * 时间/周次计算的公共逻辑。
 *
 * 桌面小部件和上课提醒都不在 Compose 环境里，需要一套独立于 ViewModel 的纯函数实现；
 * 这里集中放，避免各处再写一遍「今天是第几周、第几节」这类代码。
 */
object ScheduleTime {

    const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** 星期简称，索引 1~7（1=周一） */
    private val DAY_NAMES = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun dayName(dayOfWeek: Int): String =
        if (dayOfWeek in 1..7) DAY_NAMES[dayOfWeek] else ""

    /** 今天星期几（1=周一 … 7=周日） */
    fun todayDayOfWeek(now: Long = System.currentTimeMillis()): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    }

    /** 当天的 00:00:00.000 时间戳 */
    fun startOfDay(now: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** 在 now 的基础上加 days 天，保留时分秒 */
    fun plusDays(now: Long, days: Int): Long = now + days * DAY_MILLIS

    /**
     * 当前是第几周。
     * 未设置学期起止日期（startDate <= 0）时返回 1，与 App 内行为一致。
     */
    fun currentWeek(semester: Semester?, now: Long = System.currentTimeMillis()): Int {
        if (semester == null || semester.startDate <= 0) return 1
        val weeks = ((now - semester.startDate) / (7 * DAY_MILLIS)).toInt() + 1
        return weeks.coerceIn(1, maxWeek(semester))
    }

    /** 学期总周数（用于翻周上限），未设置日期时给一个宽松上限 */
    fun maxWeek(semester: Semester?): Int {
        if (semester == null || semester.startDate <= 0) return 30
        val weeks = ((semester.endDate - semester.startDate) / (7 * DAY_MILLIS)).toInt() + 1
        return weeks.coerceIn(1, 60)
    }

    /** 该课次在第 week 周是否会上课（含单双周） */
    fun occursInWeek(course: Course, week: Int): Boolean {
        if (week < course.weekStart || week > course.weekEnd) return false
        return when (course.weekParity) {
            1 -> week % 2 == 1
            2 -> week % 2 == 0
            else -> true
        }
    }

    /** 在课次列表里筛出第 week 周会上课的课次 */
    fun coursesInWeek(courses: List<Course>, week: Int): List<Course> =
        courses.filter { occursInWeek(it, week) }

    /** "HH:mm" -> 当天的分钟数；解析失败返回 null */
    fun parseMinutes(hhmm: String?): Int? {
        if (hhmm.isNullOrBlank()) return null
        val parts = hhmm.trim().split(":")
        if (parts.size < 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** 某节次当天的开始分钟数；该节次没有配置时间时返回 null */
    fun periodStartMinutes(periodConfigs: List<PeriodConfig>, period: Int): Int? =
        parseMinutes(periodConfigs.find { it.period == period }?.startTime)

    /** 某节次当天的结束分钟数；缺失时退回同一配置的开始时间 */
    fun periodEndMinutes(periodConfigs: List<PeriodConfig>, period: Int): Int? {
        val config = periodConfigs.find { it.period == period } ?: return null
        return parseMinutes(config.endTime) ?: parseMinutes(config.startTime)
    }

    /** 现在距离当天 00:00 的分钟数 */
    fun minutesOfDay(now: Long = System.currentTimeMillis()): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    /** 分钟数 -> "HH:mm" */
    fun formatMinutes(minutes: Int): String =
        String.format(java.util.Locale.US, "%02d:%02d", (minutes / 60) % 24, minutes % 60)

    /** 第 startPeriod~endPeriod 节的 "08:00-09:40" 时间串；取不到时返回空串 */
    fun timeRangeText(periodConfigs: List<PeriodConfig>, startPeriod: Int, endPeriod: Int): String {
        val start = periodConfigs.find { it.period == startPeriod }?.startTime ?: return ""
        val end = periodConfigs.find { it.period == endPeriod }?.endTime ?: return ""
        return "$start-$end"
    }

    /** 该时间戳所在周的周一 00:00 时间戳 */
    fun mondayOfWeek(now: Long): Long {
        val dayOfWeek = todayDayOfWeek(now)
        return startOfDay(now) - (dayOfWeek - 1) * DAY_MILLIS
    }

    /**
     * 计算某一天某节次上课的绝对时间（毫秒）。
     * @param dayStart 当天 00:00 的时间戳
     * @return 该节次开始时刻；节次没有配置时间时返回 null
     */
    fun courseStartMillis(dayStart: Long, course: Course, periodConfigs: List<PeriodConfig>): Long? {
        val minutes = periodStartMinutes(periodConfigs, course.startPeriod) ?: return null
        return dayStart + minutes * 60_000L
    }
}
