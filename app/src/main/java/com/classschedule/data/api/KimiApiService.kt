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
     * 构建“把教务整学期课表 JSON 转成标准课次列表”的纯文本请求
     */
    fun buildTimetableParseRequest(rawJson: String, model: String): KimiRequest {
        val system = """
你是一个课程表数据整理助手。用户会给出一份某高校教务系统返回的“整学期个人课表”JSON。请把它整理成统一的课次列表，只输出 JSON，不要任何解释或 Markdown 代码块之外的文字。

原始 JSON 结构要点：
- data.AdjustDays 是数组，长度7，索引 0=周一 … 6=周日，每个元素含 AM__TimePieces / PM__TimePieces / EV__TimePieces（有时还有 MN/AF，都是时间段数组）。
- 每个时间段(TimePiece)含 StartSection/EndSection（连续节次区间）、StartTime/EndTime、Dtos(课程数组)。
- 每个 Dto 的 Content 是数组，其中含 {Key: "Lesson"|"Teacher"|"Room"|"Time", Name: ...}：Lesson=课程名、Teacher=教师、Room=教室、Time=周次与节次（如 "1-8,10-17周[1-2节][单周]"）。
- Time 字段的周次可能有多个区间（如 1-8,10-17）也可能带 [单周]/[双周]。

输出要求：
逐条输出每个在 Content 里带 Lesson 的课程课次；同一门课若 Time 含多个周次区间或单双周拆分，请拆成多条。每条对象字段：
{
  "name": 课程名,
  "room": 教室,
  "teacher": 教师(可为空),
  "day_of_week": 1到7(1=周一),
  "start_period": 开始节次,
  "end_period": 结束节次,
  "weeks": "把该条对应的 Time 字段原文(周次与单双周部分)原样复制，例如 1-8周[单周] 或 10-17周。含节次也保留也可",
  "week_start": 该条起始周(整数),
  "week_end": 该条结束周(整数),
  "week_parity": 0或1或2(0=每周,1=单周,2=双周)
}
请把 weeks 原文放进去（这是我解析周次的依据），week_start/week_end/week_parity 作为辅助。若 Time 字段缺失，可在 week_start/week_end 填 1、最大可见周次，week_parity=0。若同一门课同一时段在多个时间段里重复出现(如整周实践课全天占用)，只需输出一次。
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
