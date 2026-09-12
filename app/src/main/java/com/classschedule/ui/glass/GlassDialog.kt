package com.classschedule.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * 玻璃对话框。
 *
 * ## 为什么不用 Material3 的 AlertDialog
 * Material 的 AlertDialog 自带不透明 surface 底、胶囊按钮和固定排版，在玻璃界面里
 * 会形成"贴上去的一块安卓对话框"，跟整体割裂。这里用 [Dialog] 从零搭：
 *
 * - 背景：**更高一级的玻璃层**——比卡片稍亮的磨砂 + 与卡片同族的高光/描边
 * - 圆角：走同心阶梯（[GlassTokens.radiusPanel]），与卡片同族
 * - 排版：标题 / 内容 / 操作三段，动作按钮用主题色文字（跟随主题）
 * - 遮罩：65% 暗色 scrim，把背后的玻璃压下去，突出这一层
 *
 * 用法与 AlertDialog 对齐，替换成本低：
 * ```
 * GlassDialog(
 *     onDismissRequest = { ... },
 *     title = { Text("确认删除") },
 *     text = { Text("...") },
 *     confirmButton = { TextButton(onClick = {}) { Text("删除") } },
 *     dismissButton = { TextButton(onClick = {}) { Text("取消") } }
 * )
 * ```
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // 遮罩：点击空白处关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            val shape = RoundedCornerShape(GlassTokens.radiusPanel)
            Column(
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    // 对话框是"更高一层"的玻璃：比卡片磨砂更实一点，保证正文可读
                    .liquidGlass(
                        fill = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shape = shape,
                        sheen = 0.06f
                    )
                    .clickable(
                        // 拦截点击，避免穿透到遮罩把对话框关掉
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                title?.let {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                            content = { it() }
                        )
                    }
                }

                text?.let {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                            content = { it() }
                        )
                    }
                }

                content?.let { it() }

                // 操作区：右对齐，取消在左、确认在右（与 iOS/Android 一致的惯例）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton?.let { it() }
                    confirmButton()
                }
            }
        }
    }
}

/**
 * 玻璃对话框的标题文本（统一排版，避免各处自己写 fontSize）
 */
@Composable
fun GlassDialogTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * 玻璃对话框的正文文本
 */
@Composable
fun GlassDialogBody(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
}

/** 主题色的对话框动作按钮文字 */
@Composable
fun GlassDialogAction(text: String, onClick: () -> Unit, danger: Boolean = false) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 玻璃下拉菜单。
 *
 * Material 的 DropdownMenu 用不透明 surface + 阴影，在玻璃界面里就是"贴上去的安卓菜单"。
 * 这里用 [Popup] 自己搭：玻璃底 + 同心圆角，条目悬停/点按有轻微高亮。
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        val shape = RoundedCornerShape(GlassTokens.radiusPanel)
        Column(
            modifier = modifier
                .widthIn(min = 160.dp, max = 320.dp)
                .liquidGlass(
                    fill = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shape = shape,
                    sheen = 0.05f
                )
                .padding(vertical = 8.dp),
            content = content
        )
    }
}

/**
 * 玻璃下拉菜单的单个条目。
 */
@Composable
fun GlassDropdownItem(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text = text,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassTokens.radiusInner))
            .background(
                if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else Color.Transparent
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}
