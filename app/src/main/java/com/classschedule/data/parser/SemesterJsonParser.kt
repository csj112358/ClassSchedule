package com.classschedule.data.parser

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * 单条可落库的课次（已展开到连续周次区间 + 单双周标记）。
 * weekParity: 0=每周, 1=仅单周, 2=仅双周
 */
data class ParsedOccurrence(
    val name: String,
    val room: String,
    val teacher: String,
    val dayOfWeek: Int,        // 1=周一 … 7=周日
    val startPeriod: Int,      // 开始节次
    val endPeriod: Int,        // 结束节次
    val weekStart: Int,
    val weekEnd: Int,
    val weekParity: Int
)

/**
 * 解析一份教务课表 JSON 的最终结果。
 */
data class TimetableParseResult(
    val occurrences: List<ParsedOccurrence>,
    val sectionTimes: Map<Int, Pair<String, String>>, // 节次 -> (开始时间, 结束时间)，可为空
    val maxWeek: Int,
    val distinctCourseCount: Int
)

/**
 * 教务课表 JSON 解析器 —— 学校格式适配模板。
 *
 * ⚠️ 本文件是「空白模板」，**不内置任何具体学校的课表格式**。
 * 直接运行会在导入页提示「尚未适配你学校的课表格式」。
 * 你需要根据自己学校教务系统返回的 JSON 结构，按下面 3 步填好解析逻辑即可：
 *
 *   第 1 步：拿到课表 JSON，理清结构（周几 / 课程名 / 教师 / 教室 / 节次 / 周次 / 单双周）
 *   第 2 步：在下方「一、原始 JSON 结构」处定义与之一一对应的数据类
 *   第 3 步：在「二、解析入口」的 parse() 里把字段映射成课次（用 makeOccurrence()）
 *
 * 详细说明见 README「学校适配说明」。
 */
object SemesterJsonParser {

    private val gson = Gson()

    // =====================================================================
    // 一、原始 JSON 结构（TODO：替换成你学校的字段）
    // =====================================================================
    //
    // 用 Gson 数据类映射你学校教务返回的 JSON。Gson 默认按字段名匹配；
    // 若字段名与 JSON 里的不一致，用
    // @com.google.gson.annotations.SerializedName("实际字段名") 标注。
    //
    // 例如你学校返回的是这样的结构（仅示意，请按实际替换）：
    //   { "data": { "courses": [ ... ] } }
    //
    // 那么你可以这样定义（把注释去掉、改成真实字段）：
    //
    //   private data class RawSchedule(
    //       val data: RawData? = null
    //   )
    //
    //   private data class RawData(
    //       val courses: List<RawCourse>? = null   // TODO: 改成你学校的字段名
    //   )
    //
    //   private data class RawCourse(
    //       val name: String? = null,       // 课程名
    //       val teacher: String? = null,    // 教师
    //       val room: String? = null,       // 教室
    //       val day: Int? = null,           // 星期几（1=周一）
    //       val section: String? = null,    // 节次
    //       val weeks: String? = null       // 周次
    //   )

    // =====================================================================
    // 二、解析入口（TODO：在这里实现你学校的解析逻辑）
    // =====================================================================

    /**
     * 完整解析一份课表 JSON。
     *
     * 典型实现流程：
     *   1. 用 [fromJson] 把原文反序列化成你在「一」里定义的数据类；
     *   2. 遍历每一天 / 每一门课，提取出通用字段；
     *   3. 对每一条课次调用 [makeOccurrence] 生成 ParsedOccurrence 并收集；
     *   4. （可选）把「节次 -> 上课时间」收集进 sectionTimes，用于自动补齐课时配置；
     *   5. 用 [TimetableParseResult] 打包返回。
     *
     * 解析不出课程时抛出 IllegalArgumentException（消息会显示在导入页）。
     */
    fun parse(rawText: String): TimetableParseResult {
        // TODO: 删除下面这行，替换成你自己的解析实现。
        throw UnsupportedOperationException(
            "尚未适配你学校的课表格式。请参考 README「学校适配说明」与本文件里的 TODO 注释，" +
                "在 SemesterJsonParser.parse() 里填入你学校教务 JSON 的解析逻辑。"
        )

        // 下面是一个「实现示例」骨架，供参考（请按你学校结构改写，别直接照抄）：
        //   val schedule = fromJson(rawText, RawSchedule::class.java)
        //       ?: throw IllegalArgumentException("无法解析 JSON")
        //   val courses = schedule.data?.courses
        //       ?: throw IllegalArgumentException("缺少 data.courses")
        //
        //   val occurrences = mutableListOf<ParsedOccurrence>()
        //   for (c in courses) {
        //       // ... 在这里解析周次、节次、单双周等，得到 weekStart/weekEnd/weekParity ...
        //       occurrences += makeOccurrence(
        //           dayOfWeek = c.day ?: 0,
        //           name = c.name ?: "",
        //           room = c.room ?: "",
        //           teacher = c.teacher ?: "",
        //           startPeriod = 1,
        //           endPeriod = 2,
        //           weekStart = 1,
        //           weekEnd = 16
        //       )
        //   }
        //   if (occurrences.isEmpty()) throw IllegalArgumentException("没有提取到课程")
        //
        //   return TimetableParseResult(
        //       occurrences = occurrences,
        //       sectionTimes = emptyMap(),  // 或按需收集节次时间
        //       maxWeek = occurrences.maxOf { it.weekEnd },
        //       distinctCourseCount = occurrences.map { it.name }.distinct().size
        //   )
    }

    // =====================================================================
    // 三、通用工具（一般无需修改）
    // =====================================================================

    /**
     * 把原文反序列化成你定义的数据类。
     * 例如：val schedule = fromJson(rawText, RawSchedule::class.java)
     */
    fun <T> fromJson(rawText: String, clazz: Class<T>): T? = try {
        gson.fromJson(rawText.trim(), clazz)
    } catch (e: JsonSyntaxException) {
        null
    }

    /**
     * 生成一条课次。参数都是通用字段，你只需把 JSON 里解析出的值传进来。
     * 若同一门课有多个周次区间（如 1-8 周 + 10-17 周），拆成多条分别调用。
     */
    fun makeOccurrence(
        dayOfWeek: Int,
        name: String,
        room: String = "",
        teacher: String = "",
        startPeriod: Int,
        endPeriod: Int,
        weekStart: Int,
        weekEnd: Int,
        weekParity: Int = 0
    ): ParsedOccurrence = ParsedOccurrence(
        name = name,
        room = room,
        teacher = teacher,
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        weekStart = weekStart,
        weekEnd = weekEnd,
        weekParity = weekParity
    )

    /** 把单双周整数转成可读文本（供预览展示用） */
    fun parityText(parity: Int): String = when (parity) {
        1 -> "单周"
        2 -> "双周"
        else -> ""
    }
}
