package com.classschedule.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃设计代币（Design Tokens）。
 *
 * 依据 Apple Liquid Glass 规格落地，本文件是**唯一**的参数来源，
 * 各组件不再自己写魔法数字——这是"一致性（Consistency）"与"和谐（Harmony）"的前提。
 */
object GlassTokens {

    // ─────────────────────────────────────────────────────────────
    // 几何：同心圆角（Concentric Corner Radii）
    //
    // 规格：嵌套的玻璃元素与容器圆角保持**几何同心**。
    // 公式：内圆角半径 = 外圆角半径 − 容器内边距
    //
    // 本 App 的嵌套链（每层内边距 16dp）：
    //   玻璃面板 32 = 屏幕 44 − 12
    //     内层控件 16 = 32 − 16
    //       最内元素 8 = 16 − 8
    // 阶梯之间相差 16dp，正好等于卡片内边距，做到"同心"。
    // ─────────────────────────────────────────────────────────────

    /** 最外层玻璃容器（卡片、面板、底栏）。半径 32 − 16 = 16 给内层。 */
    val radiusPanel = 32.dp

    /** 容器内一等子元素。32 − 16 = 16，与外层同心。 */
    val radiusInner = 16.dp

    /** 极小的内嵌块（色板、进度条等）。16 − 8 = 8。 */
    val radiusSmall = 8.dp

    /** 胶囊（按钮、标签、浮动底栏）：内部小控件的同心值 22 − 6 = 16。 */
    val radiusCapsule = 32.dp

    // ─────────────────────────────────────────────────────────────
    // 光学参数（用于 backdrop 库的真折射，仅底栏可用）
    //
    // 规格默认值：refraction 0.69 / chromatic 0.05 / blurSigma 20-25 / tint 0.2-0.3
    // 说明：Compose 1.7.2 无法把背景作为纹理喂给自绘 shader，
    //       因此色差与自定义折射只能落在 backdrop 库这一条路径上。
    // ─────────────────────────────────────────────────────────────

    /** 背景高斯模糊：规格 20–25，取中值 */
    val blurSigma = 22.dp

    /** 折射强度：规格 0.69，换算到 lens 的位移半径 */
    val refractionRadius = 20.dp

    /** 折射作用范围（从边缘向内衰减的距离） */
    val refractionExtent = 44.dp

    /** 玻璃色调透明度：规格 0.2–0.3 */
    const val TINT_REGULAR = 0.26f

    /** 清澈变体色调：规格约 0.03，需配合 35% 暗色遮罩保证可读性 */
    const val TINT_CLEAR = 0.03f

    /** 清澈变体必须在亮内容上叠加的暗色遮罩 */
    const val CLEAR_SCRIM_ALPHA = 0.35f

    // ─────────────────────────────────────────────────────────────
    // 高光锐度三档
    // ─────────────────────────────────────────────────────────────

    enum class Specular { SOFT, MEDIUM, SHARP }

    /** 返回 (顶边高光透明度, 高光衰减高度比例) */
    fun specular(sharpness: Specular): Pair<Float, Float> = when (sharpness) {
        Specular.SOFT -> 0.10f to 0.45f      // 磨砂感：铺得宽、亮度低
        Specular.MEDIUM -> 0.14f to 0.28f    // 默认：匹配 iOS 26
        Specular.SHARP -> 0.20f to 0.12f     // 镜面感：贴边、锐
    }

    // ─────────────────────────────────────────────────────────────
    // 动画：时长（规格：160 / 200-300 / 450ms）
    // ─────────────────────────────────────────────────────────────

    /** 快速微交互（菜单展开、按下反馈）：160ms */
    const val DURATION_QUICK = 160

    /** 标准过渡：280ms（iOS 风格预设） */
    const val DURATION_STANDARD = 280

    /** 缓慢优雅的过渡：450ms */
    const val DURATION_SLOW = 450

    // ─────────────────────────────────────────────────────────────
    // 动画：弹簧物理
    //
    // 规格：阻尼比 ζ ≈ 0.7~0.73（欠阻尼，有回弹），刚度 100
    // Compose 的 SpringSpec 用 (dampingRatio, stiffness)：
    //   dampingRatio=0.7 → 略带回弹，符合"液态"
    //   stiffness=100（低刚度）→ 有重量感；高刚度=320 会太硬
    // ─────────────────────────────────────────────────────────────

    /** 通用弹簧：用于尺寸、位移等物理响应 */
    fun <T> spring(): SpringSpec<T> = spring(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessLow
    )

    /** 交互回弹（形变、融合类）——更明显的回弹，用于按下/释放 */
    fun <T> springBouncy(): SpringSpec<T> = spring(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 沉稳过渡（大面板进出）——接近临界阻尼，不回弹 */
    fun <T> springSmooth(): SpringSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessLow
    )

    // ─────────────────────────────────────────────────────────────
    // 交互反馈（规格：0.96 → 1.0 轻微放大 + 阴影增加 + 微小 Y 偏移）
    // ─────────────────────────────────────────────────────────────

    /** 按下时的缩放：0.96 */
    const val PRESS_SCALE = 0.96f

    /** 按下时的 Y 偏移（dp）——轻微下压 */
    val pressOffsetY = 1.dp

    /** 按下时阴影增量（dp） */
    val pressElevationDelta = 4.dp

    // ─────────────────────────────────────────────────────────────
    // 布局
    // ─────────────────────────────────────────────────────────────

    /** 最小触控区：规格 44×44pt */
    val minTouchTarget = 44.dp

    /** 浮动工具栏（标签栏）距屏幕左/右/底 21pt */
    val floatingBarInset = 21.dp

    /** 玻璃面板标准内边距（决定了同心圆角的阶梯） */
    val panelPadding = 16.dp
}
