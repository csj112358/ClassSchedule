package com.classschedule.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classschedule.ui.glass.GlassTokens
import com.classschedule.ui.glass.glassContactShadow
import com.classschedule.ui.glass.liquidGlass
import com.classschedule.ui.glass.liquidGlassInset
import com.classschedule.ui.theme.LocalThemeName
import com.classschedule.ui.theme.resolveTheme

/**
 * 玻璃质感组件层。
 *
 * 设计依据（Apple 的材质与深度原则）：
 * 1. 玻璃只做"浮起来的操作层"，不叠玻璃——卡片填充半透明，透出的是根背景那一层；
 * 2. 大面读作更厚：卡片圆角更大、有外阴影；小元素更薄；
 * 3. 顶边一道细高光是"光打在材质上"的关键，比模糊本身更决定像不像玻璃；
 * 4. 浮动导航条让内容从它下面滚过去，而不是占掉一条不透明的带子。
 *
 * 模糊只在 API 31+ 生效（RenderEffect 的限制），低版本自动退回半透明，不影响可读性。
 */

/** 悬浮导航条尺寸（规格：浮动标签栏距屏幕左/右/底 21pt） */
val FloatingBarHeight = 68.dp
val FloatingBarMargin = GlassTokens.floatingBarInset
val FloatingBarBottomGap = 12.dp

/** 导航条占用的视觉高度（含渐隐区，因为底栏上方还压了一层 26dp 的淡出） */
val BottomBarReservedHeight: Dp = FloatingBarHeight + FloatingBarBottomGap + FloatingBarMargin + 26.dp

/** 页面内容的底部内边距：保证最后一项能滚到悬浮导航条上方 */
val bottomBarContentPadding: Dp = BottomBarReservedHeight + 8.dp

private val blurSupported: Boolean
    get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

/** 卡片圆角（同心阶梯的最外层；内层子元素用 [GlassTokens.radiusInner]） */
val GlassCornerRadius = GlassTokens.radiusPanel

/**
 * 玻璃卡片 = **整块玻璃上的一个磨砂区域**，不是独立的玻璃板。
 *
 * 关键取舍（按"整个界面像一块玻璃"的意图）：
 * - **不做投影**：投影会让卡片变成"浮在玻璃上的另一块板"，界面立刻变厚变碎
 * - **不做厚内阴影**：那是"厚玻璃"的做法
 * - **不画任何外框线**：早期版本画了 1px 顶边亮线 + 全周描边，滚动手势下这两条线会被
 *   眼睛抓成"横向分界线"（实测亮度突变 +39），界面显得被一格一格框死
 * - 只用**磨砂区的填充差**表达边界；圆角走同心阶梯：面板 32 − 内边距 16 = 内层 16
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = GlassCornerRadius,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                fill = palette.surface.copy(alpha = 0.55f),
                shape = shape
            )
            .padding(GlassTokens.panelPadding),
        content = content
    )
}

/**
 * 按下反馈：规格要求"0.96 → 1.0 轻微放大 + 阴影增加 + 微小 Y 轴偏移"，
 * 并且动画由**欠阻尼弹簧**驱动（阻尼比 ≈0.7），而不是线性 tween——
 * 线性过渡没有重量感，是"不像苹果"的主要原因之一。
 *
 * 只改 graphicsLayer 的 scale/translationY，不触发重新布局（零测量开销）。
 */
fun Modifier.pressableScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = GlassTokens.PRESS_SCALE,
    pressedOffsetY: Dp = GlassTokens.pressOffsetY
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = GlassTokens.spring(),
        label = "pressScale"
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed) pressedOffsetY else 0.dp,
        animationSpec = GlassTokens.spring(),
        label = "pressOffsetY"
    )
    this
        .scale(scale)
        .offset(y = offsetY)
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(GlassTokens.radiusCapsule)
    ) {
        Text(text = text, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    singleLine: Boolean = true
) {
    // 输入框在玻璃界面里是"凹进去"的：不用 Material 的描边方框（那是安卓味的主要来源），
    // 改用凹陷玻璃材质 + 一条很淡的底线。
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        val shape = RoundedCornerShape(GlassTokens.radiusInner)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassInset(
                    fill = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                    shape = shape
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            fontSize = 15.sp
                        )
                    }
                    inner()
                }
            }
        )
    }
}

/**
 * 屏幕根容器。
 *
 * 背景画在 MainActivity 的最外层（一整块主题底色 + 柔光），这里保持透明，让所有页面共用同一层底，
 * 半透明的玻璃卡片与悬浮导航条透出来的才是同一个东西——叠两层底色会让"玻璃"看起来是脏的。
 */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), content = content)
}

/**
 * 背景极光（Aurora）。
 *
 * 苹果的底不是一块死色，而是**缓慢呼吸的光**——玻璃浮在它上面才有"活"的感觉。
 * 三团异速、异相、异色的光斑各自漂移，周期互不整除，所以整体不会出现可察觉的循环。
 * 用离屏模糊（API 31+）而非实时模糊，静态图层，没有每帧 shader 开销。
 */
