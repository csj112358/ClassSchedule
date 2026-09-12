package com.classschedule.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import com.classschedule.data.db.AppDatabase
import com.classschedule.data.model.Course
import com.classschedule.data.model.PeriodConfig
import com.classschedule.data.model.Semester
import com.classschedule.data.prefs.SettingsStore
import com.classschedule.util.ScheduleTime
import com.classschedule.widget.WidgetUpdateWorker
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * 一次待提醒的上课事件
 *
 * @param startAt 上课时刻
 * @param notifyAt 该提醒的触发时刻（= 上课时刻 - 提前分钟，被上一节课占用时会顺延到下课）
 * @param courseId 课程 id，用于在闹钟触发时把课重新查出来
 * @param isFirstOfDay 当天第一节（第一节课前面不会撞上别的课）
 */
data class ReminderOccurrence(
    val course: Course,
    val startAt: Long,
    val notifyAt: Long,
    val isFirstOfDay: Boolean
) {
    val courseId: Long get() = course.id
}

/**
 * 下一个到点要做的事。
 *
 * 提醒和"刷新桌面小部件"共用一条 AlarmManager 链条：小部件的「上课中 / 下一节」是在节次边界上
 * 翻转的，只靠 1 小时的周期刷新最多会错 59 分钟，所以关键信息单独排精确闹钟。
 */
sealed interface ReminderEvent {

    /** 触发时刻 */
    val notifyAt: Long

    /** 该发一条上课提醒通知 */
    data class Reminder(
        val occurrence: ReminderOccurrence,
        override val notifyAt: Long
    ) : ReminderEvent

    /** 只刷新桌面小部件，不发通知（节次开始/结束，或跨天/跨周） */
    data class WidgetRefresh(override val notifyAt: Long) : ReminderEvent
}

/**
 * 上课提醒 + 小部件精确刷新调度。
 *
 * 只给「下一件要做的事」排一个闹钟，触发后立刻排下一件（链式调度），
 * 避免给整学期几百节课各排一个闹钟。课表变动、开机、每天凌晨都会重新排一次。
 */
object ReminderScheduler {

    private const val TAG = "ClassReminder"
    private const val SCAN_DAYS = 35

    /** 闹钟被系统推迟超过这个时间就不再补发通知，避免"上课了才收到提醒" */
    private const val NOTIFY_LATE_TOLERANCE_MS = 2 * 60_000L

    /**
     * 重排下一个闹钟。
     *
     * 提醒关闭时不再取消闹钟——小部件仍然需要在节次边界刷新；
     * 只有「今天往后没有任何课」时才会清掉残留闹钟。
     */
    suspend fun reschedule(context: Context) {
        val appContext = context.applicationContext
        val event = nextEvent(appContext)
        if (event == null) {
            Log.d(TAG, "没有下一个事件，取消闹钟 | ${describeSchedule(appContext)}")
            cancel(appContext)
            return
        }
        Log.d(TAG, "下一个事件: ${event.javaClass.simpleName} @ ${java.util.Date(event.notifyAt)}")
        scheduleAlarm(appContext, event.notifyAt)
    }

    /** 排程诊断：说清楚"为什么排不出事件"（学期日期没设？没课？还是都上完了） */
    private suspend fun describeSchedule(context: Context): String {
        return try {
            val database = AppDatabase.getDatabase(context)
            val semester = database.semesterDao().getActiveSemester().first()
                ?: return "没有当前学期"
            val courses = database.courseDao().getCoursesBySemesterList(semester.id)
            val periods = database.periodConfigDao().getAllPeriodConfigsList()
            val today = ScheduleTime.todayDayOfWeek()
            val week = ScheduleTime.currentWeek(semester)
            val todayCount = coursesOnDay(semester, courses, ScheduleTime.startOfDay()).size
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            fun dateText(value: Long): String =
                if (value <= 0) "未设置" else fmt.format(java.util.Date(value))
            "学期=${semester.name} 起=${dateText(semester.startDate)} " +
                "止=${dateText(semester.endDate)} 第${week}周 今天周${today} " +
                "今日课=$todayCount 学期课=${courses.size} 节次配置=${periods.size}"
        } catch (e: Exception) {
            "诊断失败: ${e.message}"
        }
    }

