package com.lxmusic.android.ui.neon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 会"发光"的玻璃面板底板。
 *
 * 一层从 0.13 到 0.06 的白色竖向渐变 + 1dp 描边，朴素但有一个关键好处：
 * 它**不依赖背景模糊**。真正的 Glassmorphism 需要 backdrop-filter / RenderEffect，
 * 而这个项目已经因为全屏 RenderEffect 崩溃过（NowPlayingScreen.kt:140-145），
 * 所以这里用"高光渐变 + 描边"伪造折射感——在深色极光背景上几乎看不出差别。
 * （对应 ui-ux-pro-max 里 Glassmorphism 的 cost:low 实现路径。）
 */
fun Modifier.neonGlass(
    shape: Shape = RoundedCornerShape(NeonTokens.radiusGlass),
    fillTop: Color = NeonTokens.glassHi,
    fillBottom: Color = NeonTokens.glass,
    borderColor: Color = NeonTokens.stroke,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(fillTop, fillBottom)))
    .border(borderWidth, borderColor, shape)

/**
 * 玻璃胶囊（圆形/胶囊按钮的底板）。
 *
 * [active] 为 true 时描边换成 [accent] 的半透明版，这是"开启态"的唯一视觉
 * 语言：除了图标换色，描边也会变亮，这样即使靠色觉之外的信息也能分辨开关。
 */
fun Modifier.neonChip(
    shape: Shape = RoundedCornerShape(50),
    active: Boolean = false,
    accent: Color = NeonTokens.accent,
): Modifier = this
    .clip(shape)
    .background(if (active) accent.copy(alpha = 0.16f) else NeonTokens.glass)
    .border(
        width = 1.dp,
        color = if (active) accent.copy(alpha = 0.55f) else NeonTokens.stroke,
        shape = shape,
    )
