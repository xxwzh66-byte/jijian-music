package com.lxmusic.android.ui.shell

/**
 * 宽屏右栏该演什么。
 *
 * V1.4 之前右栏只有播放页一种内容，判断塞在 `LxApp` 的一个 `if` 里。加了专辑页
 * 之后这里变成三个互斥状态 + 一条优先级，写成纯函数才能在没有设备的情况下断言
 * —— 这块逻辑一旦错，用户看到的是"点了专辑，右栏什么都没变"。
 */
object WidePane {

    /** 右栏的三种内容。 */
    enum class Pane {
        /** 没有右栏，舞台独占整屏。 */
        NONE,

        /** 专辑/歌手详情。 */
        GROUP,

        /** 全屏播放页。 */
        NOW_PLAYING,
    }

    /**
     * 决定右栏演什么。
     *
     * 优先级：播放页 > 专辑页。播放页是用户刚刚点出来的，它必须在最上面；专辑页
     * 关掉播放页后还在原地，所以不需要重新导航一次。
     *
     * @param wide 屏幕是否够宽（>= 840dp）。窄屏永远没有右栏。
     * @param hasTrack 当前是否有曲目。没有曲目时播放页是空的，不该占半屏。
     */
    fun resolve(wide: Boolean, nowPlayingOpen: Boolean, hasTrack: Boolean, groupOpen: Boolean): Pane =
        when {
            !wide -> Pane.NONE
            nowPlayingOpen && hasTrack -> Pane.NOW_PLAYING
            groupOpen -> Pane.GROUP
            else -> Pane.NONE
        }

    /**
     * 右栏占屏宽的比例。
     *
     * 播放页是竖版唱片，0.45 够用；专辑页是一列行，太窄会把歌名挤成两行，所以
     * 给到 0.5。这里只返回比例，夹紧交给 `LxShell`（它才是知道真实约束的地方）。
     */
    fun share(pane: Pane): Float = when (pane) {
        Pane.GROUP -> 0.5f
        else -> 0.45f
    }
}
