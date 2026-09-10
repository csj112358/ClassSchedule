package com.classschedule.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.classschedule.data.parser.ParsedOccurrence
import com.classschedule.data.parser.SemesterJsonParser
import com.classschedule.ui.components.*
import com.classschedule.ui.viewmodel.ImportMode
import com.classschedule.ui.viewmodel.ImportState
import com.classschedule.ui.viewmodel.MainViewModel
import com.classschedule.ui.viewmodel.WeeklyImport
import com.classschedule.ui.viewmodel.getDayName
import com.classschedule.ui.viewmodel.snapshotForWeek
import com.classschedule.ui.viewmodel.weekFromFileName
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecognitionScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val importState by viewModel.importState.collectAsState()
    val importLogs by viewModel.importLogs.collectAsState()
    var jsonText by remember { mutableStateOf("") }
    var importMode by remember { mutableStateOf(ImportMode.WEEK_SNAPSHOT) }
    var targetWeek by remember { mutableIntStateOf(viewModel.currentWeek.value.coerceAtLeast(1)) }
    var showImportConfirm by remember { mutableStateOf(false) }

    // 从 txt 文件读取教务 response（粘贴框放不下大文本时使用）
    val appContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileLoading by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileError by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                fileLoading = true
                fileError = null
                try {
                    val text = withContext(Dispatchers.IO) { readTextFromUri(appContext, uri) }
                    fileName = withContext(Dispatchers.IO) { queryFileName(appContext, uri) }
                    if (text.isBlank()) {
                        fileError = "文件内容为空，请检查 txt 是否保存成功。"
                    } else {
                        jsonText = text
                        val s = importState
                        if (s is ImportState.Success || s is ImportState.Done || s is ImportState.Error) {
                            viewModel.resetImportState()
                        }
                    }
                } catch (e: Exception) {
                    fileError = "读取文件失败：${e.message}"
                } finally {
                    fileLoading = false
                }
            }
        }
    }

    // 批量按周导入：文件名即周数，一次多选多个 txt
    var batchLoading by remember { mutableStateOf(false) }
    var batchItems by remember { mutableStateOf<List<WeeklyImport>>(emptyList()) }
    var batchFailed by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showBatchConfirm by remember { mutableStateOf(false) }
    val batchPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                batchLoading = true
                batchItems = emptyList()
                batchFailed = emptyList()
                val items = mutableListOf<WeeklyImport>()
                val failed = mutableListOf<Pair<String, String>>()
                for (uri in uris) {
                    try {
                        val text = withContext(Dispatchers.IO) { readTextFromUri(appContext, uri) }
                        val name = withContext(Dispatchers.IO) { queryFileName(appContext, uri) }
                        val week = weekFromFileName(name)
                        if (week == null) {
                            failed.add(name to "文件名里没有周数，请改成 3 或 第3周 之类")
                            continue
                        }
                        if (text.isBlank()) {
                            failed.add(name to "文件内容为空")
                            continue
                        }
                        val result = withContext(Dispatchers.Default) { SemesterJsonParser.parse(text) }
                        val snapshot = snapshotForWeek(result.occurrences, week)
                        if (snapshot.isEmpty()) {
                            failed.add(name to "第${week}周没有会上课的课程（周次不含该周）")
                            continue
                        }
                        items.add(WeeklyImport(week, name, snapshot, result.sectionTimes))
                    } catch (e: Exception) {
                        val name = runCatching { withContext(Dispatchers.IO) { queryFileName(appContext, uri) } }
                            .getOrNull() ?: uri.toString()
                        failed.add(name to (e.message ?: "解析失败"))
                    }
                }
                batchItems = items.sortedBy { it.week }
                batchFailed = failed
                batchLoading = false
            }
        }
    }

    // 实际将写入的课次：整学期 = 全部解析结果；第N周 = 折叠为该周的快照
    val displayOccurrences: List<ParsedOccurrence> =
        when (val s = importState) {
            is ImportState.Success -> if (importMode == ImportMode.WEEK_SNAPSHOT) {
                snapshotForWeek(s.preview.occurrences, targetWeek)
            } else s.preview.occurrences
            else -> emptyList()
        }

    val displayDistinct = displayOccurrences.map { it.name }.distinct().size
    val maxWeek = viewModel.semesterWeekCount()
    val weekDateRange = viewModel.getWeekDateRange(targetWeek)

    val weekTag = if (weekDateRange.isNotEmpty()) "第$targetWeek 周（$weekDateRange）" else "第 $targetWeek 周"
    val countText = "$displayDistinct 门课程，共 ${displayOccurrences.size} 条课次"
    val summaryText = when (importState) {
        is ImportState.Success -> if (importMode == ImportMode.WEEK_SNAPSHOT) "$weekTag · $countText"
        else "$countText，覆盖到第${(importState as ImportState.Success).preview.occurrences.maxOf { it.weekEnd }}周"
        else -> ""
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
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(text = "导入课表", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 导入方式
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "导入方式", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeButton(
                            text = "第N周导入",
                            selected = importMode == ImportMode.WEEK_SNAPSHOT,
                            modifier = Modifier.weight(1f)
                        ) { importMode = ImportMode.WEEK_SNAPSHOT }
                        ModeButton(
                            text = "整学期导入",
                            selected = importMode == ImportMode.WHOLE_SEMESTER,
                            modifier = Modifier.weight(1f)
                        ) { importMode = ImportMode.WHOLE_SEMESTER }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (importMode == ImportMode.WEEK_SNAPSHOT) {
                        Text(
                            text = "教务里先选好第几周再导出，这里指定要写入的第N周；每次只覆盖第 N 周，其它周保持不变。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        WeekStepSelector(
                            week = targetWeek,
                            maxWeek = maxWeek,
                            onChange = { targetWeek = it.coerceIn(1, maxWeek) }
                        )
                    } else {
                        Text(
                            text = "粘贴整学期课表（含各课程完整周次），一次清空当前课表并写入整个学期。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (importMode == ImportMode.WEEK_SNAPSHOT) {
                Spacer(modifier = Modifier.height(16.dp))

                // 批量按周导入：文件名 = 周数，一次多选多个 txt
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "批量按周导入（文件名＝周数）",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "把每周课表 response 存成 .txt，文件名改成周数（如 3.txt 或 第3周.txt），可一次多选导入多个周。本地解析、离线免费。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        GlassButton(
                            onClick = { batchPicker.launch("*/*") },
                            text = if (batchLoading) "读取中…" else "选择多个 txt 文件",
                            enabled = !batchLoading,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "提示：长按文件即可勾选多个；部分机型进入后需点右上角「多选」或长按进入勾选模式。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )

                        if (batchItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            batchItems.forEach { item ->
                                Text(
                                    text = "第${item.week}周（${item.fileName}）· ${item.occurrences.map { it.name }.distinct().size} 门 / ${item.occurrences.size} 条课次",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                        if (batchFailed.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            batchFailed.forEach { (n, e) ->
                                Text(
                                    text = "⚠ $n：$e",
                                    color = Color(0xFFF59E0B),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                        if (batchItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            GlassButton(
                                onClick = { showBatchConfirm = true },
                                text = "导入 ${batchItems.map { it.week }.distinct().size} 个周（仅覆盖这些周）",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 粘贴 / 读取 txt 文件
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "教务课表 JSON",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !fileLoading) {
                            if (fileLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(text = if (fileLoading) "读取中…" else "从 txt 文件导入", fontSize = 13.sp)
                        }
                    }
                    fileName?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "已读取：$it", color = Color(0xFF10B981), fontSize = 12.sp)
                    }
                    fileError?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = it, color = Color(0xFFF59E0B), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (importMode == ImportMode.WEEK_SNAPSHOT)
                            "把教务系统里【第 $targetWeek 周】的课表 response 存成 .txt 用右上角导入；短文本也可直接粘贴到下面。"
                        else
                            "把整学期课表 response 存成 .txt 用右上角导入；短文本也可直接粘贴到下面。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = {
                            jsonText = it
                            val s = importState
                            if (s is ImportState.Success || s is ImportState.Done || s is ImportState.Error) {
                                viewModel.resetImportState()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        placeholder = { Text("{ ... 粘贴教务课表 JSON ... }") },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        singleLine = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            jsonText = ""
                            fileName = null
                            fileError = null
                            viewModel.resetImportState()
                        }) {
                            Text("清空", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassButton(
                onClick = {
                    viewModel.parseScheduleJson(jsonText)
                },
                text = "开始解析",
                modifier = Modifier.fillMaxWidth(),
                enabled = jsonText.isNotBlank() && importState !is ImportState.Loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = importState) {
                is ImportState.Loading -> {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = state.message, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                is ImportState.Success -> {
                    if (displayOccurrences.isEmpty()) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (importMode == ImportMode.WEEK_SNAPSHOT)
                                    "第 $targetWeek 周没有会上课的课程（解析出的课程周次不包含该周）。请核对周次或改用整学期导入。"
                                else
                                    "没有解析出课程，请检查粘贴内容。",
                                color = Color(0xFFF59E0B),
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        ParsePreviewCard(
                            occurrences = displayOccurrences,
                            mode = importMode,
                            week = if (importMode == ImportMode.WEEK_SNAPSHOT) targetWeek else null
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    GlassButton(
                        onClick = { showImportConfirm = true },
                        text = if (importMode == ImportMode.WEEK_SNAPSHOT) "导入到第 $targetWeek 周（仅覆盖该周）"
                        else "导入课表（将清空并覆盖整个学期）",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = displayOccurrences.isNotEmpty()
                    )
                }
                is ImportState.Done -> {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = state.message, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                is ImportState.Error -> {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = state.message, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                is ImportState.Idle -> {}
            }

            // 实时日志
            if (importLogs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                val context = LocalContext.current
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "日志", color = Color(0xFF89B4FA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row {
                                TextButton(
                                    onClick = {
                                        val text = importLogs.joinToString("\n")
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("logs", text))
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("复制全部", fontSize = 11.sp, color = Color(0xFF89B4FA))
                                }
                                TextButton(onClick = { viewModel.clearLogs() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                    Text("清除", fontSize = 11.sp)
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF313244)))
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(importLogs) { log ->
                                SelectionContainer {
                                    Text(
                                        text = log,
                                        color = if (log.contains("ERROR")) Color(0xFFF38BA8) else Color(0xFFA6E3A1),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("确认导入") },
            text = {
                Text(
                    text = if (importMode == ImportMode.WEEK_SNAPSHOT)
                        "将只覆盖第 $targetWeek 周已导入的课程（其它周不受影响）。\n\n本次将写入：$countText\n\n是否继续？"
                    else
                        "导入将清空当前课表并写入本次解析结果。\n\n本次共 $summaryText\n\n是否继续？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    viewModel.importSchedule(mode = importMode, week = targetWeek)
                }) { Text("清空并导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showBatchConfirm) {
        val weeks = batchItems.map { it.week }.distinct().sorted()
        val totalRows = batchItems.sumOf { it.occurrences.size }
        val totalCourses = batchItems.flatMap { it.occurrences }.map { it.name }.distinct().size
        AlertDialog(
            onDismissRequest = { showBatchConfirm = false },
            title = { Text("确认批量导入") },
            text = {
                Text(
                    "将只覆盖第${weeks.joinToString("、")}周已导入的课程（其它周不受影响）。\n\n" +
                        "共 ${weeks.size} 个周、$totalCourses 门课程、$totalRows 条课次。\n\n是否继续？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBatchConfirm = false
                    viewModel.importWeeklySnapshots(batchItems)
                }) { Text("批量导入") }
            },
            dismissButton = {
                TextButton(onClick = { showBatchConfirm = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 解析预览（按逻辑课目合并，避免同一门课多条重复展示）
 */
@Composable
private fun ParsePreviewCard(
    occurrences: List<ParsedOccurrence>,
    mode: ImportMode,
    week: Int? = null
) {
    val grouped = remember(occurrences) {
        occurrences.groupBy { o ->
            "${o.name}|${o.teacher}|${o.room}|${o.dayOfWeek}|${o.startPeriod}-${o.endPeriod}"
        }.toList()
    }
    val distinctNames = occurrences.map { it.name }.distinct().size

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (mode == ImportMode.WEEK_SNAPSHOT)
                        "第${week}周 · $distinctNames 门课目 · ${occurrences.size} 条课次"
                    else
                        "${distinctNames} 门课目 · ${occurrences.size} 条课次 · 覆盖到第${occurrences.maxOf { it.weekEnd }}周",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            grouped.forEach { (_, items) ->
                val first = items.first()
                val sorted = items.sortedBy { it.weekStart }
                val weeksText = sorted.map { item ->
                    val span = if (item.weekStart == item.weekEnd) "${item.weekStart}周"
                    else "${item.weekStart}-${item.weekEnd}周"
                    val p = SemesterJsonParser.parityText(item.weekParity)
                    if (p.isEmpty()) span else "$span（$p）"
                }.distinct().joinToString("、")
                Text(
                    text = "${first.name}  ·  ${getDayName(first.dayOfWeek)} 第${first.startPeriod}-${first.endPeriod}节",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = weeksText + if (first.room.isNotBlank()) "  ·  ${first.room}" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val content = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    ) {
        Text(text, fontSize = 13.sp)
    }
}

/**
 * 第几周选择器：上一步/下一步 + 快速下拉跳到指定周
 */
@Composable
private fun WeekStepSelector(
    week: Int,
    maxWeek: Int,
    onChange: (Int) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onChange(week - 1) }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一周", tint = MaterialTheme.colorScheme.onSurface)
        }

        Box {
            Surface(
                onClick = { menuExpanded = true },
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "第 $week 周",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                (1..maxWeek).forEach { w ->
                    DropdownMenuItem(
                        text = { Text("第 $w 周") },
                        onClick = { onChange(w); menuExpanded = false }
                    )
                }
            }
        }

        IconButton(onClick = { onChange(week + 1) }) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一周", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * 读取 txt / 任意文本文件内容。
 * 优先按 UTF-8 严格解码；失败（如 Windows 记事本保存的 GBK）则回退 GB18030，再兜底 UTF-8。
 */
private fun readTextFromUri(context: Context, uri: Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return ""
    val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    ) 3 else 0 // 跳过 UTF-8 BOM
    val body = bytes.copyOfRange(start, bytes.size)
    val utf8Decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        utf8Decoder.decode(ByteBuffer.wrap(body)).toString()
    } catch (e: CharacterCodingException) {
        try {
            Charset.forName("GB18030").decode(ByteBuffer.wrap(body)).toString()
        } catch (e2: Exception) {
            String(body, Charsets.UTF_8)
        }
    }
}

/** 读取所选文件的显示名，用于提示已读取哪个文件 */
private fun queryFileName(context: Context, uri: Uri): String {
    var name: String? = null
    context.contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = cursor.getString(idx)
        }
    }
    return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
}
