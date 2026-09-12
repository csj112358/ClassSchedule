package com.classschedule.data.api

import com.classschedule.data.parser.ParsedOccurrence
import com.classschedule.data.parser.SemesterJsonParser
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容 Chat API 请求/响应模型
 */
data class KimiMessage(
    val role: String,
    val content: String
)

data class KimiRequest(
    val model: String,
    val messages: List<KimiMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 131072,
    val thinking: Map<String, String>? = null
)

data class KimiChoice(
    val message: KimiResponseMessage
)

data class KimiResponseMessage(
    val content: String?,
    @SerializedName("reasoning_content")
    val reasoningContent: String? = null
)

data class KimiError(
    val message: String?,
    val type: String?,
    val code: String?
)

data class KimiResponse(
    val choices: List<KimiChoice>?,
    val error: KimiError? = null
)

/**
 * Kimi API 服务（OpenAI 兼容 /v1/chat/completions）
 */
interface KimiApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") auth: String,
        @Body request: KimiRequest
    ): KimiResponse
}

/**
 * Kimi API 客户端
 */
object KimiApiClient {
    private const val DEFAULT_BASE_URL = "https://api.deepseek.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.HEADERS
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun createApi(baseUrl: String = DEFAULT_BASE_URL): KimiApi {
        // 自动补全 https:// 和尾部 /
        val url = baseUrl.trim().let { raw ->
            val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
            if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        }
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KimiApi::class.java)
    }

    /**
     * 构建“把课表 JSON 转成标准课次列表”的纯文本请求。
     *
     * 提示词刻意**不假设任何具体学校的 JSON 结构**：字段名、层级、周次写法
     * 都由模型自行从用户给出的原始 JSON 中推断，因此对任意学校 / 任意教务系统通用。
     */
    fun buildTimetableParseRequest(rawJson: String, model: String): KimiRequest {
        val system = """
你是一个课程表数据整理助手。用户会给出一份“个人课表”JSON（来自某高校教务系统，各校字段命名与层级差异很大）。请先自行推断其结构，再整理成统一的课次列表。只输出 JSON，不要任何解释或 Markdown 代码块之外的文字。

结构推断要点（不要假设固定字段名，一切以用户实际给的 JSON 为准）：
- 找到承载“按星期划分的课表”的那部分，通常是一个长度 7 的数组（索引 0=周一 … 6=周日），但也可能是带 dayOfWeek / 星期 字段的对象数组，或按周次分组的嵌套结构。
- 时间可能被拆成多个“时间段/节次块”，每块含起止节次与起止时间；也可能每门课自带节次字段。
- 课程名、教师、教室可能是独立字段，也可能藏在形如 [{Key:"xxx", Name:"yyy"}] 的键值对数组里（此时按 Key 的语义判断是课程名/教师/教室/时间）。
- 周次常见写法："1-16周"、"1-8,10-17周"、"第1-16周"、"1-16周(单)"、"[单周]/[双周]"、"奇/偶" 等；节次常见 "第1-2节"、"[3-4节]"。
- 若同一门课同时出现多个周次区间或单双周，请拆成多条；同一门课同一时段重复出现只保留一条。

输出要求：
逐条输出你能识别出的每一个课次。每条对象字段：
{
  "name": 课程名,
  "room": 教室,
  "teacher": 教师(可为空),
  "day_of_week": 1到7(1=周一),
  "start_period": 开始节次,
  "end_period": 结束节次,
  "weeks": "该条的周次与单双周原文（保留原写法即可，如 1-8周[单周] 或 10-17周）",
  "week_start": 该条起始周(整数),
  "week_end": 该条结束周(整数),
  "week_parity": 0或1或2(0=每周,1=单周,2=双周)
}
请务必把 weeks 原文填进去（这是解析周次的依据），week_start/week_end/week_parity 作为辅助。若周次信息确实缺失，可在 week_start/week_end 填 1 与最大可见周次、week_parity=0。
返回形如 {"courses":[...]}。
        """.trimIndent()

        val user = "课表JSON如下：\n\n$rawJson"

        return KimiRequest(
            model = model,
            messages = listOf(
                KimiMessage(role = "system", content = system),
                KimiMessage(role = "user", content = user)
            ),
            maxTokens = 16384
        )
    }

    /**
     * 从模型返回文本中提取 JSON 对象（去掉 ```json 包裹或前后文字）
     */
    fun extractJsonBlock(responseText: String): String {
        val text = responseText.trim()
        return when {
            text.contains("```json") -> {
                val start = text.indexOf("```json") + 7
                val end = text.indexOf("```", start)
                if (end > start) text.substring(start, end).trim() else text
            }
            text.contains("```") -> {
                val start = text.indexOf("```") + 3
                val end = text.indexOf("```", start)
                if (end > start) text.substring(start, end).trim() else text
            }
            text.contains("{") -> {
                val start = text.indexOf("{")
                val lastEnd = text.lastIndexOf("}")
                if (lastEnd > start) text.substring(start, lastEnd + 1).trim() else text
            }
            else -> text
        }
    }

    /**
     * 解析模型返回的课次列表，统一展开成 ParsedOccurrence。
     * 优先使用 weeks 原文本地展开；没有时用 week_start/week_end/week_parity。
     */
    fun parseLlmOccurrences(responseText: String): List<ParsedOccurrence> {
        if (responseText.isBlank()) return emptyList()
        val jsonStr = extractJsonBlock(responseText)
        val result = try {
            com.google.gson.Gson().fromJson(jsonStr, LlmOccurrenceResult::class.java)
        } catch (e: Exception) {
            android.util.Log.e("KimiAPI", "LLM结果解析失败", e)
            return emptyList()
        }

        val out = LinkedHashSet<ParsedOccurrence>()
        for (c in result.courses.orEmpty()) {
            val day = c.dayOfWeek ?: continue
            val name = c.name ?: continue
            if (name.isBlank()) continue
            val sp = (c.startPeriod ?: 1).coerceAtLeast(1)
            val ep = (c.endPeriod ?: sp).coerceAtLeast(sp)
            val occurrences = SemesterJsonParser.toOccurrences(
                dayOfWeek = day,
                name = name,
                room = c.room ?: "",
                teacher = c.teacher ?: "",
                startPeriod = sp,
                endPeriod = ep,
                timeText = c.weeks,
                fallbackWeekStart = c.weekStart,
                fallbackWeekEnd = c.weekEnd,
                fallbackParity = c.weekParity ?: 0
            )
            out.addAll(occurrences)
        }
        return out.toList()
    }
}

/**
 * LLM 返回的课次列表
 */
data class LlmOccurrenceResult(
    val courses: List<LlmOccurrenceEntry>? = null
)

data class LlmOccurrenceEntry(
    val name: String? = null,
    val room: String? = null,
    val teacher: String? = null,
    @SerializedName("day_of_week")
    val dayOfWeek: Int? = null,
    @SerializedName("start_period")
    val startPeriod: Int? = null,
    @SerializedName("end_period")
    val endPeriod: Int? = null,
    val weeks: String? = null,
    @SerializedName("week_start")
    val weekStart: Int? = null,
    @SerializedName("week_end")
    val weekEnd: Int? = null,
    @SerializedName("week_parity")
    val weekParity: Int? = null
)
