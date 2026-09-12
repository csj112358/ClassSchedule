package com.classschedule.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.classschedule.ui.glass.GlassTokens

/**
 * 一套主题配色。
 *
 * @param name 设置页里显示/持久化的主题名，也是主题的唯一标识
 * @param primary 主色（按钮、选中态、强调文字）
 * @param secondary 次色（小部件副标题、次要强调）
 * @param tertiary 第三色（标签、装饰）
 * @param background App 与小部件的整体底色
 * @param surface 卡片底色（比 background 浅一档）
 * @param onSurface 正文文字色
 */
data class ThemePalette(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    /**
     * 正文文字色。
     *
     * 这里刻意不用 Material 默认的 #F9FAFB：文字是画在半透明玻璃卡片上、卡片底下还有柔光，
     * 实测会把亮度吃掉一截（14sp 的 #D1D5DB 实际只渲染出 #777D84）。所以正文直接用近纯白，
     * 次要文字用 #B9C1CD —— 在玻璃上仍能保持 7:1 以上对比度。
     */
    val onSurface: Color = Color(0xFFFFFFFF),
    val onSurfaceVariant: Color = Color(0xFFB9C1CD)
) {
    /** 比 primary 深一档，用于选中项的填充底 */
    val primaryContainer: Color get() = lerp(primary, Color.Black, 0.32f)

    val onPrimaryContainer: Color get() = lerp(primary, Color.White, 0.78f)

    val secondaryContainer: Color get() = lerp(secondary, Color.Black, 0.45f)

    val onSecondaryContainer: Color get() = lerp(secondary, Color.White, 0.78f)

    /** 空格子/分隔用的中间色：底色往白色提亮一档 */
    val surfaceVariant: Color get() = lerp(background, Color.White, 0.11f)

    val outline: Color get() = lerp(background, Color.White, 0.32f)

    val outlineVariant: Color get() = lerp(background, Color.White, 0.22f)

    /**
     * 悬浮导航条底色（**不透明**）。
     *
     * 取舍记录：
     * - 做成半透明时，滚到亮色课程卡片上会透出那边的亮色，整条忽明忽暗（实测胶囊底从 #38414E 变到 #616873）；
     * - 提亮到 #59606C 时，白色图标和文字被拉进中间灰，两者只剩 1.5:1，反而更糊。
     * 所以底栏定为：不透明、只比背景亮一档（约 2.0:1 边界对比），把对比度预算全部留给纯白图标与文字。
     * 玻璃质感由描边和顶边细高光表达，不再靠透光。
     */
    val barFill: Color get() = lerp(surface, Color.White, 0.13f)
}

/**
 * 全部可选主题。App 内配色与桌面小部件配色都从这里取，保证两边一致。
 */
val THEMES: List<ThemePalette> = listOf(
    ThemePalette(
        name = "蓝紫渐变",
        primary = Color(0xFF6366F1),
        secondary = Color(0xFF818CF8),
        tertiary = Color(0xFFA78BFA),
        background = Color(0xFF111827),
        surface = Color(0xFF1F2937)
    ),
    ThemePalette(
        name = "粉橙渐变",
        primary = Color(0xFFEC4899),
        secondary = Color(0xFFF472B6),
        tertiary = Color(0xFFFB923C),
        background = Color(0xFF1F1420),
        surface = Color(0xFF2E1E2C)
    ),
    ThemePalette(
        name = "青绿渐变",
        primary = Color(0xFF14B8A6),
        secondary = Color(0xFF2DD4BF),
        tertiary = Color(0xFF34D399),
        background = Color(0xFF0E1F1C),
        surface = Color(0xFF16302B)
    ),
    ThemePalette(
        name = "暖阳渐变",
        primary = Color(0xFFF59E0B),
        secondary = Color(0xFFFBBF24),
        tertiary = Color(0xFFFB7185),
        background = Color(0xFF1F1810),
        surface = Color(0xFF2E2418),
        onSurface = Color(0xFFFFF7ED),
        onSurfaceVariant = Color(0xFFD6C4AB)
    ),
    ThemePalette(
        name = "冰蓝渐变",
        primary = Color(0xFF0EA5E9),
        secondary = Color(0xFF38BDF8),
        tertiary = Color(0xFF22D3EE),
        background = Color(0xFF0B1A29),
        surface = Color(0xFF13273C)
    ),
    ThemePalette(
        name = "深紫渐变",
        primary = Color(0xFF8B5CF6),
        secondary = Color(0xFFA78BFA),
        tertiary = Color(0xFFC084FC),
        background = Color(0xFF15102A),
        surface = Color(0xFF221A3D)
    )
)

/** 默认主题名 */
val DEFAULT_THEME_NAME: String = THEMES.first().name

private val themeByName: Map<String, ThemePalette> = THEMES.associateBy { it.name }

/** 找不到（旧版本存下来的名字、手工改坏的值）时回退到默认主题 */
fun resolveTheme(themeName: String?): ThemePalette =
    themeByName[themeName] ?: THEMES.first()

/** 当前主题名的 CompositionLocal：玻璃组件需要拿到配色表里的柔光/高光色，又不想层层传参 */
val LocalThemeName = staticCompositionLocalOf { DEFAULT_THEME_NAME }

private val errorColor = Color(0xFFEF4444)

private fun ThemePalette.toColorScheme() = darkColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = Color.White,
    background = background,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    error = errorColor,
    onError = Color.White
)

@Composable
fun ClassScheduleTheme(
    themeName: String = DEFAULT_THEME_NAME,
    content: @Composable () -> Unit
) {
    val palette = resolveTheme(themeName)
    val colorScheme = palette.toColorScheme()
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // edge-to-edge：系统栏保持透明，内容铺到底，玻璃层的透光才成立。
            // 这里只负责"系统栏图标用浅色"，透明本身由 enableEdgeToEdge() 设置。
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        // 同心圆角：把 Material 的三档形状重定向到本 App 的同心阶梯，
        // 这样全工程 13 处 MaterialTheme.shapes.* 自动统一，不必逐个改。
        shapes = Shapes(
            extraSmall = RoundedCornerShape(GlassTokens.radiusSmall),
            small = RoundedCornerShape(GlassTokens.radiusSmall),
            medium = RoundedCornerShape(GlassTokens.radiusInner),
            large = RoundedCornerShape(GlassTokens.radiusPanel),
            extraLarge = RoundedCornerShape(GlassTokens.radiusPanel)
        ),
        content = {
            CompositionLocalProvider(LocalThemeName provides palette.name, content = content)
        }
    )
}
