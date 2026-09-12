package com.classschedule.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 学期实体
 */
@Entity(tableName = "semesters")
data class Semester(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,           // 学期名称，如"2024秋季学期"
    val startDate: Long,        // 开始日期时间戳
    val endDate: Long,          // 结束日期时间戳
    val isActive: Boolean = false // 是否为当前学期
)

/**
 * 课程实体
 */
@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = Semester::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["semesterId"])]
)
data class Course(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val semesterId: Long,       // 所属学期ID
    val name: String,           // 课程名称
    val room: String,           // 教室地点
    val dayOfWeek: Int,         // 星期几 (1-7, 1=周一)
    val startPeriod: Int,       // 开始节次
    val endPeriod: Int,         // 结束节次
    val weekStart: Int,         // 开始周次
    val weekEnd: Int,           // 结束周次
    val weekParity: Int = 0,    // 单双周: 0=每周都有, 1=仅单周, 2=仅双周
    val colorIndex: Int = 0,    // 颜色索引，用于显示不同颜色
    val teacher: String = "",   // 教师（可选）
    val note: String = ""       // 备注（可选）
)

/**
 * 课时配置实体（全局生效）
 */
@Entity(tableName = "period_configs")
data class PeriodConfig(
    @PrimaryKey
    val period: Int,            // 第几节 (1, 2, 3...)
    val startTime: String,      // 开始时间 "HH:mm"
    val endTime: String,        // 结束时间 "HH:mm"
    val label: String = "",     // 标签，如"上午第一节"
)

/**
 * API配置实体
 */
@Entity(tableName = "api_configs")
data class ApiConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,           // 配置名称
    val apiKey: String,         // API Key
    val baseUrl: String,        // API基础URL
    val modelName: String,      // 模型名称
    val isActive: Boolean = false // 是否为当前使用的配置
)

/**
 * 课程颜色方案
 */
data class CourseColor(
    val primary: Long,
    val secondary: Long,
    val text: Long
)

/**
 * 预设颜色列表
 */
object CourseColors {
    val colors = listOf(
        CourseColor(0xFF6366F1, 0xFF818CF8, 0xFFFFFFFF), // Indigo
        CourseColor(0xFFEC4899, 0xFFF472B6, 0xFFFFFFFF), // Pink
        CourseColor(0xFF14B8A6, 0xFF2DD4BF, 0xFFFFFFFF), // Teal
        CourseColor(0xFFF97316, 0xFFFB923C, 0xFFFFFFFF), // Orange
        CourseColor(0xFF8B5CF6, 0xFFA78BFA, 0xFFFFFFFF), // Violet
        CourseColor(0xFF06B6D4, 0xFF22D3EE, 0xFFFFFFFF), // Cyan
        CourseColor(0xFFEF4444, 0xFFF87171, 0xFFFFFFFF), // Red
        CourseColor(0xFF10B981, 0xFF34D399, 0xFFFFFFFF), // Emerald
        CourseColor(0xFFF59E0B, 0xFFFBBF24, 0xFFFFFFFF), // Amber
        CourseColor(0xFF3B82F6, 0xFF60A5FA, 0xFFFFFFFF), // Blue
    )
}
