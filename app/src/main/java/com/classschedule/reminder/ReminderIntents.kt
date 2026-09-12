package com.classschedule.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.classschedule.MainActivity

/** 提醒相关的 PendingIntent 构造，统一放在这里避免各处 requestCode / flag 写岔。 */
object ReminderIntents {

    private const val REQUEST_OPEN_APP = 2001
    private const val REQUEST_ALARM = 2002

    /** 点击通知打开 App 到课表页 */
    fun openSchedule(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_SCHEDULE, true)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 到点后唤醒 ClassReminderReceiver 的闹钟 PendingIntent */
    fun alarm(context: Context): PendingIntent {
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ClassReminderReceiver.ACTION_REMIND
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
