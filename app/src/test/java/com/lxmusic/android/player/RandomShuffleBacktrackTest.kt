package com.lxmusic.android.player

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * V1.4.7: 「上一首」不能把用户刚按下「下一首」那一步原样撤销。
 *
 * V1.0.1 起 [RandomShuffleState.previousIndex] 的 KDoc 就写着「避开刚播完的
 * 那首」，否则按回去听到刚才那首、看起来像没生效。这条规则依赖
 * [ShuffleMemo.lastTrack]，而 `lastTrack` 由 `started(size, current)` 写入 ——
 * 写进去的是**刚开播的那一首**，也就是 `current` 本身。于是
 * `justPlayed != current` 这个前置条件在任何时刻都为假，避开规则**一次都没
 * 生效过**：
 *
 * - 播放 3 → 按下一首到 5 → 按上一首：规则本该避开 3（不然等于把刚才那步
 *   撤销掉），实际却在 0..8 里除 5 之外均匀抽，包括 3；
 * - 连按两次上一首可以在同一对曲子之间来回弹。
 *
 * 这个文件断言的是用户能看到的那两条：不能撤销、也不能在两点之间弹。
 */
class RandomShuffleBacktrackTest {

    @Test
    fun previousDoesNotUndoTheNextPressThatJustHappened() {
        var undone = 0
        for (seed in 0 until 50) {
            val state = RandomShuffleState()
            val random = Random(seed)
            state.onTrackStarted(9, 3)
            val target = state.nextIndex(9, 3, random)
            state.onTrackStarted(9, target)
            val back = state.previousIndex(9, target, random)
            if (back == 3) undone++
        }
        // 8 个候选里均匀抽，50 个种子里「一次都没回到 3」的概率是
        // (6/7)^50 ≈ 0.0005 —— 断言 0 才是真的在断言，断言 < 50 等于什么都没说。
        assertEquals(
            "previous undid the next press on $undone/50 seeds: it landed back on the track we came from",
            0,
            undone,
        )
    }

    @Test
    fun twoPreviousPressesInARowCannotBounceBetweenTheSameTwoTracks() {
        val state = RandomShuffleState()
        val random = Random(4)
        state.onTrackStarted(9, 0)

        val a = state.nextIndex(9, 0, random)
        state.onTrackStarted(9, a)

        // 第一下：不能回到 0。
        val b = state.previousIndex(9, a, random)
        state.onTrackStarted(9, b)
        assertNotEquals("the first previous press went back to where we came from", 0, b)

        // 第二下：不能立刻弹回 a（那样就是两点之间来回弹）。
        val c = state.previousIndex(9, b, random)
        assertNotEquals("the second previous press bounced straight back", a, c)
    }

    @Test
    fun theAvoidedTrackIsTheOneWeCameFromNotTheOnePlaying() {
        // 语义断言：started() 记的是「上一首」，不是「这一首」。
        val state = RandomShuffleState()
        state.onTrackStarted(9, 2)
        state.onTrackStarted(9, 6)
        // 从 2 走到 6，所以「上一首」是 2。
        val order = RandomShuffleOrder(9, state, Random(1))
        for (index in 0 until 9) {
            if (index == 6) continue
            val previous = order.getPreviousIndex(index)
            assertNotEquals(
                "previousIndex from $index ignored the track we came from",
                2,
                previous,
            )
        }
    }

    @Test
    fun repeatingTheSameStartReportDoesNotEraseWhatWeCameFrom() {
        // 两条换歌路径都会上报同一首：onMediaItemTransition 与 playIndex。
        // 第二条不能把「上一首」覆盖成当前首。
        val state = RandomShuffleState()
        state.onTrackStarted(9, 2)
        state.onTrackStarted(9, 6)
        state.onTrackStarted(9, 6)
        state.onTrackStarted(9, 6)

        val order = RandomShuffleOrder(9, state, Random(1))
        assertNotEquals(
            "a repeated start report reset the track we came from",
            2,
            order.getPreviousIndex(4),
        )
    }
}
