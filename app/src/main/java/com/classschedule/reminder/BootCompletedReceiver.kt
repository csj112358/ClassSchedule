package com.classschedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机 / 应用被替换后重建提醒链条。
 * AlarmManager 的闹钟不会跨重启保留，必须在这里补排。
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ClassReminderNotifier.ensureChannel(appContext)
                ReminderScheduler.scheduleDailyMaintenance(appContext)
                ReminderScheduler.reschedule(appContext)
            } catch (e: Exception) {
                android.util.Log.e("ClassReminder", "开机重排失败: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
