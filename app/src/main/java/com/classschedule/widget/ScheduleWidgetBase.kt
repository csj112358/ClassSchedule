package com.classschedule.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
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
import com.classschedule.MainActivity
import com.classschedule.data.db.AppDatabase
import com.classschedule.data.model.Course
import com.classschedule.data.model.PeriodConfig
import com.classschedule.data.model.Semester
import com.classschedule.data.prefs.SettingsStore
import com.classschedule.ui.glass.GlassTokens
import com.classschedule.ui.theme.ThemePalette
import com.classschedule.ui.theme.resolveTheme
import com.classschedule.util.ScheduleTime
import kotlinx.coroutines.flow.first

/**
 * 桌面小部件「今日课程」的渲染内容。
 *
 * 数据全部在 [provideGlance] 阶段读好，再交给 [ScheduleWidgetContent] 纯渲染，
 * 这样样式调整不会碰到数据库逻辑。
 */
data class WidgetData(
    val semester: Semester?,
    val currentWeek: Int,
    val todayDayOfWeek: Int,
    val todayCourses: List<Course>,
    val tomorrowCourses: List<Course>,
    val tomorrowDayOfWeek: Int,
    val periodConfigs: List<PeriodConfig>,
    val palette: ThemePalette
)

/** 课程在小部件里的展示状态 */
enum class CoursePhase { DONE, ACTIVE, NEXT, LATER }

/**
 * 小部件基类：负责读数据库 / 偏好，子类只决定尺寸与渲染入口。
 */
abstract class ScheduleWidgetBase : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)
        val settings = SettingsStore.get(context)

        // 数据源全部走 Room 的 Flow.first()，小部件刷新时取当前快照即可
        val semester = database.semesterDao().getActiveSemester().first()
        val periodConfigs = database.periodConfigDao().getAllPeriodConfigs().first()
            .sortedBy { it.period }
        val allCourses = if (semester != null) {
            database.courseDao().getCoursesBySemester(semester.id).first()
        } else {
            emptyList()
        }
        val themeName = settings.currentThemeName()

        val now = System.currentTimeMillis()
        val todayDayOfWeek = ScheduleTime.todayDayOfWeek(now)
        val currentWeek = ScheduleTime.currentWeek(semester, now)

        // 明天可能是下一周，周次越界时按最后一周处理，避免整周课程凭空消失
        val tomorrowDayOfWeek = if (todayDayOfWeek >= 7) 1 else todayDayOfWeek + 1
        val tomorrowWeek = if (todayDayOfWeek >= 7) currentWeek + 1 else currentWeek
        val tomorrowCourses = ScheduleTime.coursesInWeek(allCourses, tomorrowWeek)
            .filter { it.dayOfWeek == tomorrowDayOfWeek }
            .sortedBy { it.startPeriod }

        val data = WidgetData(
            semester = semester,
            currentWeek = currentWeek,
            todayDayOfWeek = todayDayOfWeek,
            todayCourses = ScheduleTime.coursesInWeek(allCourses, currentWeek)
                .filter { it.dayOfWeek == todayDayOfWeek }
                .sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }, { it.name })),
            tomorrowCourses = tomorrowCourses,
            tomorrowDayOfWeek = tomorrowDayOfWeek,
            periodConfigs = periodConfigs,
            palette = resolveTheme(themeName)
        )

        provideContent {
            ScheduleWidgetContent(data)
        }
    }
}

/**
 * 今日课程样式：标题 + 「正在上课 / 下一节」高亮 + 课程列表；
 * 今天没课时自动改为显示明天（周日或无课日）。
 *
 * ## 与 App 统一的只是"材质"，信息与格式保持原样
 * 面板/卡片改用 App 内同一套玻璃规则（[GlassTokens] 的同心圆角阶梯 + `surface @ 0.55` 的磨砂区），
 * **显示什么、怎么排、字号几号一律没动**（标题、节次、时间、课名、地点·老师、上课中/下一节标记、
 * 明天预览、还有 N 节…）。
 */
