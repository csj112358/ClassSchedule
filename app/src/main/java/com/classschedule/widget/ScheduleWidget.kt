package com.classschedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.classschedule.data.db.AppDatabase
import com.classschedule.data.model.Course
import com.classschedule.data.model.PeriodConfig
import com.classschedule.data.model.Semester
import kotlinx.coroutines.flow.first

/**
 * 课程表桌面小部件
 */
class ScheduleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)

        // 获取当前活跃学期
        val semester = database.semesterDao().getActiveSemester().first()

        // 获取当前周次
        val currentWeek = if (semester != null) {
            val now = System.currentTimeMillis()
            val diff = now - semester.startDate
            val weeks = (diff / (7 * 24 * 60 * 60 * 1000L)).toInt() + 1
            val maxWeek = ((semester.endDate - semester.startDate) / (7 * 24 * 60 * 60 * 1000L)).toInt() + 1
            weeks.coerceIn(1, maxWeek)
        } else {
            1
        }

        // 获取本周课程
        val courses = if (semester != null) {
            database.courseDao().getCoursesByWeek(semester.id, currentWeek).first()
        } else {
            emptyList()
        }

        // 获取课时配置
        val periodConfigs = database.periodConfigDao().getAllPeriodConfigs().first()
            .sortedBy { it.period }

        // 获取当前是星期几（1=周一, 7=周日）
        val calendar = java.util.Calendar.getInstance()
        val todayDayOfWeek = (calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 + 1

        provideContent {
            GlanceTheme {
                WidgetContent(
                    semester = semester,
                    currentWeek = currentWeek,
                    todayDayOfWeek = todayDayOfWeek,
                    courses = courses,
                    periodConfigs = periodConfigs
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        semester: Semester?,
        currentWeek: Int,
        todayDayOfWeek: Int,
        courses: List<Course>,
        periodConfigs: List<PeriodConfig>
    ) {
        val todayCourses = courses.filter { it.dayOfWeek == todayDayOfWeek }
            .sortedBy { it.startPeriod }

        val dayName = when (todayDayOfWeek) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            7 -> "周日"
            else -> ""
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(ColorProvider(Color(0xFF1A1730)))
                .padding(12.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = semester?.name ?: "课程表",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = "$dayName · 第${currentWeek}周",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF818CF8)),
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                if (todayCourses.isEmpty()) {
                    // 今天没有课
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(12.dp)
                            .background(ColorProvider(Color(0x30FFFFFF))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "今天没有课 🎉",
                            style = TextStyle(
                                color = ColorProvider(Color(0xB3FFFFFF)),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                } else {
                    // 今日课程列表
                    todayCourses.take(4).forEach { course ->
                        val periodConfig = periodConfigs.find { it.period == course.startPeriod }
                        val startTime = periodConfig?.startTime ?: ""
                        val endTime = periodConfig?.let { config ->
                            val endConfig = periodConfigs.find { it.period == course.endPeriod }
                            endConfig?.endTime ?: config.endTime
                        } ?: ""

                        TodayCourseItem(
                            course = course,
                            timeRange = "$startTime-$endTime"
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }

                    if (todayCourses.size > 4) {
                        Text(
                            text = "还有 ${todayCourses.size - 4} 节课...",
                            style = TextStyle(
                                color = ColorProvider(Color(0x80FFFFFF)),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            ),
                            modifier = GlanceModifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TodayCourseItem(
        course: Course,
        timeRange: String
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(8.dp)
                .background(ColorProvider(Color(0x30FFFFFF)))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 时间
            Column(
                modifier = GlanceModifier.width(56.dp)
            ) {
                Text(
                    text = "第${course.startPeriod}-${course.endPeriod}节",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF818CF8)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = timeRange,
                    style = TextStyle(
                        color = ColorProvider(Color(0x80FFFFFF)),
                        fontSize = 9.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // 课程信息
            Column(
                modifier = GlanceModifier.defaultWeight()
            ) {
                Text(
                    text = course.name,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Text(
                    text = course.room,
                    style = TextStyle(
                        color = ColorProvider(Color(0xB3FFFFFF)),
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
