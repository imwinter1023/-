package com.careos.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * CareOS 设计令牌 —— 与画布上的设计变量一一对应。
 *
 * 画布侧变量集：CareOS-Color / CareOS-Size
 * 这里是它们的 Kotlin 投影，改色改字号只需改这一个文件。
 */
object CareOSColor {
    val bg = Color(0xFFFBF7F2)
    val surface = Color(0xFFFFFFFF)
    val primary = Color(0xFFE05A1E)
    val primaryDark = Color(0xFFB84512)
    val primarySoft = Color(0xFFFCEEDF)
    val callGreen = Color(0xFF0A8F4D)
    val wechatGreen = Color(0xFF07C160)
    val hangupRed = Color(0xFFD93026)
    val textPrimary = Color(0xFF1F1B16)
    val textSecondary = Color(0xFF5C554C)
    val border = Color(0xFFE8E0D4)
    val track = Color(0xFFF2EDE6)

    /** 深绿底上用的浅色文字，保证 7:1 以上对比度 */
    val onCallGreenSoft = Color(0xFFE8F5ED)
}

object CareOSSize {
    const val radiusCard = 26
    const val radiusButton = 16

    /**
     * 触控热区下限。
     * 画布上定的是 88（对应 1x 设计稿坐标），落到真机用 dp 表达：
     * 主要动作按钮 64dp（≈128px @2x）已经远超 Android 推荐的 48dp，
     * 底部紧急求助用满 88dp，留给最需要点准的那一下。
     */
    const val touchPrimary = 64
    const val touchCritical = 88
}

/**
 * 字号阶：与画布同名档位。
 * 真机上的缩放不靠改这里，而是靠 MainActivity 里覆盖 CompositionLocal 的 fontScale，
 * 这样子树里所有 sp 一起放大，不会漏掉任何一处文字。
 */
object CareOSFont {
    const val huge = 64
    const val pageTitle = 34
    const val cardTitle = 32
    const val action = 24
    const val body = 20
    const val caption = 18

    /** 正文下限，低于这个值不合法（适老硬指标） */
    const val bodyMin = 16

    val bold = FontWeight.Bold
    val medium = FontWeight.Medium
    val regular = FontWeight.Normal
}
