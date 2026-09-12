package com.classschedule.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.classschedule.R
import com.classschedule.data.model.Course
import com.classschedule.data.model.PeriodConfig
import com.classschedule.util.ScheduleTime

/**
 * 上课提醒的通知渠道与通知内容。
 */
object ClassReminderNotifier {

    const val CHANNEL_ID = "class_reminder"
    private const val NOTIFICATION_ID = 1001

    /** 创建通知渠道（重复调用安全，只在 O+ 需要） */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "上课提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "每节课开始前按设定的提前时间提醒"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** 通知权限是否已授予（33 以下默认有权限） */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * 弹出提醒。
     * @param leadMinutes 设定提前了几分钟（用于文案）；提前时间被上一节课挤掉时传 null，改为“即将上课”
     */
    fun show(
        context: Context,
        course: Course,
        periodConfigs: List<PeriodConfig>,
        startAt: Long,
        leadMinutes: Int?
    ) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)

        val startMinutes = ScheduleTime.periodStartMinutes(periodConfigs, course.startPeriod)
        val endMinutes = ScheduleTime.periodEndMinutes(periodConfigs, course.endPeriod)
        val timeRange = if (startMinutes != null && endMinutes != null) {
            "${ScheduleTime.formatMinutes(startMinutes)}-${ScheduleTime.formatMinutes(endMinutes)}"
        } else {
            ""
        }

        val remaining = ((startAt - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0)
        val whenText = when {
            leadMinutes == null -> "即将上课"
            remaining in 1..(leadMinutes + 1) -> "$remaining 分钟后上课"
            else -> "${leadMinutes} 分钟后上课"
        }

        val place = listOf(course.room, course.teacher)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        val text = listOf(whenText, timeRange, place)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        val contentIntent = ReminderIntents.openSchedule(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(course.name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // 权限在弹出瞬间被撤销，忽略即可
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
