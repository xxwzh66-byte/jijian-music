package com.lxmusic.android.ui.screens

import kotlin.math.roundToInt

/**
 * V1.4: 播放页上的歌词快捷调节。
 *
 * 为什么要有这一层：字号和偏移原本只能进设置页改。但调歌词的人一定是
 * **正在听一首具体的歌并觉得某一行早了半拍**，那一刻进设置页翻两个滑块再回来，
 * 歌已经过去了。播放页右上角的菜单里给加减按钮，改动直接落盘。
 *
 * 边界值刻意与设置页的两个 Slider 完全一致（0.7–1.6 倍、±500 ms），
 * 否则从播放页调完之后打开设置页，滑块会显示一个它自己永远拖不到的数值。
 *
 * 纯函数，没有 Compose 依赖，因此可以直接测。
 */
object LyricTuning {

    const val MIN_SCALE = 0.7f
    const val MAX_SCALE = 1.6f

    /** 字号一档 5%。低了按不出变化，高了按三次就到底。 */
    const val SCALE_STEP = 0.05f

    const val MIN_OFFSET_MS = -500
    const val MAX_OFFSET_MS = 500

    /** 偏移一档 50 ms —— 人耳能分辨的歌词错位大致从 100 ms 起，50 是半步。 */
    const val OFFSET_STEP_MS = 50

    enum class ScaleStep { UP, DOWN }

    enum class OffsetStep { LATER, EARLIER }

    /**
     * 加减一档后的字号。
     *
     * 结果按两位小数吸附：设置页用 `"%.2f×"` 显示，浮点累积会让同一份设置
     * 在两处读出 1.05 与 1.0499999× 两个不一样的数字。
     */
    fun scale(current: Float, step: ScaleStep): Float {
        val raw = when (step) {
            ScaleStep.UP -> current + SCALE_STEP
            ScaleStep.DOWN -> current - SCALE_STEP
        }
        val snapped = (raw * 100f).roundToInt() / 100f
        return snapped.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /** 与设置页同一个格式，避免两处显示不一致。 */
    fun scaleText(scale: Float): String = "%.2f×".format(scale)

    /** 加减一档后的偏移毫秒数。 */
    fun offset(currentMs: Int, step: OffsetStep): Int {
        val raw = when (step) {
            OffsetStep.LATER -> currentMs + OFFSET_STEP_MS
            OffsetStep.EARLIER -> currentMs - OFFSET_STEP_MS
        }
        return raw.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
    }

    /** `+120 ms` / `-120 ms` / `0 ms`：正号必须显式写出，否则「120 ms」看不出方向。 */
    fun offsetText(offsetMs: Int): String = when {
        offsetMs > 0 -> "+$offsetMs ms"
        offsetMs < 0 -> "$offsetMs ms"
        else -> "0 ms"
    }

    /** 只有真的偏了才给「复位」入口。 */
    fun canResetOffset(offsetMs: Int): Boolean = offsetMs != 0

    fun canGrow(scale: Float): Boolean = scale < MAX_SCALE - 0.0001f

    fun canShrink(scale: Float): Boolean = scale > MIN_SCALE + 0.0001f
}
