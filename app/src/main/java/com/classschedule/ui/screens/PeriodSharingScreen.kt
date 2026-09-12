package com.classschedule.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classschedule.data.share.PeriodSharing
import com.classschedule.ui.components.*
import com.classschedule.ui.glass.GlassDialog
import com.classschedule.ui.glass.GlassDialogBody
import com.classschedule.ui.glass.GlassDialogTitle
import com.classschedule.ui.viewmodel.MainViewModel
import com.classschedule.ui.viewmodel.PeriodImportState
import com.classschedule.util.FileTextReader
import com.classschedule.util.TextFileSharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 分享 / 导入课时时间。
 *
 * 分享出去的文件只有「第几节 ↔ 上下课时间」这一层信息，不含课程、教室等个人课表内容；
 * 同校同学导入后就不用再手动逐节编辑时间了。
 */
@Composable
fun PeriodSharingScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val periodConfigs by viewModel.periodConfigs.collectAsState()
    val importState by viewModel.periodImportState.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pasteText by remember { mutableStateOf("") }
    var fileLoading by remember { mutableStateOf(false) }
    var sourceLabel by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showApplyConfirm by remember { mutableStateOf(false) }

    val sortedPeriods = periodConfigs.sortedBy { it.period }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                fileLoading = true
                message = null
                try {
                    val text = withContext(Dispatchers.IO) { FileTextReader.readText(context, uri) }
                    val name = withContext(Dispatchers.IO) { FileTextReader.queryFileName(context, uri) }
                    if (text.isBlank()) {
                        message = "文件内容为空，请确认是否选错了文件。"
                    } else {
                        sourceLabel = name
                        pasteText = text.take(2000)
                        viewModel.parsePeriodText(text, sourceLabel = name)
                    }
                } catch (e: Exception) {
                    message = "读取文件失败：${e.message}"
                } finally {
                    fileLoading = false
                }
            }
        }
    }

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    text = "分享 / 导入课时时间",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "只分享每节课的上下课时间，不含课程名、教室等个人课表内容。同学导入后无需再手动编辑课时。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 分享 =====
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "分享我的课时时间",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (sortedPeriods.isEmpty()) {
                            "还没有课时配置，先去「课时配置」里添加节次时间。"
                        } else {
                            "共 ${sortedPeriods.size} 个节次（第${sortedPeriods.first().period}节 ~ 第${sortedPeriods.last().period}节）"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    sortedPeriods.forEach { config ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "第 ${config.period} 节",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                modifier = Modifier.width(64.dp)
                            )
                            Text(
                                text = "${config.startTime} - ${config.endTime}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                            if (config.label.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = config.label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    GlassButton(
                        onClick = {
                            scope.launch {
                                val text = viewModel.buildPeriodShareText()
                                val ok = TextFileSharer.shareText(
                                    context = context,
                                    fileName = PeriodSharing.suggestedFileName(),
                                    content = text
                                )
                                if (!ok) {
                                    message = "没有找到可用的分享方式，可改用「复制分享文本」。"
                                }
                            }
                        },
                        text = "分享为 txt 文件",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = sortedPeriods.isNotEmpty()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                copyToClipboard(context, "课时时间", viewModel.currentPeriodShareText())
                                message = "已复制分享文本，可直接发给同学"
                            },
                            enabled = sortedPeriods.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("复制分享文本", fontSize = 13.sp)
                        }
                        TextButton(
                            onClick = {
                                val text = viewModel.currentPeriodShareText()
                                pasteText = text.take(2000)
                                sourceLabel = "本机课时时间（自己预览）"
                                viewModel.parsePeriodText(text, sourceLabel = "本机课时时间（自己预览）")
                            },
                            enabled = sortedPeriods.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("预览文件内容", fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 导入 =====
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "导入同学的课时时间",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "选择同学分享的 .txt 文件，或把内容粘贴到下面。导入会整份替换当前课时配置。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        text = if (fileLoading) "读取中…" else "选择课时时间 txt 文件",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !fileLoading
                    )

                    sourceLabel?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "已读取：$it", color = Color(0xFF10B981), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = {
                            pasteText = it
                            viewModel.resetPeriodImportState()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        placeholder = {
                            Text(
                                "${PeriodSharing.MAGIC}\n1,08:00,08:45,上午第一节\n2,08:55,09:40,上午第二节",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        singleLine = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            pasteText = ""
                            sourceLabel = null
                            viewModel.resetPeriodImportState()
                        }) {
                            Text("清空", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { viewModel.parsePeriodText(pasteText, sourceLabel = "粘贴内容") },
                            enabled = pasteText.isNotBlank() && importState !is PeriodImportState.Parsing
                        ) {
                            Text("解析", fontSize = 13.sp)
                        }
                    }
                }
            }

            message?.let {
                Spacer(modifier = Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = it, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = importState) {
                is PeriodImportState.Idle -> {}
                is PeriodImportState.Parsing -> {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "正在解析…", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                is PeriodImportState.Parsed -> {
                    PeriodImportPreviewCard(
                        configs = state.configs,
                        warnings = state.warnings,
                        sourceLabel = state.sourceLabel,
                        currentCount = sortedPeriods.size
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassButton(
                        onClick = { showApplyConfirm = true },
                        text = "用这份课时时间覆盖当前配置",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is PeriodImportState.Done -> {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "导入完成：已写入 ${state.count} 个节次",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                is PeriodImportState.Error -> {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = state.message, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showApplyConfirm) {
        val parsed = importState as? PeriodImportState.Parsed
        GlassDialog(
            onDismissRequest = { showApplyConfirm = false },
            title = { Text("确认导入") },
            text = {
                Text(
                    "将删除当前 ${sortedPeriods.size} 个节次的课时时间，改用这份 ${parsed?.configs?.size ?: 0} 个节次的配置。\n\n" +
                        "课表里的课程不会被删除，但节次对应的上课时间会随之变化。\n\n是否继续？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showApplyConfirm = false
                    viewModel.applyParsedPeriods()
                }) { Text("覆盖导入") }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PeriodImportPreviewCard(
    configs: List<com.classschedule.data.model.PeriodConfig>,
    warnings: List<String>,
    sourceLabel: String,
    currentCount: Int
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "解析出 ${configs.size} 个节次",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (sourceLabel.isNotEmpty()) {
                Text(
                    text = "来源：$sourceLabel" + if (currentCount > 0) "（当前有 $currentCount 个节次，导入后整份替换）" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            configs.forEach { config ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "第 ${config.period} 节",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.width(64.dp)
                    )
                    Text(
                        text = "${config.startTime} - ${config.endTime}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                    if (config.label.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = config.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            warnings.forEach { warning ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "⚠ $warning", color = Color(0xFFF59E0B), fontSize = 12.sp)
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