    /** 取消闹钟（提醒与刷新共用同一个 PendingIntent） */
    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(ReminderIntents.alarm(appContext))
    }

    /**
     * 找出下一件要做的事：提醒（若开启）与节次边界刷新里更早的那个。
     * 找不到（没学期、没课、学期已结束）返回 null。
     */
    suspend fun nextEvent(context: Context): ReminderEvent? {
        val now = System.currentTimeMillis()
        val reminder = try {
            nextReminder(context)
        } catch (e: Exception) {
            Log.e(TAG, "计算下一次提醒失败", e)
            null
        }
        val refresh = try {
            nextWidgetRefresh(context, now)
        } catch (e: Exception) {
            Log.e(TAG, "计算下一次小部件刷新失败", e)
            null
        }
        // notifyAt 已过去超过容忍时间的候选直接丢弃（闹钟被系统大幅推迟的情况）
        return listOfNotNull(reminder, refresh)
            .filter { it.notifyAt > now - NOTIFY_LATE_TOLERANCE_MS }
            .minByOrNull { it.notifyAt }
    }

    // ===== 上课提醒 =====

    /**
     * 下一次该提醒的上课事件；提醒未开启或后面没课返回 null。
     * notifyAt 允许落在过去，用于「刚打开提醒开关时给即将开始的课补一条」。
     */
    private suspend fun nextReminder(context: Context): ReminderEvent.Reminder? {
        val settings = SettingsStore.get(context)
        if (!settings.isReminderEnabled()) return null
        val leadMinutes = settings.currentLeadMinutes()
        val occurrence = nextOccurrence(context, leadMinutes) ?: return null
        return ReminderEvent.Reminder(occurrence = occurrence, notifyAt = occurrence.notifyAt)
    }

    suspend fun nextOccurrence(context: Context, leadMinutes: Int): ReminderOccurrence? {
        val database = AppDatabase.getDatabase(context)
        val semester = database.semesterDao().getActiveSemester().first() ?: return null
        if (semester.startDate <= 0) return null // 没有真实日期就算不出上课时刻
        val periodConfigs = database.periodConfigDao().getAllPeriodConfigsList()
        if (periodConfigs.isEmpty()) return null
        val courses = database.courseDao().getCoursesBySemesterList(semester.id)
        if (courses.isEmpty()) return null

        val now = System.currentTimeMillis()
        val todayStart = ScheduleTime.startOfDay(now)

        for (dayOffset in 0 until SCAN_DAYS) {
            val dayStart = todayStart + dayOffset * ScheduleTime.DAY_MILLIS
            val occurrence = firstReminderOnDay(
                dayStart = dayStart,
                semester = semester,
                courses = courses,
                periodConfigs = periodConfigs,
                leadMinutes = leadMinutes,
                now = now
            )
            if (occurrence != null) return occurrence
        }
        return null
    }

    /**
     * 计算某一天第一条「还来得及提醒」的上课事件。
     * 同一天内按节次顺序处理，遇到已经过去的课跳过。
     */
    private fun firstReminderOnDay(
        dayStart: Long,
        semester: Semester,
        courses: List<Course>,
        periodConfigs: List<PeriodConfig>,
        leadMinutes: Int,
        now: Long
    ): ReminderOccurrence? {
        val dayCourses = coursesOnDay(semester, courses, dayStart)

        for ((index, course) in dayCourses.withIndex()) {
            val startMinutes = ScheduleTime.periodStartMinutes(periodConfigs, course.startPeriod)
                ?: continue
            val startAt = dayStart + startMinutes * 60_000L
            val endAt = ScheduleTime.periodEndMinutes(periodConfigs, course.endPeriod)
                ?.let { dayStart + it * 60_000L }

            // 已经下课（或正在上课）的不再提醒
            if (endAt != null && endAt <= now) continue
            if (startAt <= now) continue

            // 提前时间落到上一节课里时，顺延到上一节课下课立刻提醒
            var notifyAt = startAt - leadMinutes * 60_000L
            val previousEndAt = dayCourses.take(index)
                .mapNotNull { previous ->
                    ScheduleTime.periodEndMinutes(periodConfigs, previous.endPeriod)
                        ?.let { dayStart + it * 60_000L }
                }
                .maxOrNull()
            if (previousEndAt != null && notifyAt < previousEndAt) {
                notifyAt = previousEndAt
            }
            // 顺延后仍然来不及（上一节课还没下就要上课），直接跳过这条
            if (notifyAt >= startAt) continue

            return ReminderOccurrence(
                course = course,
                startAt = startAt,
                notifyAt = notifyAt,
                isFirstOfDay = index == 0
            )
        }
        return null
    }

    // ===== 小部件精确刷新 =====

    /**
     * 下一次节次边界（某节课结束，或下一节课开始）。
     *
     * 小部件的「上课中 / 下一节」只在这些时刻翻转；
     * 正在上课时要排到这节课结束，而不是这节课开始。
     */
    private suspend fun nextWidgetRefresh(context: Context, now: Long): ReminderEvent.WidgetRefresh? {
        val database = AppDatabase.getDatabase(context)
        val semester = database.semesterDao().getActiveSemester().first() ?: return null
        if (semester.startDate <= 0) return null
        val periodConfigs = database.periodConfigDao().getAllPeriodConfigsList()
        if (periodConfigs.isEmpty()) return null
        val courses = database.courseDao().getCoursesBySemesterList(semester.id)
        if (courses.isEmpty()) return null

        val todayStart = ScheduleTime.startOfDay(now)
        for (dayOffset in 0 until SCAN_DAYS) {
            val dayStart = todayStart + dayOffset * ScheduleTime.DAY_MILLIS
            val dayCourses = coursesOnDay(semester, courses, dayStart)
            if (dayCourses.isEmpty()) continue

            val boundaries = buildList {
                dayCourses.forEach { course ->
                    ScheduleTime.periodEndMinutes(periodConfigs, course.endPeriod)
                        ?.let { add(dayStart + it * 60_000L) }
                }
                // 明天以来的第一节：跨天时也要把标题里的"星期/周次"刷新掉
                if (dayOffset > 0) {
                    dayCourses.minOfOrNull { it.startPeriod }
                        ?.let { ScheduleTime.periodStartMinutes(periodConfigs, it) }
                        ?.let { add(dayStart + it * 60_000L) }
                }
            }
            val next = boundaries.filter { it > now }.minOrNull() ?: continue
            return ReminderEvent.WidgetRefresh(next)
        }
        return null
    }

    /** 某一天会上课的课程，按节次排序 */
    private fun coursesOnDay(
        semester: Semester,
        courses: List<Course>,
        dayStart: Long
    ): List<Course> {
        val week = ScheduleTime.currentWeek(semester, dayStart)
        val dayOfWeek = ScheduleTime.todayDayOfWeek(dayStart)
        return ScheduleTime.coursesInWeek(courses, week)
            .filter { it.dayOfWeek == dayOfWeek }
            .sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }))
    }

    // ===== 闹钟本体 =====

    /** 排下一个闹钟；精确闹钟不可用时自动降级为非精确 */
    private fun scheduleAlarm(context: Context, triggerAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = ReminderIntents.alarm(context)
        val at = triggerAt.coerceAtLeast(System.currentTimeMillis() + 1_000L)

        val scheduled = try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            true
        } catch (e: SecurityException) {
            // Android 12+ 未授予「闹钟和提醒」权限
            false
        }
        if (!scheduled) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
        }
        Log.d(TAG, "已排下一个事件: ${java.util.Date(at)} 内容=@$triggerAt (精确=$scheduled)")
    }

    /**
     * 闹钟到点：重新确认这件事是否仍然有效（期间可能改了提前时间/课表/开关），
     * 该发通知就发，然后无论如何都刷新一次小部件，并继续链式排下一件。
     */
    suspend fun onAlarmFired(context: Context) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val event = nextEvent(appContext)

        if (event != null && event.notifyAt <= now + 60_000L) {
            when (event) {
                is ReminderEvent.Reminder -> showReminderIfDue(appContext, event.occurrence, now)
                is ReminderEvent.WidgetRefresh -> Unit // 刷新由下面统一处理
            }
        }

        // 到点就刷新小部件：正常情况是"这一节课刚开始/刚结束"，提前量已由 nextWidgetRefresh 保证
        WidgetUpdateWorker.refreshAll(appContext)

        // 继续链式排下一件
        reschedule(appContext)
    }

    /** 只在还没到上课时间之前提醒；闹钟被系统推迟过久时不补发 */
    private suspend fun showReminderIfDue(
        context: Context,
        occurrence: ReminderOccurrence,
        now: Long
    ) {
        if (occurrence.startAt <= now) return
        if (occurrence.notifyAt < now - NOTIFY_LATE_TOLERANCE_MS) return

        val settings = SettingsStore.get(context)
        val leadMinutes = settings.currentLeadMinutes()
        val periodConfigs = AppDatabase.getDatabase(context).periodConfigDao().getAllPeriodConfigsList()
        // 提前时间被上一节课顺延过时，文案不再声称"提前 N 分钟"
        val expectedNotifyAt = occurrence.startAt - leadMinutes * 60_000L
        val effectiveLead = if (occurrence.notifyAt > expectedNotifyAt) null else leadMinutes
        ClassReminderNotifier.show(
            context = context,
            course = occurrence.course,
            periodConfigs = periodConfigs,
            startAt = occurrence.startAt,
            leadMinutes = effectiveLead
        )
    }

    /** 每日本地 0 点后重排一次，兜底系统清理 / 长期未打开 App 导致的链条断裂 */
    fun scheduleDailyMaintenance(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = maintenanceIntent(context)
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun maintenanceIntent(context: Context): PendingIntent {
        val intent = android.content.Intent(context, ClassReminderReceiver::class.java).apply {
            action = ClassReminderReceiver.ACTION_DAILY_MAINTENANCE
        }
        return PendingIntent.getBroadcast(
            context,
            2003,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 当前是否需要引导用户去授权「闹钟和提醒」（Android 12+，且已开启提醒功能） */
    fun needsExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return !alarmManager.canScheduleExactAlarms()
    }
}