@Composable
fun ScheduleWidgetContent(data: WidgetData) {
    val context = LocalContext.current
    val todayCourses = data.todayCourses
    val showTomorrow = todayCourses.isEmpty()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            // 同心阶梯最外层：与 App 的面板/卡片同一个 32dp
            .cornerRadius(GlassTokens.radiusPanel)
            .background(ColorProvider(data.palette.background))
            // 与 App 的 panelPadding 一致，这样"外圆角 32 − 内边距 16 = 内圆角 16"才成立
            .padding(GlassTokens.panelPadding)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WidgetHeader(data, todayCourses.size)
            Spacer(modifier = GlanceModifier.height(6.dp))

            if (showTomorrow) {
                TomorrowPreview(data)
            } else {
                TodayList(data)
            }
        }
    }
}

@Composable
private fun WidgetHeader(data: WidgetData, todayCount: Int) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = data.semester?.name ?: "课程表",
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = buildString {
                append(ScheduleTime.dayName(data.todayDayOfWeek))
                append(" · 第").append(data.currentWeek).append("周")
                if (todayCount > 0) append(" · ").append(todayCount).append("节")
            },
            style = TextStyle(
                color = ColorProvider(data.palette.secondary),
                fontSize = 11.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun TodayList(data: WidgetData) {
    val phases = resolvePhases(data.todayCourses, data.periodConfigs)
    val visible = data.todayCourses.take(3)

    visible.forEachIndexed { index, course ->
        if (index > 0) Spacer(modifier = GlanceModifier.height(4.dp))
        CourseRow(
            course = course,
            phase = phases[index],
            periodConfigs = data.periodConfigs,
            palette = data.palette
        )
    }

    val hidden = data.todayCourses.size - visible.size
    if (hidden > 0) {
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "还有 $hidden 节…",
            style = TextStyle(
                color = ColorProvider(Color(0x99FFFFFF)),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            ),
            modifier = GlanceModifier.fillMaxWidth()
        )
    }
}

/**
 * 把 [top] 以 [alpha] 叠在 [bottom] 上，返回不透明色。
 *
 * 这就是 App 里"半透明玻璃叠在底色上"的**真实渲染结果**（sRGB 逐通道 alpha 合成），
 * 所以小部件的磨砂浓淡与 App 内 `GlassCard` 的 `surface @ 0.55` 是同一个值。
 *
 * 刻意不用 Compose 的 `lerp()`：它的插值空间与真实合成不一定一致（可能走 Oklab），
 * 那样算出来的颜色会与 App 里看到的差一点点。
 */
private fun overlay(top: Color, alpha: Float, bottom: Color): Color = Color(
    red = bottom.red + (top.red - bottom.red) * alpha,
    green = bottom.green + (top.green - bottom.green) * alpha,
    blue = bottom.blue + (top.blue - bottom.blue) * alpha
)

/**
 * 磨砂区的填充色：与 App 内 `GlassCard` **同一套规则** —— `surface` 以 0.55 混到底色上
 * （见 [overlay]）。
 */
private fun frostFill(palette: ThemePalette): Color =
    overlay(top = palette.surface, alpha = 0.55f, bottom = palette.background)

