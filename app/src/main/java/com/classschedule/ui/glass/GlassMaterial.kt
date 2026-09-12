package com.classschedule.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 玻璃材质（Apple 风格）。
 *
 * ## 设计意图：**整个界面是一块玻璃**，而不是"一堆玻璃卡片"
 *
 * 这是本项目最关键的一条设计决定。早期版本给每张卡片都加了投影 + 强内阴影 + 厚度渐变，
 * 结果每张卡都变成一块**独立的厚玻璃板**，界面又厚又碎。正确的心智模型是：
 *
 * - **屏幕本身 = 一块玻璃**（背景极光透过来 → 局部柔光 → 全局高光扫过）
 * - **卡片 = 玻璃上一个"磨砂区域"**，不是另一块玻璃
 *   → 所以卡片**没有投影**、**没有厚内阴影**，只有不透明度差 + 一道极细的顶边亮线
 * - **内容层严禁用折射**（Apple 规格明确禁止），保证文字可读
 *
 * 层次靠"存在感递减"表达：基底玻璃 > 磨砂卡片 > 内容文字。
 */
object GlassMaterial {

    /** 默认高光锐度档位：MEDIUM 匹配 iOS 26 */
    val DEFAULT_SPECULAR = GlassTokens.Specular.MEDIUM

    /**
     * 磨砂区域相对玻璃的"抬升"——**刻意做得很轻**。
     * 一块玻璃上的磨砂区只比周围亮一点点，这才像"同一块玻璃上的处理"。
     */
    const val FROST_ALPHA = 0.05f

    /** 顶边亮线：玻璃被切割/磨砂的细边，1px 即可 */
    const val HAIRLINE_ALPHA = 0.10f

    /**
     * 内阴影：0 = 关闭。
     * 只有需要"凹陷"语义的元素（如输入框）才打开，普通卡片保持 0。
     */
    const val INNER_SHADOW_ALPHA = 0.0f

    /** 环境的对角柔光强度——整块玻璃上的天光，而非每张卡自己的高光 */
    const val SHEEN_ALPHA = 0.045f
}

/**
 * 玻璃上的**磨砂区域**（卡片、面板、列表容器）。
 *
 * 关键：这**不是**一块独立的玻璃板。它只做三件事，都很轻：
 * 1. 比周围玻璃亮一点点的填充（[GlassMaterial.FROST_ALPHA]）
 * 2. 一道 1px 顶边亮线（被磨砂的边缘）
 * 3. 一道 1px 描边（玻璃的切割边）
 *
 * **没有投影、没有厚内阴影**——那是"独立玻璃板"的做法，会让界面变厚变碎。
 * 需要"凹陷"语义的元素（输入框）才通过 [innerShadow] 打开内阴影。
 *
 * @param fill 磨砂区底色（通常传主题 surface，接近不透明以保证文字可读）
 * @param shape 形状，走同心阶梯
 * @param sheen 对角柔光强度；0 = 完全平面（用于极简区域）
 * @param innerShadow 内阴影强度，默认 0（卡片不需要凹陷感）
 */
fun Modifier.liquidGlass(
    fill: Color,
    shape: RoundedCornerShape,
    sheen: Float = GlassMaterial.SHEEN_ALPHA,
    innerShadow: Float = GlassMaterial.INNER_SHADOW_ALPHA
): Modifier = this
    .clip(shape)
    .background(fill)
    .drawWithContent {
        val radius = CornerRadius(shape.topStart.toPx(size, this), shape.topStart.toPx(size, this))
        drawContent()

        // ── 弱对角柔光：只留一点"光从左上过来"的暗示，不做厚度
        if (sheen > 0f) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = sheen),
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width * 0.85f, size.height * 0.85f)
                ),
                cornerRadius = radius
            )
        }

        // ── 内阴影（仅凹陷语义元素使用）
        if (innerShadow > 0f) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = innerShadow),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.5f
                ),
                cornerRadius = radius
            )
        }

        // ── 刻意不画外框线 ──
        // 早期版本在卡片上画了 1px 顶边亮线 + 1px 全周描边，滚动手势下这两条线会被眼睛抓成
        // "横向分界线"，界面显得被一格一格框死。既然整体是一块玻璃，卡片的边界就只应该由
        // **磨砂区的填充差**来表达，不加任何线条。
    }

/**
 * 整块玻璃的**全局高光带**。
 *
 * 这是"整个界面像一块玻璃"的关键一环：一道很淡的对角亮带横跨**整个屏幕**，
 * 从所有元素之上扫过。它不跟着任何卡片走，只属于"这块大玻璃"本身。
 *
 * 用法：放在根容器的最上层（内容之上、底栏之下）。
 */
fun Modifier.globalGlassSheen(): Modifier = this.drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.030f),
                Color.Transparent,
                Color.White.copy(alpha = 0.016f),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width * 1.1f, size.height * 0.75f)
        )
    )
}

/**
 * 凹陷表面（输入框、搜索框）。
 *
 * 与卡片相反：这类元素在玻璃上表现为**凹进去**，所以用内阴影 + 更暗的填充。
 */
fun Modifier.liquidGlassInset(
    fill: Color,
    shape: RoundedCornerShape,
    depth: Float = 0.10f
): Modifier = this
    .clip(shape)
    .background(fill)
    .drawWithContent {
        val radius = CornerRadius(shape.topStart.toPx(size, this), shape.topStart.toPx(size, this))
        drawContent()
        // 顶部内阴影：光被凹槽挡住
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = depth),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.65f
            ),
            cornerRadius = radius
        )
        // 底部一道反光：凹槽的下唇接光
        drawLine(
            color = Color.White.copy(alpha = 0.07f),
            start = Offset(radius.x, size.height - 0.5.dp.toPx()),
            end = Offset(size.width - radius.x, size.height - 0.5.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.06f),
            cornerRadius = radius,
            style = Stroke(width = 1.dp.toPx())
        )
    }

/**
 * 接触阴影：把玻璃从背景上"抬起来"。
 *
 * 用 Compose 的 [Modifier.shadow]（原生绘制）而不是自己叠多层偏移矩形——
 * 后者会留下肉眼可见的色阶条纹，是不专业的实现。
 * 注意必须画在玻璃**外层**（clip 之前），且要求元素有不透明背景才能看到效果。
 */
fun Modifier.glassContactShadow(
    shape: RoundedCornerShape,
    elevation: Dp = 12.dp,
    alpha: Float = 0.5f
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = alpha),
    spotColor = Color.Black.copy(alpha = alpha)
)
