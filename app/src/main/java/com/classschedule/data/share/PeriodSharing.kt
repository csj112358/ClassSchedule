package com.classschedule.data.share

import com.classschedule.data.model.PeriodConfig

/** 课时时间文本的解析结果 */
data class PeriodTextParseResult(
    val configs: List<PeriodConfig>,
    val warnings: List<String>
)

/**
 * 「课时时间」分享文本的编解码。
 *
 * 格式（UTF-8，带 BOM，方便 Windows 记事本直接打开不乱码）：
 * ```
 * #课时时间分享 v1
 * #第1节,08:00,08:45,上午第一节
 * 1,08:00,08:45,上午第一节
 * 2,08:55,09:40,上午第二节
 * ```
 * 首行是识别标记，导入时只认这一行，避免把无关文本当课时时间吃进来。
 */
object PeriodSharing {

    const val MAGIC = "#课时时间分享 v1"
    private const val COLUMN_HEADER = "#节次,开始时间,结束时间,标签"
    const val FILE_EXTENSION = "txt"

    /** UTF-8 BOM，写在文件最前面让 Windows 记事本正确识别编码 */
    val BOM: ByteArray = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** 时间格式 "HH:mm" */
    private val TIME_REGEX = Regex("""^([01]?\d|2[0-3]):([0-5]?\d)$""")

    fun encode(periods: List<PeriodConfig>): String {
        val builder = StringBuilder()
        builder.append(MAGIC).append('\n')
        builder.append(COLUMN_HEADER).append('\n')
        periods.sortedBy { it.period }.forEach { config ->
            val label = config.label.replace('\n', ' ').replace(',', '，').trim()
            builder.append(config.period)
                .append(',')
                .append(config.startTime.trim())
                .append(',')
                .append(config.endTime.trim())
                .append(',')
                .append(label)
                .append('\n')
        }
        return builder.toString()
    }

    /** 建议的分享文件名，例如 课时时间_2026-02-14.txt */
    fun suggestedFileName(): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        return "课时时间_$date.$FILE_EXTENSION"
    }

    /**
     * 解析分享文本。
     * @throws IllegalArgumentException 首行不是本 App 的识别标记时
     */
    fun decode(rawText: String): PeriodTextParseResult {
        // 兼容 UTF-8 BOM 与 Windows 换行
        val text = rawText.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')
        val magicIndex = lines.indexOfFirst { it.trim() == MAGIC }
        if (magicIndex < 0) {
            throw IllegalArgumentException(
                "这不是本App分享的课时时间文件（缺少「$MAGIC」标记）。\n请让同学在 设置 → 分享 / 导入课时时间 里重新分享一份。"
            )
        }

        val warnings = mutableListOf<String>()
        val parsed = mutableListOf<PeriodConfig>()
        var skipped = 0

        for (line in lines.drop(magicIndex + 1)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val parts = trimmed.split(',', '，').map { it.trim() }
            if (parts.size < 3) {
                skipped++
                continue
            }

            val period = parts[0].toIntOrNull()
            if (period == null || period <= 0 || period > 30) {
                skipped++
                continue
            }
            val start = normalizeTime(parts[1])
            val end = normalizeTime(parts[2])
            if (start == null || end == null) {
                skipped++
                continue
            }
            val label = parts.getOrNull(3).orEmpty().take(20)
            parsed += PeriodConfig(period = period, startTime = start, endTime = end, label = label)
        }

        if (skipped > 0) warnings += "有 $skipped 行格式不正确，已跳过"

        // 同一节次出现多次时保留最后一条，避免覆盖写入时相互打架
        val deduped = parsed.associateBy { it.period }.values.sortedBy { it.period }
        val duplicates = parsed.size - deduped.size
        if (duplicates > 0) warnings += "有 $duplicates 个重复节次，已按最后一条为准"

        if (deduped.isEmpty()) {
            throw IllegalArgumentException("文件里没有解析到任何课时，请确认内容是本App分享出来的课时时间。")
        }

        // 节次不连续只做提示，不阻止导入
        val expected = (deduped.first().period..deduped.last().period).toSet()
        val missing = expected - deduped.map { it.period }.toSet()
        if (missing.isNotEmpty()) {
            warnings += "缺少第 ${missing.sorted().joinToString("、")} 节，导入后这些节次不会有时间"
        }

        return PeriodTextParseResult(configs = deduped, warnings = warnings)
    }

    /** "8:00" -> "08:00"；非法返回 null */
    private fun normalizeTime(raw: String): String? {
        val match = TIME_REGEX.find(raw) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        return String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
    }
}
