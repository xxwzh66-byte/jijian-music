package com.lxmusic.android.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lxmusic.android.ui.neon.rememberMotionEnabled

/**
 * 一块「幕布」：盖在舞台之上的全屏页面。
 *
 * [onDismiss] 是这块幕布自己的关闭动作 —— 返回键和页面里的返回钮都走它。
 * 之所以由幕布自己携带而不是让调用方写一个总的 `onBack`：五个全屏页的关闭
 * 分散在 `MainViewModel` 的三个开关 + 两个局部 `remember` 里，只有调用点知道
 * 该把谁置 false。
 */
class CurtainSpec(
    val key: String,
    val visible: Boolean,
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit,
)

/**
 * 幕布进出的时长。比状态切换（`NeonTiming.stateMs` = 220ms）略长：它移动的是
 * 整屏内容，太快会看起来像闪了一下。
 */
private const val CurtainMs = 260

/** 进场时从下方浮起的距离。只够读出「这是盖上来的一层」，不做整屏滑入。 */
private val CurtainRise = 12.dp

/**
 * 主界面骨架：舞台 + 幕布。
 *
 * 舞台是常驻的那一层，分四个槽位：
 * - [backdrop] 最底层的极光，永远铺满
 * - [tabs] 主界面正文（顶栏 + 标签 + 当前标签页）
 * - [dock] 底部迷你播放条
 * - [spotlight] 宽屏时右侧的播放页；窄屏传 null
 *
 * 幕布盖在舞台之上。列表里**越靠前越在上层**，与 V1.1.2 之前那串 early-return
 * 的判断顺序一致（设置最先判、播放页最后判），所以「从播放页打开队列」的层级
 * 和以前完全一样。
 *
 * 每块幕布始终参与组合，靠 [AnimatedVisibility] 的 [visible] 进出 —— 这样退场
 * 动画期间内容还在，不会先白屏再消失。
 */
@Composable
fun LxShell(
    backdrop: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
    dock: @Composable () -> Unit,
    curtains: List<CurtainSpec>,
    spotlight: (@Composable () -> Unit)? = null,
    spotlightShare: Float = 0.45f,
) {
    // 返回键只关最上面那块。没有任何幕布时不接管，留给系统。
    curtains.firstOrNull { it.visible }?.let { top ->
        BackHandler { top.onDismiss() }
    }

    Box(Modifier.fillMaxSize()) {
        backdrop()

        if (spotlight != null) {
            // 宽屏双栏：左栏是完整舞台，右栏是同一个播放页，不再弹全屏。
            // 播放页是竖版唱片，45% 够用；专辑页是一列 48dp 的行，太窄会把歌名
            // 挤成两行。所以右栏宽度由调用方按内容给，缺省仍是 0.45。
            val share = spotlightShare.coerceIn(0.30f, 0.70f)
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f - share)) { Stage(tabs, dock) }
                Box(Modifier.weight(share)) { spotlight() }
            }
        } else {
            Stage(tabs, dock)
        }

        // 从下往上叠：列表末尾先画（在最底），列表开头最后画（在最顶）。
        curtains.asReversed().forEach { curtain ->
            key(curtain.key) {
                Curtain(visible = curtain.visible) { curtain.content() }
            }
        }
    }
}

@Composable
private fun Stage(
    tabs: @Composable () -> Unit,
    dock: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { tabs() }
        dock()
    }
}

/**
 * 一块幕布的进出场。
 *
 * 进场：淡入 + 从下方浮起 [CurtainRise]。退场反向。系统关掉动画时两者都退化成
 * 直接出现/消失 —— `rememberMotionEnabled` 是应用里读这个设置的唯一入口。
 */
@Composable
private fun Curtain(visible: Boolean, content: @Composable () -> Unit) {
    val animate = rememberMotionEnabled()
    val rise = with(LocalDensity.current) { CurtainRise.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        enter = if (animate) {
            fadeIn(tween(CurtainMs)) + slideInVertically(tween(CurtainMs)) { rise }
        } else {
            fadeIn(tween(0))
        },
        exit = if (animate) {
            fadeOut(tween(CurtainMs)) + slideOutVertically(tween(CurtainMs)) { rise }
        } else {
            fadeOut(tween(0))
        },
    ) {
        content()
    }
}
