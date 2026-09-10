package com.classschedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classschedule.data.model.*
import com.classschedule.ui.components.AnimatedGradientBackground
import com.classschedule.ui.components.GlassButton
import com.classschedule.ui.components.GlassCard
import com.classschedule.ui.viewmodel.MainViewModel
import com.classschedule.ui.viewmodel.getDayName

@Composable
fun ScheduleScreen(
    viewModel: MainViewModel,
    onAddCourse: () -> Unit,
    onEditCourse: (Long) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val activeSemester by viewModel.activeSemester.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val coursesByWeek by viewModel.coursesByWeek.collectAsState()
    val periodConfigs by viewModel.periodConfigs.collectAsState()
    var infoCourse by remember { mutableStateOf<Course?>(null) }

    AnimatedGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            HeaderSection(
                semester = activeSemester,
                currentWeek = currentWeek,
                maxWeek = viewModel.semesterWeekCount(),
                weekDateRange = viewModel.getCurrentWeekDateRange(),
                onWeekChange = { viewModel.setWeek(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (activeSemester == null) {
                EmptySemesterHint(onNavigateToSettings = onNavigateToSettings)
            } else {
                ScheduleContent(
                    courses = coursesByWeek,
                    periodConfigs = periodConfigs,
                    onShowInfo = { infoCourse = it },
                    onEditCourse = onEditCourse,
                    getDateForDay = { dayOfWeek -> viewModel.getDateForWeekAndDay(currentWeek, dayOfWeek) }
                )
            }
        }

        if (activeSemester != null) {
            FloatingActionButton(
                onClick = onAddCourse,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomEnd),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "添加课程",
                    tint = Color.White
                )
            }
        }
    }

    infoCourse?.let { course ->
        AlertDialog(
            onDismissRequest = { infoCourse = null },
            title = { Text(course.name) },
            text = {
                Column {
                    if (course.teacher.isNotBlank()) InfoRow("教师", course.teacher)
                    if (course.room.isNotBlank()) InfoRow("教室", course.room)
                    InfoRow("时间", "${getDayName(course.dayOfWeek)} 第${course.startPeriod}-${course.endPeriod}节")
                    InfoRow("周次", formatWeeks(course))
                    if (course.note.isNotBlank()) InfoRow("备注", course.note)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    infoCourse = null
                    onEditCourse(course.id)
                }) { Text("编辑") }
            },
            dismissButton = {
                TextButton(onClick = { infoCourse = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun HeaderSection(
    semester: Semester?,
    currentWeek: Int,
    maxWeek: Int = 30,
    weekDateRange: String = "",
    onWeekChange: (Int) -> Unit
) {
    var weekMenuExpanded by remember { mutableStateOf(false) }
    val noDates = semester != null && semester.startDate <= 0

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = semester?.name ?: "未设置学期",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (semester != null) {
                        Text(
                            text = "第 ${currentWeek} 周${if (weekDateRange.isNotEmpty()) " ($weekDateRange)" else ""}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }

                if (semester != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (currentWeek > 1) onWeekChange(currentWeek - 1)
                        }) {
                            Icon(
                                Icons.Default.KeyboardArrowLeft,
                                contentDescription = "上一周",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 点击当前周可下拉快速跳转到任意一周
                        Box {
                            Surface(
                                onClick = { weekMenuExpanded = true },
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "第${currentWeek}周",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = weekMenuExpanded,
                                onDismissRequest = { weekMenuExpanded = false }
                            ) {
                                (1..maxWeek).forEach { w ->
                                    DropdownMenuItem(
                                        text = { Text("第 $w 周") },
                                        onClick = {
                                            onWeekChange(w)
                                            weekMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(onClick = {
                            if (currentWeek < maxWeek) onWeekChange(currentWeek + 1)
                        }) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "下一周",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            if (noDates) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "未设置学期起止日期，本周次未关联真实日期。到 设置 → 学期管理 设置后可自动定位当前周。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun EmptySemesterHint(onNavigateToSettings: () -> Unit = {}) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        GlassCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "请先创建学期",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "前往设置页面创建学期并设置为当前学期",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                GlassButton(
                    onClick = onNavigateToSettings,
                    text = "前往设置"
                )
            }
        }
    }
}

@Composable
private fun ScheduleContent(
    courses: List<Course>,
    periodConfigs: List<PeriodConfig>,
    onShowInfo: (Course) -> Unit,
    onEditCourse: (Long) -> Unit,
    getDateForDay: (Int) -> Long? = { null }
) {
    val sortedConfigs = periodConfigs.sortedBy { it.period }
    val cellHeight = 64.dp
    val cellPadding = 2.dp

    val sdf = remember { java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault()) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 星期行头（含日期）
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Spacer(modifier = Modifier.width(50.dp))
                (1..7).forEach { day ->
                    val dateTs = getDateForDay(day)
                    val dateStr = dateTs?.let { sdf.format(java.util.Date(it)) } ?: ""

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = cellPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = getDayName(day),
                            color = if (day <= 5) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (dateStr.isNotEmpty()) {
                            Text(
                                text = dateStr,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // 课程表格 - 底层格子 + 上层课程卡片叠加
        item {
            val allCourses = courses.distinctBy { it.id }
            val firstPeriod = sortedConfigs.firstOrNull()?.period ?: 1

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { clip = false }
            ) {
                // 底层：所有节次行（固定高度 + 空格子）
                Column(modifier = Modifier.fillMaxWidth()) {
                    sortedConfigs.forEach { config ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight)
                        ) {
                            // 左侧节次信息
                            Column(
                                modifier = Modifier
                                    .width(50.dp)
                                    .padding(end = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${config.period}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = config.startTime,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }

                            // 右侧空格子
                            (1..7).forEach { _ ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = cellPadding)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .border(
                                            width = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                )
                            }
                        }
                    }
                }

                // 上层：课程卡片叠加（绝对定位）
                allCourses.forEach { course ->
                    val dayIndex = course.dayOfWeek - 1
                    val rowIndex = course.startPeriod - firstPeriod
                    val spanCount = course.endPeriod - course.startPeriod + 1
                    val cardHeight = cellHeight * spanCount

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 50.dp)
                            .offset(y = cellHeight * rowIndex)
                    ) {
                        // 左侧占位
                        repeat(dayIndex) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        // 课程卡片
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(cardHeight)
                                .padding(horizontal = cellPadding)
                        ) {
                            CourseCard(
                                course = course,
                                spanCount = spanCount,
                                onClick = { onShowInfo(course) },
                                onLongClick = { onEditCourse(course.id) }
                            )
                        }
                        // 右侧占位
                        repeat(7 - dayIndex - 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // 上层：大课中间行的点击区域（透明覆盖层）
                allCourses.forEach { course ->
                    val spanCount = course.endPeriod - course.startPeriod + 1
                    if (spanCount <= 1) return@forEach

                    (1 until spanCount).forEach { offset ->
                        val rowIndex = course.startPeriod - firstPeriod + offset

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 50.dp)
                                .offset(y = cellHeight * rowIndex)
                        ) {
                            repeat(course.dayOfWeek - 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(cellHeight)
                                    .padding(horizontal = cellPadding)
                                    .pointerInput(course.id) {
                                        detectTapGestures(
                                            onTap = { onShowInfo(course) },
                                            onLongPress = { onEditCourse(course.id) }
                                        )
                                    }
                            )
                            repeat(7 - course.dayOfWeek) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.width(52.dp)
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatWeeks(course: Course): String {
    val range = if (course.weekStart == course.weekEnd) "第${course.weekStart}周"
    else "第${course.weekStart}-${course.weekEnd}周"
    val parity = when (course.weekParity) {
        1 -> "（单周）"
        2 -> "（双周）"
        else -> ""
    }
    return range + parity
}

@Composable
private fun CourseCard(
    course: Course,
    spanCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val color = CourseColors.colors.getOrElse(course.colorIndex) {
        CourseColors.colors[0]
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((64 * spanCount - 4).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(color.primary),
                        Color(color.secondary)
                    )
                )
            )
            .pointerInput(course.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = course.name,
                color = Color(color.text),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = course.room,
                color = Color(color.text).copy(alpha = 0.8f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
