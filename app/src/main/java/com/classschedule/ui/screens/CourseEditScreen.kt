package com.classschedule.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classschedule.data.model.Course
import com.classschedule.data.model.CourseColors
import com.classschedule.ui.components.*
import com.classschedule.ui.glass.GlassDialog
import com.classschedule.ui.glass.GlassDialogBody
import com.classschedule.ui.glass.GlassDialogTitle
import com.classschedule.ui.glass.GlassDropdownItem
import com.classschedule.ui.glass.GlassDropdownMenu
import com.classschedule.ui.viewmodel.MainViewModel
import com.classschedule.ui.viewmodel.getDayName

@Composable
fun CourseEditScreen(
    viewModel: MainViewModel,
    courseId: Long? = null,
    onBack: () -> Unit
) {
    val periodConfigs by viewModel.periodConfigs.collectAsState()
    val allCourses by viewModel.allCourses.collectAsState()

    var name by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var dayOfWeek by remember { mutableIntStateOf(1) }
    var startPeriod by remember { mutableIntStateOf(1) }
    var endPeriod by remember { mutableIntStateOf(1) }
    var weekStart by remember { mutableIntStateOf(1) }
    var weekEnd by remember { mutableIntStateOf(16) }
    var weekParity by remember { mutableIntStateOf(0) }
    var colorIndex by remember { mutableIntStateOf(0) }
    var isLoaded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isEditing = courseId != null && courseId > 0

    LaunchedEffect(courseId) {
        if (isEditing && courseId != null) {
            viewModel.allCourses.collect { courses ->
                val course = courses.find { it.id == courseId }
                if (course != null && !isLoaded) {
                    name = course.name; room = course.room; teacher = course.teacher
                    dayOfWeek = course.dayOfWeek; startPeriod = course.startPeriod; endPeriod = course.endPeriod
                    weekStart = course.weekStart; weekEnd = course.weekEnd; weekParity = course.weekParity; colorIndex = course.colorIndex
                    isLoaded = true
                }
            }
        }
    }

    GradientBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(text = if (isEditing) "编辑课程" else "添加课程", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "课程信息", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(16.dp))
                GlassTextField(value = name, onValueChange = { name = it }, label = "课程名称", placeholder = "请输入课程名称")
                Spacer(modifier = Modifier.height(12.dp))
                GlassTextField(value = room, onValueChange = { room = it }, label = "教室地点", placeholder = "请输入教室地点")
                Spacer(modifier = Modifier.height(12.dp))
                GlassTextField(value = teacher, onValueChange = { teacher = it }, label = "教师姓名（选填）", placeholder = "请输入教师姓名")
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "时间设置", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "星期", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..7).forEach { day ->
                        DayChip(day = day, selected = dayOfWeek == day, onClick = { dayOfWeek = day })
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "节次", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    PeriodDropdown(label = "开始", selectedPeriod = startPeriod, maxPeriod = periodConfigs.size, onSelected = { startPeriod = it })
                    Text(text = "至", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PeriodDropdown(label = "结束", selectedPeriod = endPeriod, maxPeriod = periodConfigs.size, onSelected = { endPeriod = it })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "周次设置", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    WeekDropdown(label = "开始周", selectedWeek = weekStart, onSelected = { weekStart = it })
                    Text(text = "至", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WeekDropdown(label = "结束周", selectedWeek = weekEnd, onSelected = { weekEnd = it })
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "单双周", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParityChip(text = "每周", selected = weekParity == 0, onClick = { weekParity = 0 }, modifier = Modifier.weight(1f))
                    ParityChip(text = "仅单周", selected = weekParity == 1, onClick = { weekParity = 1 }, modifier = Modifier.weight(1f))
                    ParityChip(text = "仅双周", selected = weekParity == 2, onClick = { weekParity = 2 }, modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "颜色", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CourseColors.colors.forEachIndexed { index, color ->
                        ColorDot(color = Color(color.primary), selected = colorIndex == index, onClick = { colorIndex = index })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            GlassButton(
                onClick = {
                    if (name.isNotBlank() && room.isNotBlank()) {
                        if (isEditing && courseId != null) {
                            allCourses.find { it.id == courseId }?.let {
                                viewModel.updateCourse(it.copy(name = name, room = room, teacher = teacher, dayOfWeek = dayOfWeek, startPeriod = startPeriod, endPeriod = endPeriod, weekStart = weekStart, weekEnd = weekEnd, weekParity = weekParity, colorIndex = colorIndex))
                            }
                        } else {
                            viewModel.addCourse(name = name, room = room, dayOfWeek = dayOfWeek, startPeriod = startPeriod, endPeriod = endPeriod, weekStart = weekStart, weekEnd = weekEnd, weekParity = weekParity, teacher = teacher)
                        }
                        onBack()
                    }
                },
                text = "保存",
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && room.isNotBlank()
            )

            if (isEditing) {
                Spacer(modifier = Modifier.height(12.dp))
                GlassButton(onClick = { showDeleteDialog = true }, text = "删除课程", modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDeleteDialog) {
        GlassDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除课程「$name」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    courseId?.let { id -> allCourses.find { it.id == id }?.let { viewModel.deleteCourse(it) } }
                    showDeleteDialog = false; onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun DayChip(day: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = getDayName(day).takeLast(1),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ParityChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PeriodDropdown(label: String, selectedPeriod: Int, maxPeriod: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { expanded = true }, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "$label 第${selectedPeriod}节", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GlassDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..maxPeriod).forEach { period ->
                GlassDropdownItem(text = "第${period}节", onClick = { onSelected(period); expanded = false })
            }
        }
    }
}

@Composable
private fun WeekDropdown(label: String, selectedWeek: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { expanded = true }, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "$label 第${selectedWeek}周", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GlassDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..30).forEach { week ->
                GlassDropdownItem(text = "第${week}周", onClick = { onSelected(week); expanded = false })
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.small, color = color, modifier = Modifier.size(36.dp)) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp))
        }
    }
}
