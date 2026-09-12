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
import com.classschedule.data.model.PeriodConfig
import com.classschedule.ui.components.*
import com.classschedule.ui.glass.GlassDialog
import com.classschedule.ui.glass.GlassDialogBody
import com.classschedule.ui.glass.GlassDialogTitle
import com.classschedule.ui.viewmodel.MainViewModel

@Composable
fun PeriodConfigScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val periodConfigs by viewModel.periodConfigs.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<PeriodConfig?>(null) }

    GradientBackground {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(text = "课时配置", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(text = "配置每天的课程节数和上下课时间，全局生效", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(periodConfigs.sortedBy { it.period }) { config ->
                    PeriodConfigItem(config = config, onEdit = { editingConfig = config }, onDelete = { viewModel.deletePeriodConfig(config) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            GlassButton(onClick = { showAddDialog = true }, text = "添加课时", modifier = Modifier.fillMaxWidth())
        }
    }

    if (showAddDialog) {
        PeriodEditDialog(
            title = "添加课时",
            initialPeriod = (periodConfigs.maxOfOrNull { it.period } ?: 0) + 1,
            onDismiss = { showAddDialog = false },
            onConfirm = { period, start, end, label ->
                viewModel.addPeriodConfig(PeriodConfig(period = period, startTime = start, endTime = end, label = label))
                showAddDialog = false
            }
        )
    }

    editingConfig?.let { config ->
        PeriodEditDialog(
            title = "编辑课时",
            initialPeriod = config.period,
            initialStart = config.startTime,
            initialEnd = config.endTime,
            initialLabel = config.label,
            onDismiss = { editingConfig = null },
            onConfirm = { period, start, end, label ->
                viewModel.updatePeriodConfig(config.copy(period = period, startTime = start, endTime = end, label = label))
                editingConfig = null
            }
        )
    }
}

@Composable
private fun PeriodConfigItem(config: PeriodConfig, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "第 ${config.period} 节", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    if (config.label.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(text = config.label, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${config.startTime} - ${config.endTime}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }

            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (showDeleteDialog) {
        GlassDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除第 ${config.period} 节吗？") },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun PeriodEditDialog(
    title: String,
    initialPeriod: Int,
    initialStart: String = "08:00",
    initialEnd: String = "08:45",
    initialLabel: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Int, String, String, String) -> Unit
) {
    var period by remember { mutableStateOf(initialPeriod.toString()) }
    var startTime by remember { mutableStateOf(initialStart) }
    var endTime by remember { mutableStateOf(initialEnd) }
    var label by remember { mutableStateOf(initialLabel) }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                GlassTextField(value = period, onValueChange = { period = it.filter { c -> c.isDigit() } }, label = "第几节", placeholder = "如：1")
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GlassTextField(value = startTime, onValueChange = { startTime = it }, label = "开始时间", placeholder = "HH:mm", modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassTextField(value = endTime, onValueChange = { endTime = it }, label = "结束时间", placeholder = "HH:mm", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                GlassTextField(value = label, onValueChange = { label = it }, label = "标签（选填）", placeholder = "如：上午第一节")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { val p = period.toIntOrNull() ?: initialPeriod; if (p > 0 && startTime.isNotBlank() && endTime.isNotBlank()) onConfirm(p, startTime, endTime, label) },
                enabled = period.isNotBlank() && startTime.isNotBlank() && endTime.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
