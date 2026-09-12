package com.classschedule

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.classschedule.reminder.ClassReminderNotifier
import com.classschedule.reminder.ReminderScheduler
import com.classschedule.widget.WidgetUpdateWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class ClassScheduleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleWidgetUpdate()

        // 通知渠道尽早建好；提醒与小部件节次刷新的闹钟链条在每次启动时重排一次，
        // 避免闹钟被系统清掉后无人补排
        ClassReminderNotifier.ensureChannel(this)
        ReminderScheduler.scheduleDailyMaintenance(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.reschedule(this@ClassScheduleApp)
            } catch (e: Exception) {
                android.util.Log.e("ClassReminder", "启动时排程失败: ${e.message}", e)
            }
        }
    }

    /** 定期刷新桌面小部件（每小时兜底一次；精确时刻由节次边界闹钟保证） */
    private fun scheduleWidgetUpdate() {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "schedule_widget_update",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
