package com.classschedule.ui.glass

/**
 * 液态玻璃（Liquid Glass）—— 本 App 的实现说明。
 *
 * ## 实现方式：自绘材质，**不依赖任何第三方库**
 *
 * 早前尝试过接入 `io.github.kyant0:backdrop`（AGSL 真折射/色散），最终放弃，原因有三：
 *
 * 1. **原生崩溃**：库的 `drawBackdrop` 只要出现**第二个消费端**就触发
 *    `SIGSEGV / SEGV_ACCERR (write) in RenderThread`。已用 5 组对照实验确认：
 *    与玻璃面数量（5 张 / 1 张）、尺寸、显式高度、挂载结构**均无关**，是库内部状态问题。
 * 2. **违反 Apple 规格**：规格要求"避免玻璃元素相互堆叠，玻璃无法采样其他玻璃"，
 *    而全屏铺玻璃时必然出现玻璃叠玻璃。
 * 3. **技术受限**：Compose 1.7.2 的 `GraphicsLayer` 没有公开 `RenderNode`，
 *    无法把录制的背景作为纹理喂给自绘 shader，因此自绘真折射在这套版本上做不到。
 *
 * ## 现在的方案：用材质分层还原观感
 *
 * 真折射缺席，但苹果玻璃的高级感主要来自材质分层，这部分完整实现了：
 *
 * | 组件 | 职责 |
 * |---|---|
 * | [liquidGlass] | 磨砂区（填充差 + 弱对角柔光 + 可选内阴影） |
 * | [liquidGlassInset] | 凹陷语义（输入框、搜索框） |
 * | [globalGlassSheen] | **整块玻璃**的全局高光带 |
 * | [glassContactShadow] | 把浮动控件从内容上抬起来 |
 * | [GlassTokens] | 全部参数（同心圆角 / 弹簧 / 时长 / 光学）的唯一来源 |
 * | [GlassDialog] / [GlassDropdownMenu] | 玻璃对话框与下拉菜单 |
 * | [GlassDatePickerDialog] | 玻璃日历 |
 *
 * 设计心智模型见 [GlassMaterial] 的注释：**整个界面是一块玻璃**，
 * 卡片是这块玻璃上的"磨砂区域"，而不是一堆独立的玻璃板。
 *
 * 材质是纯自绘的，**不依赖任何系统图形特性**，所以 API 26+ 表现完全一致。
 * 桌面小部件的样式规则见 `widget/WidgetShared.kt`（Glance 只支持纯色+圆角）。
 */
