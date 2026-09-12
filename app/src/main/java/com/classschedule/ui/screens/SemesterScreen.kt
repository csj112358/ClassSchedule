package com.classschedule.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.classschedule.data.model.Semester
import com.classschedule.ui.components.*
import com.classschedule.ui.glass.GlassDatePickerDialog
import com.classschedule.ui.glass.GlassDialog
import com.classschedule.ui.glass.GlassDialogBody
import com.classschedule.ui.glass.GlassDialogTitle
import com.classschedule.ui.viewmodel.MainViewModel
import com.classschedule.ui.viewmodel.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val semesters by viewModel.allSemesters.collectAsState()
    val activeSemester by viewModel.activeSemester.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSemester by remember { mutableStateOf<Semester?>(null) }

    GradientBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(text = "学期管理", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(semesters) { semester ->
                    SemesterItem(
                        semester = semester,
                        isActive = semester.id == activeSemester?.id,
                        onSetActive = { viewModel.setActiveSemester(semester.id) },
                        onEdit = { editingSemester = semester },
                        onDelete = { viewModel.deleteSemester(semester) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            GlassButton(
                onClick = { showAddDialog = true },
                text = "添加学期",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showAddDialog) {
        AddSemesterDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, start, end, active ->
                viewModel.addSemester(name, start, end, active)
                showAddDialog = false
            }
        )
    }

    editingSemester?.let { target ->
        AddSemesterDialog(
            title = "编辑学期",
            confirmText = "保存",
            initial = target,
            onDismiss = { editingSemester = null },
            onConfirm = { name, start, end, _ ->
                viewModel.updateSemester(
                    target.copy(name = name, startDate = start, endDate = end)
                )
                editingSemester = null
            }
        )
    }
}

@Composable
private fun SemesterItem(
    semester: Semester,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = semester.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                            Text(text = "当前", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (semester.startDate > 0 && semester.endDate > 0) {
                        "${formatDate(semester.startDate)} 至 ${formatDate(semester.endDate)}"
                    } else {
                        "未设置起止日期"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑日期", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!isActive) {
                    IconButton(onClick = onSetActive) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "设为当前", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteDialog) {
        GlassDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除学期「${semester.name}」吗？该学期下的所有课程也会被删除。") },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSemesterDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Long, Boolean) -> Unit,
    title: String = "添加学期",
    confirmText: String = "确定",
    initial: Semester? = null
) {
    val hasInitialDates = initial != null && initial.startDate > 0
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var setDates by remember { mutableStateOf(hasInitialDates) }
    var startDate by remember {
        mutableStateOf(initial?.startDate?.takeIf { it > 0 } ?: System.currentTimeMillis())
    }
    var endDate by remember {
        mutableStateOf(
            initial?.endDate?.takeIf { it > 0 }
                ?: (initial?.startDate?.takeIf { it > 0 }
                    ?: System.currentTimeMillis()) + 120 * 24 * 60 * 60 * 1000L
        )
    }
    var setAsActive by remember { mutableStateOf(true) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                GlassTextField(value = name, onValueChange = { name = it }, label = "学期名称", placeholder = "如：2024秋季学期")
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = setDates, onCheckedChange = { setDates = it })
                    Text(text = "设置开学/结束日期（用于把第几周对应到真实日期）", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                }
                if (setDates) {
                    Spacer(modifier = Modifier.height(8.dp))
                    GlassButton(onClick = { showStartDatePicker = true }, text = "开学日期: ${formatDate(startDate)}", modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    GlassButton(onClick = { showEndDatePicker = true }, text = "结束日期: ${formatDate(endDate)}", modifier = Modifier.fillMaxWidth())
                }
                if (initial == null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = setAsActive, onCheckedChange = { setAsActive = it })
                        Text(text = "设为当前学期", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val finalStart = if (setDates) startDate else 0L
                        val finalEnd = if (setDates) endDate else 0L
                        onConfirm(name, finalStart, finalEnd, setAsActive)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(confirmText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showStartDatePicker) {
        GlassDatePickerDialog(
            initialDateMillis = startDate,
            onDismissRequest = { showStartDatePicker = false },
            onDateSelected = { startDate = it; showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        GlassDatePickerDialog(
            initialDateMillis = endDate,
            onDismissRequest = { showEndDatePicker = false },
            onDateSelected = { endDate = it; showEndDatePicker = false }
        )
    }
}