@Composable
private fun CourseRow(
    course: Course,
    phase: CoursePhase,
    periodConfigs: List<PeriodConfig>,
    palette: ThemePalette
) {
    val startTime = periodConfigs.find { it.period == course.startPeriod }?.startTime ?: ""
    val endTime = periodConfigs.find { it.period == course.endPeriod }?.endTime ?: ""
    val timeRange = if (startTime.isNotEmpty() && endTime.isNotEmpty()) "$startTime-$endTime" else ""

    // 卡片填充走 App 内 GlassCard 的规则：底色之上的"磨砂区"。
    // Glance 只能给纯色，所以用 [overlay] 预先合成到不透明（底色本身不透明，与叠一层半透明等价）。
    val frost = frostFill(palette)
    val cardColor = when (phase) {
        // 正在上的课：主题主色按 App 的强调用法（primary @ 0.35）压在磨砂区上
        CoursePhase.ACTIVE -> overlay(top = palette.primary, alpha = 0.35f, bottom = frost)
        // 已上完：同一块磨砂区，浓度降到 35%，存在感递减
        CoursePhase.DONE -> overlay(top = frost, alpha = 0.35f, bottom = palette.background)
        else -> frost
    }
    val titleColor = when (phase) {
        CoursePhase.ACTIVE -> Color.White
        CoursePhase.DONE -> Color(0x80FFFFFF)
        else -> Color.White
    }
    val subColor = when (phase) {
        CoursePhase.ACTIVE -> Color(0xE6FFFFFF)
        CoursePhase.DONE -> Color(0x66FFFFFF)
        else -> Color(0xB3FFFFFF)
    }
    val accentColor = when (phase) {
        CoursePhase.ACTIVE -> Color.White
        CoursePhase.NEXT -> palette.secondary
        CoursePhase.DONE -> Color(0x66FFFFFF)
        else -> palette.secondary
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            // 同心阶梯内层：外圆角 32 − 面板内边距 16 = 16，和 App 内嵌套元素同一档
            .cornerRadius(GlassTokens.radiusInner)
            .background(ColorProvider(cardColor))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.width(52.dp)) {
            Text(
                text = "第${course.startPeriod}-${course.endPeriod}节",
                style = TextStyle(
                    color = ColorProvider(accentColor),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            if (timeRange.isNotEmpty()) {
                Text(
                    text = timeRange,
                    style = TextStyle(color = ColorProvider(subColor), fontSize = 9.sp),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = course.name,
                style = TextStyle(
                    color = ColorProvider(titleColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            val subtitle = buildString {
                if (course.room.isNotBlank()) append(course.room)
                if (course.teacher.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(course.teacher)
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = TextStyle(color = ColorProvider(subColor), fontSize = 10.sp),
                    maxLines = 1
                )
            }
        }

        val badge = when (phase) {
            CoursePhase.ACTIVE -> "上课中"
            CoursePhase.NEXT -> "下一节"
            else -> ""
        }
        if (badge.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = badge,
                style = TextStyle(
                    color = ColorProvider(accentColor),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TomorrowPreview(data: WidgetData) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text(
            text = if (data.tomorrowCourses.isEmpty()) {
                "${ScheduleTime.dayName(data.tomorrowDayOfWeek)}也没有课，好好休息 🎉"
            } else {
                "今天没有课 · ${ScheduleTime.dayName(data.tomorrowDayOfWeek)}有 ${data.tomorrowCourses.size} 节"
            },
            style = TextStyle(
                color = ColorProvider(Color(0xB3FFFFFF)),
                fontSize = 12.sp
            )
        )

        if (data.tomorrowCourses.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            data.tomorrowCourses.take(3).forEach { course ->
                val time = ScheduleTime.timeRangeText(
                    data.periodConfigs, course.startPeriod, course.endPeriod
                )
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "第${course.startPeriod}-${course.endPeriod}节",
                        style = TextStyle(
                            color = ColorProvider(data.palette.secondary),
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = if (time.isEmpty()) course.name else "${course.name}  $time",
                        style = TextStyle(color = ColorProvider(Color(0xCCFFFFFF)), fontSize = 12.sp),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
            val hidden = data.tomorrowCourses.size - 3
            if (hidden > 0) {
                Text(
                    text = "还有 $hidden 节…",
                    style = TextStyle(color = ColorProvider(Color(0x80FFFFFF)), fontSize = 10.sp)
                )
            }
        }
    }
}

/**
 * 逐条判断课程状态：一段课正在进行时记为 ACTIVE，其后的第一节记为 NEXT，其余为 LATER/DONE。
 * 同一时段有多门课时它们共享状态，不会互相把对方挤成“已结束”。
 */
private fun resolvePhases(
    courses: List<Course>,
    periodConfigs: List<PeriodConfig>
): List<CoursePhase> {
    if (courses.isEmpty()) return emptyList()
    val nowMinutes = ScheduleTime.minutesOfDay()
    if (periodConfigs.isEmpty()) return List(courses.size) { CoursePhase.LATER }

    val activeFlags = courses.map { course ->
        val start = ScheduleTime.periodStartMinutes(periodConfigs, course.startPeriod)
        val end = ScheduleTime.periodEndMinutes(periodConfigs, course.endPeriod)
        start != null && end != null && nowMinutes >= start && nowMinutes < end
    }
    val nextIndex = courses.indices.firstOrNull { index ->
        val start = ScheduleTime.periodStartMinutes(periodConfigs, courses[index].startPeriod)
        !activeFlags[index] && start != null && start > nowMinutes
    }

    return courses.mapIndexed { index, course ->
        when {
            activeFlags[index] -> CoursePhase.ACTIVE
            index == nextIndex -> CoursePhase.NEXT
            else -> {
                val end = ScheduleTime.periodEndMinutes(periodConfigs, course.endPeriod)
                if (end != null && end <= nowMinutes) CoursePhase.DONE else CoursePhase.LATER
            }
        }
    }
}
