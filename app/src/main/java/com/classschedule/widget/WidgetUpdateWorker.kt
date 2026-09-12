package com.classschedule.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 桌面小部件刷新。
 *
 * 刷新来源有三条，互相兜底：
 * 1. 系统按 `updatePeriodMillis`（1 小时）与 WorkManager 每小时各刷一次——兜底、不保证时刻；
 * 2. **节次边界精确闹钟**（[com.classschedule.reminder.ReminderScheduler]）——保证「上课中 / 下一节」
 *    在课程开始、结束的当口就翻过来，这是会不会显示错的关键；
 * 3. 课表 / 学期 / 课时配置变动、App 启动、开机——数据一变立刻刷。
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            refreshAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        /**
         * 刷新所有已添加的小部件实例。
         * 没有添加小部件时 `getGlanceIds` 返回空列表，这里直接什么都不做。
         */
        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(ScheduleWidget::class.java).forEach { id ->
                ScheduleWidget().update(context, id)
            }
        }
    }
}
