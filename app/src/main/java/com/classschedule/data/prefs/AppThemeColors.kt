package com.classschedule.data.prefs

import com.classschedule.ui.theme.resolveTheme

/**
 * 主题名 → 颜色。
 *
 * 真正的配色表在 ui/theme/Theme.kt（[com.classschedule.ui.theme.THEMES]），App 内 Compose 主题和
 * 桌面小部件共用同一份；小部件不在 Compose 环境里，只能拿到 ARGB，所以这里做一层转换。
 */
object AppThemeColors {

    /** 卡片/分隔用的半透明白 */
    const val SURFACE_TRANSLUCENT = 0x14FFFFFFL

    /** 主题底色（小部件背景） */
    fun backgroundOf(themeName: String?): Long = resolveTheme(themeName).background.toArgbLong()
}

private fun androidx.compose.ui.graphics.Color.toArgbLong(): Long =
    value.toLong() and 0xFFFFFFFFL
