package com.lxmusic.android.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V1.4: 右栏优先级。
 *
 * 这块逻辑错起来的症状很具体：平板上点了专辑，右栏还停在播放页；或者关掉播放页
 * 之后右栏变空白，把底下的列表挤成半屏。
 */
class WidePaneTest {

    @Test
    fun aNarrowScreenNeverGetsASecondPane() {
        assertEquals(
            WidePane.Pane.NONE,
            WidePane.resolve(wide = false, nowPlayingOpen = true, hasTrack = true, groupOpen = true),
        )
    }

    @Test
    fun theNowPlayingPageOutranksTheGroupPage() {
        assertEquals(
            WidePane.Pane.NOW_PLAYING,
            WidePane.resolve(wide = true, nowPlayingOpen = true, hasTrack = true, groupOpen = true),
        )
    }

    @Test
    fun closingThePlayerFallsBackToTheGroupPageThatWasStillOpen() {
        assertEquals(
            WidePane.Pane.GROUP,
            WidePane.resolve(wide = true, nowPlayingOpen = false, hasTrack = true, groupOpen = true),
        )
    }

    @Test
    fun anEmptyPlayerDoesNotTakeHalfTheScreen() {
        // 没有曲目时播放页是空的：一个空唱片加两个禁用按钮，不如把宽度还给列表。
        assertEquals(
            WidePane.Pane.NONE,
            WidePane.resolve(wide = true, nowPlayingOpen = true, hasTrack = false, groupOpen = false),
        )
        assertEquals(
            WidePane.Pane.GROUP,
            WidePane.resolve(wide = true, nowPlayingOpen = true, hasTrack = false, groupOpen = true),
        )
    }

    @Test
    fun nothingOpenMeansTheStageKeepsTheWholeWidth() {
        assertEquals(
            WidePane.Pane.NONE,
            WidePane.resolve(wide = true, nowPlayingOpen = false, hasTrack = true, groupOpen = false),
        )
    }

    @Test
    fun theGroupPaneIsWiderThanThePlayer() {
        assertEquals(0.5f, WidePane.share(WidePane.Pane.GROUP), 0.0001f)
        assertEquals(0.45f, WidePane.share(WidePane.Pane.NOW_PLAYING), 0.0001f)
        assertEquals(0.45f, WidePane.share(WidePane.Pane.NONE), 0.0001f)
    }
}
