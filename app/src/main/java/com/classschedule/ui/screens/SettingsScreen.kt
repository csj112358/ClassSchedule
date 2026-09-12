package com.classschedule.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classschedule.data.model.ApiConfig
import com.classschedule.data.prefs.SettingsStore
import com.classschedule.ui.components.*
import com.classschedule.ui.components.bottomBarContentPadding
import com.classschedule.ui.glass.GlassDialog
import com.classschedule.ui.glass.GlassDialogBody
import com.classschedule.ui.glass.GlassDialogTitle
import com.classschedule.ui.theme.THEMES
import com.classschedule.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToSemester: () -> Unit,
    onNavigateToPeriodConfig: () -> Unit,
    onNavigateToPeriodSharing: () -> Unit = {}
) {
    val currentTheme by viewModel.currentTheme.collectAsState()
    val activeApiConfig by viewModel.activeApiConfig.collectAsState()
    val allApiConfigs by viewModel.allApiConfigs.collectAsState()
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val reminderLeadMinutes by viewModel.reminderLeadMinutes.collectAsState()
    var showApiDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLeadDialog by remember { mutableStateOf(false) }

    // Android 13+ 弹通知需要运行时权限；其它版本直接开启
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setReminderEnabled(granted)
    }
    val requestNotificationPermission = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    GradientBackground {
        LazyColumn(
            // 左右与顶部正常留白，底部滚动区一直延伸到悬浮导航条后面
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            contentPadding = PaddingValues(bottom = bottomBarContentPadding)
        ) {
            item {
                Text(
                    text = "设置",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                SettingsItem(
                    icon = Icons.Default.School,
                    title = "学期管理",
                    subtitle = "创建和管理学期",
                    onClick = onNavigateToSemester
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Schedule,
                    title = "课时配置",
                    subtitle = "设置每天的课程节数和时间",
                    onClick = onNavigateToPeriodConfig
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Share,
                    title = "分享 / 导入课时时间",
                    subtitle = "把课时时间分享给同学，或导入同学分享的课时时间",
                    onClick = onNavigateToPeriodSharing
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                ReminderSettingsItem(
                    enabled = reminderEnabled,
                    leadMinutes = reminderLeadMinutes,
                    onToggle = { checked ->
                        if (checked) {
                            val granted = requestNotificationPermission()
                            if (granted == true) viewModel.setReminderEnabled(true)
                        } else {
                            viewModel.setReminderEnabled(false)
                        }
                    },
                    onEditLead = { showLeadDialog = true }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "主题颜色",
                    subtitle = currentTheme,
                    onClick = { showThemeDialog = true }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "API配置",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = { showApiDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加API",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (allApiConfigs.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "暂无API配置",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击右上角 + 添加API配置（用于AI解析课表）",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(allApiConfigs) { config ->
                    ApiConfigItem(
                        config = config,
                        isActive = config.id == activeApiConfig?.id,
                        onSetActive = { viewModel.setActiveApiConfig(config.id) },
                        onDelete = { viewModel.deleteApiConfig(config) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showApiDialog) {
        ApiConfigDialog(
            onDismiss = { showApiDialog = false },
            onConfirm = { name, apiKey, baseUrl, model ->
                viewModel.addApiConfig(name, apiKey, baseUrl, model, true)
                showApiDialog = false
            }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = {
                viewModel.setTheme(it)
                showThemeDialog = false
            }
        )
    }

    if (showLeadDialog) {
        ReminderLeadDialog(
            initialMinutes = reminderLeadMinutes,
            onDismiss = { showLeadDialog = false },
            onConfirm = { minutes ->
                viewModel.setReminderLeadMinutes(minutes)
                showLeadDialog = false
            }
        )
    }
}

/**
 * 上课提醒设置项：总开关 + 提前时间
 */
@Composable
private fun ReminderSettingsItem(
    enabled: Boolean,
    leadMinutes: Int,
    onToggle: (Boolean) -> Unit,
    onEditLead: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "上课提醒",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (enabled) {
                            "上课前 $leadMinutes 分钟提醒"
                        } else {
                            "关闭中 · 打开后按设定时间提醒"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "提前时间",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    TextButton(onClick = onEditLead) {
                        Text("$leadMinutes 分钟", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "修改提前时间",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "提前时间会显示为通知里的「N 分钟后上课」；若这段时间正好落在上一节课里，会改为下课后立刻提醒。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * 提前时间设置：常用档位一键选择，或用滑杆自定义
 */
@Composable
private fun ReminderLeadDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var customMode by remember { mutableStateOf(initialMinutes !in SettingsStore.LEAD_PRESETS) }
    var sliderValue by remember { mutableFloatStateOf(initialMinutes.toFloat()) }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("提前多久提醒") },
        text = {
            Column {
                SettingsStore.LEAD_PRESETS.forEach { preset ->
                    val selected = !customMode && preset == initialMinutes
                    Surface(
                        onClick = { customMode = false; onConfirm(preset) },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = { customMode = false; onConfirm(preset) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$preset 分钟",
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    onClick = { customMode = true },
                    color = if (customMode) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = customMode, onClick = { customMode = true })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "自定义",
                            color = if (customMode) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                    }
                }

                if (customMode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "上课前 ${sliderValue.toInt()} 分钟提醒",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 5f..120f,
                        steps = 22
                    )
                    Text(
                        text = "范围 5 ~ 120 分钟（按 5 分钟为一档）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (customMode) {
                        val snapped = (Math.round(sliderValue / 5f) * 5).coerceIn(5, 120)
                        onConfirm(snapped)
                    } else {
                        onDismiss()
                    }
                }
            ) { Text(if (customMode) "确定" else "关闭") }
        },
        dismissButton = {
            if (customMode) {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApiConfigItem(
    config: ApiConfig,
    isActive: Boolean,
    onSetActive: () -> Unit,
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
                    Text(
                        text = config.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "当前",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "模型: ${config.modelName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }

            Row {
                if (!isActive) {
                    IconButton(onClick = onSetActive) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "设为当前",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        GlassDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除API配置「${config.name}」吗？") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 预置 API 提供商
 */
private data class ApiPreset(
    val name: String,
    val baseUrl: String,
    val model: String
)

private val apiPresets = listOf(
    ApiPreset("DeepSeek", "https://api.deepseek.com/", "deepseek-chat"),
    ApiPreset("Kimi (Moonshot)", "https://api.moonshot.cn/", "kimi-k3"),
    ApiPreset("自定义", "", "")
)

@Composable
private fun ApiConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var selectedPreset by remember { mutableIntStateOf(0) }
    var apiKey by remember { mutableStateOf("") }
    var customBaseUrl by remember { mutableStateOf("") }
    var customModel by remember { mutableStateOf("") }

    val preset = apiPresets[selectedPreset]
    val isCustom = selectedPreset == apiPresets.lastIndex

    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加API配置") },
        text = {
            Column {
                Text(
                    text = "选择API提供商",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 提供商选择卡片
                apiPresets.forEachIndexed { index, item ->
                    val isSelected = selectedPreset == index
                    Surface(
                        onClick = { selectedPreset = index },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedPreset = index }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = item.name,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (!isCustom || index != apiPresets.lastIndex) {
                                    Text(
                                        text = if (index == apiPresets.lastIndex) "手动输入网址和模型"
                                        else "${item.model}",
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // API Key 输入
                GlassTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "API Key",
                    placeholder = "请输入API Key"
                )

                // 自定义模式显示额外输入框
                if (isCustom) {
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassTextField(
                        value = customBaseUrl,
                        onValueChange = { customBaseUrl = it },
                        label = "API地址",
                        placeholder = "https://api.example.com/"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassTextField(
                        value = customModel,
                        onValueChange = { customModel = it },
                        label = "模型名称",
                        placeholder = "输入模型名，如 deepseek-chat / kimi-k3"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val baseUrl = if (isCustom) customBaseUrl else preset.baseUrl
                    val model = if (isCustom) customModel else preset.model
                    val name = if (isCustom) "自定义" else preset.name
                    if (apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()) {
                        onConfirm(name, apiKey, baseUrl, model)
                    }
                },
                enabled = apiKey.isNotBlank() && (!isCustom || (customBaseUrl.isNotBlank() && customModel.isNotBlank()))
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: String,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    val themes = THEMES

    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择主题") },
        text = {
            Column {
                themes.forEach { palette ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(palette.name) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 色板预览：底色 + 主色 + 次色
                        Row(
                            modifier = Modifier
                                .size(width = 44.dp, height = 22.dp)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(palette.background)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(palette.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(palette.secondary)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = palette.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (palette.name == currentTheme) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "当前主题",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
