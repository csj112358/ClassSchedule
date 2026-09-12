package com.classschedule.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 玻璃日历选择器。
 *
 * 替换 Material 的 `DatePicker`：后者的日历网格、头部色块和按钮是固定安卓外观，
 * 在玻璃界面里非常突兀。这里用玻璃排版自己画一套：
 *
 * - 面板本身是玻璃对话框（[GlassDialog]）
 * - 月份标题 + 左右翻月（弹簧无关，纯点击）
 * - 周一到周日表头（中文习惯周一为第一天）
 * - 日期网格：选中日为主色填充圆角格，今天有一圈细描边
 *
 * 与 Material 版行为一致：只选日期（不含时间），返回当天 00:00 的时间戳。
 */
@Composable
fun GlassDatePickerDialog(
    initialDateMillis: Long?,
    onDismissRequest: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val initial = remember(initialDateMillis) {
        Calendar.getInstance().apply {
            timeInMillis = initialDateMillis?.takeIf { it > 0 } ?: System.currentTimeMillis()
        }
    }
    var year by remember { mutableIntStateOf(initial.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(initial.get(Calendar.MONTH)) }
    var selected by remember {
        mutableStateOf(
            initialDateMillis?.takeIf { it > 0 }?.let { startOfDay(it) }
        )
    }

    GlassDialog(
        onDismissRequest = onDismissRequest,
        title = { GlassDialogTitle("选择日期") },
        confirmButton = {
            GlassDialogAction(text = "确定", onClick = {
                onDateSelected(selected ?: startOfDay(System.currentTimeMillis()))
            })
        },
        dismissButton = {
            GlassDialogAction(text = "取消", onClick = onDismissRequest)
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MonthHeader(
                year = year,
                month = month,
                onPrev = {
                    if (month == 0) { month = 11; year -= 1 } else month -= 1
                },
                onNext = {
                    if (month == 11) { month = 0; year += 1 } else month += 1
                }
            )

            WeekdayHeader()

            DayGrid(
                year = year,
                month = month,
                selected = selected,
                onSelect = { selected = it }
            )
        }
    }
}

@Composable
private fun MonthHeader(
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(GlassTokens.minTouchTarget)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上个月",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "$year 年 ${month + 1} 月",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onNext, modifier = Modifier.size(GlassTokens.minTouchTarget)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下个月",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DayGrid(
    year: Int,
    month: Int,
    selected: Long?,
    onSelect: (Long) -> Unit
) {
    val today = remember { startOfDay(System.currentTimeMillis()) }
    val cells = remember(year, month) { buildMonthCells(year, month) }

    // 每行 7 天
    Column(modifier = Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { dayStart ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayStart == null) return@Box
                        val isSelected = selected != null && sameDay(dayStart, selected)
                        val isToday = sameDay(dayStart, today)
                        val shape = RoundedCornerShape(GlassTokens.radiusInner)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(shape)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> Color_TransparentWhite
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    }
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelect(dayStart) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayOfMonth(dayStart).toString(),
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontSize = 14.sp,
                                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                // 补齐本行剩余空位
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

private val Color_TransparentWhite = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f)

/** 当月每一天的 00:00 时间戳；月初前的空位与月末后的空位为 null */
private fun buildMonthCells(year: Int, month: Int): List<Long?> {
    val first = Calendar.getInstance().apply {
        clear()
        set(year, month, 1)
    }
    // Calendar.MONDAY = 2 → 周一为第 0 列
    val leading = (first.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)

    val cells = ArrayList<Long?>(42)
    repeat(leading) { cells.add(null) }
    for (d in 1..daysInMonth) {
        val c = Calendar.getInstance().apply {
            clear()
            set(year, month, d)
        }
        cells.add(c.timeInMillis)
    }
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun sameDay(a: Long, b: Long): Boolean {
    val f = SimpleDateFormat("yyyyMMdd", Locale.US)
    return f.format(Date(a)) == f.format(Date(b))
}

private fun dayOfMonth(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)
