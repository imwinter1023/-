package com.careos.launcher.prefs

import android.content.Context

/**
 * 显示偏好：字号档位 / 高对比度 / 加粗。
 *
 * 用 SharedPreferences 而不是 DataStore —— 桌面要在 onCreate 里**同步**读到字号，
 * 否则会先按默认字号画一帧再跳变，老人看得见这个闪动。
 */
object DisplayPrefs {

    private const val FILE = "careos_display"
    private const val KEY_SCALE = "font_scale"
    private const val KEY_HIGH_CONTRAST = "high_contrast"
    private const val KEY_BOLD = "bold"

    /** 默认落在「大」这一档，而不是系统默认的 1.0，让新用户第一次打开就够大 */
    const val DEFAULT_LEVEL = 4

    /** 七档倍率：1.00 → 1.95。刻意做成非等差，小档位精细、大档位跨度大。 */
    private val SCALE = floatArrayOf(1.00f, 1.10f, 1.25f, 1.40f, 1.55f, 1.75f, 1.95f)

    /** 画布上的四个口语化档位名，映射到七档滑块 */
    private val TAGS = arrayOf("小", "较小", "标准", "较大", "大", "特大", "最大")

    const val LEVEL_COUNT = 7

    fun level(context: Context): Int =
        prefs(context).getInt(KEY_SCALE, DEFAULT_LEVEL).coerceIn(0, LEVEL_COUNT - 1)

    fun setLevel(context: Context, level: Int) =
        prefs(context).edit().putInt(KEY_SCALE, level.coerceIn(0, LEVEL_COUNT - 1)).apply()

    /** 某个档位对应的倍率。设置页的实时预览也用它 —— 倍率数组只允许存在这一份。 */
    fun scaleOf(level: Int): Float = SCALE[level.coerceIn(0, LEVEL_COUNT - 1)]

    /** 交给 CompositionLocal 的 fontScale 值 */
    fun fontScale(context: Context): Float = scaleOf(level(context))

    fun tag(level: Int): String = TAGS[level.coerceIn(0, LEVEL_COUNT - 1)]

    fun highContrast(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIGH_CONTRAST, false)

    fun setHighContrast(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_HIGH_CONTRAST, on).apply()

    fun bold(context: Context): Boolean = prefs(context).getBoolean(KEY_BOLD, false)

    fun setBold(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_BOLD, on).apply()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