@Composable
fun BoxScope.AmbientGlow(primary: Color, tertiary: Color) {
    val transition = rememberInfiniteTransition(label = "aurora")

    // 主光斑：右上，缓慢上下漂 + 微微呼吸
    val driftA by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraA"
    )
    // 次光斑：左下，方向相反、周期不同（17s vs 14s，避免同步）
    val driftB by transition.animateFloat(
        initialValue = 1f, targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(17000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraB"
    )
    // 第三光斑：中右，周期 23s，负责打破规律
    val driftC by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(23000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraC"
    )
    // 整体呼吸：让光的强度也在变，而不是只有位置
    val breath by transition.animateFloat(
        initialValue = 0.82f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraBreath"
    )

    val blurMod = if (blurSupported) Modifier.blur(110.dp, BlurredEdgeTreatment.Unbounded) else Modifier

    // 主光斑
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 90.dp + (driftA * 26).dp, y = (-70 + driftA * 40).dp)
            .size(360.dp)
            .then(blurMod)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = (0.42f * breath).coerceIn(0f, 1f)),
                        Color.Transparent
                    )
                )
            )
    )

    // 次光斑
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .offset(x = (-110 + driftB * 30).dp, y = (40 + driftB * 46).dp)
            .size(330.dp)
            .then(blurMod)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        tertiary.copy(alpha = (0.30f * breath).coerceIn(0f, 1f)),
                        Color.Transparent
                    )
                )
            )
    )

    // 第三光斑：偏中，让画面不空
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .offset(x = 140.dp + (driftC * 22).dp, y = (driftC * 60).dp)
            .size(260.dp)
            .then(blurMod)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = (0.16f * breath).coerceIn(0f, 1f)),
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * 悬浮玻璃导航条。
 *
 * 由 Scaffold 的 bottomBar 槽位承载并叠在内容之上，不占布局高度；
 * 页面用 [bottomBarContentPadding] 预留滚动空间，于是列表会从导航条下面滚过去。
 *
 * ## 为什么不用 backdrop 库的真折射
 * 早期版本底栏走的是库的 `drawBackdrop`（真折射），但：
 * 1. **材质不统一**——折射底栏与磨砂卡片是两种视觉语言，界面割裂；
 * 2. **违反 Apple 规格**——规格明确要求"避免玻璃元素相互堆叠，玻璃无法采样其他玻璃"，
 *    而底栏背后正是飘过玻璃卡片的内容；
 * 3. 库的 `drawBackdrop` 只要出现第二个消费端就触发原生 SIGSEGV（已复现 5 次），
 *    不用它也就没有这个隐患，还少一个第三方依赖。
 *
 * 现在底栏用**同一套材质**，但比内容卡片强一档：它是浮在内容之上的**控件层**，
 * 需要更实的填充（保证图标文字对比度）、更强的顶边高光，以及接触阴影把它抬起来。
 */
@Composable
fun GlassBottomBar(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val themePalette = resolveTheme(LocalThemeName.current)
    val shape = RoundedCornerShape(GlassTokens.radiusCapsule)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = FloatingBarMargin)
                .padding(top = 26.dp)
                .padding(bottom = FloatingBarBottomGap)
                .fillMaxWidth()
                .height(FloatingBarHeight)
                // 控件层：先用接触阴影从内容上"抬起来"，再上玻璃材质
                .glassContactShadow(shape = shape, elevation = 14.dp, alpha = 0.55f)
                .liquidGlass(
                    // 比卡片磨砂更实：底栏上有图标和小字，对比度优先（实测约 12:1）
                    fill = themePalette.barFill.copy(alpha = 0.88f),
                    shape = shape,
                    sheen = 0.09f
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    BottomBarTab(
                        item = item,
                        selected = index == selectedIndex,
                        modifier = Modifier.weight(1f),
                        onClick = { onItemSelected(index) }
                    )
                }
            }
        }

        // 内容滚到导航条附近时不画任何"淡出带"。
        // 早期版本在这里画了一条全宽的渐隐渐变，但胶囊底栏是左右内缩 21pt 的，
        // 渐变带的两端会露在胶囊外面，形成一道明显的横向分界线（实测亮度突变 +108）。
        // 实测结论：**这道带子本身就是那道不好看的横线**，直接去掉。
        // 层次靠胶囊自身的高光/描边/接触阴影表达即可。
    }
}

@Composable
private fun BottomBarTab(
    item: BottomNavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    // 选中态用主题强调色，未选中用纯白——小字在悬浮底栏上最容易糊，不能压透明度，字号也给足
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // 弹簧驱动的选中态过渡（规格：欠阻尼 ζ≈0.7，有重量感而非线性切换）
    val emphasis by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = GlassTokens.spring(),
        label = "tabEmphasis"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = GlassTokens.spring(),
        label = "tabIconScale"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .pressableScale(interactionSource)
            .clip(RoundedCornerShape(GlassTokens.radiusInner))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 选中指示条：随弹簧展开/收起，不是硬切换
        Box(
            modifier = Modifier
                .width(20.dp * emphasis)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = emphasis)
                )
        )
        Spacer(modifier = Modifier.height(5.dp))
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = item.label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector
)
