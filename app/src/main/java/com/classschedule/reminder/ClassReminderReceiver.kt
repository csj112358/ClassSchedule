package com.classschedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.classschedule.widget.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟接收器：到点后按需发上课提醒、刷新桌面小部件，并链式排下一个事件。
 * 另外负责每天凌晨的兜底重排。
 *
 * 注意：即使「上课提醒」是关闭状态，这条链条也会继续跑——小部件的「上课中 / 下一节」需要在
 * 节次边界刷新，不能因为没有提醒就停掉。
 */
class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_REMIND &&
            action != ACTION_DAILY_MAINTENANCE &&
            action != ACTION_WIDGET_TICK
        ) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    // 用户手动触发（或其它组件请求）：只要刷新，不动闹钟链条
                    ACTION_WIDGET_TICK -> WidgetUpdateWorker.refreshAll(appContext)
                    ACTION_REMIND -> ReminderScheduler.onAlarmFired(appContext)
                    else -> {
                        ReminderScheduler.reschedule(appContext)
                        WidgetUpdateWorker.refreshAll(appContext)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ClassReminder", "闹钟处理失败: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.classschedule.action.CLASS_REMIND"
        const val ACTION_DAILY_MAINTENANCE = "com.classschedule.action.REMINDER_DAILY_MAINTENANCE"

        /** 只刷新小部件（不发通知、不动闹钟链条），便于调试与外部触发 */
        const val ACTION_WIDGET_TICK = "com.classschedule.action.WIDGET_TICK"
    }
}
