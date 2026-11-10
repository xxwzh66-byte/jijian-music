package com.lxmusic.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V1.4: 播放页里的歌词快捷调节。
 *
 * 这些边界值是刻意钉死的 —— 字号滑块在设置页里被限制在 0.7×–1.6×，
 * 播放页的加减按钮必须落在同一区间，否则从播放页调完之后打开设置页，
 * 滑块会显示一个它自己永远拖不到的数值。
 */
class LyricTuningTest {

    @Test
    fun oneStepUpIsOneStepUp() {
        assertEquals(1.05f, LyricTuning.scale(1.0f, LyricTuning.ScaleStep.UP), 0.0001f)
        assertEquals(0.95f, LyricTuning.scale(1.0f, LyricTuning.ScaleStep.DOWN), 0.0001f)
    }

    @Test
    fun theCeilingIsTheSameAsTheSettingsSlider() {
        assertEquals(LyricTuning.MAX_SCALE, LyricTuning.scale(1.6f, LyricTuning.ScaleStep.UP), 0.0001f)
        assertEquals(LyricTuning.MAX_SCALE, LyricTuning.scale(1.58f, LyricTuning.ScaleStep.UP), 0.0001f)
    }

    @Test
    fun theFloorIsTheSameAsTheSettingsSlider() {
        assertEquals(LyricTuning.MIN_SCALE, LyricTuning.scale(0.7f, LyricTuning.ScaleStep.DOWN), 0.0001f)
        assertEquals(LyricTuning.MIN_SCALE, LyricTuning.scale(0.72f, LyricTuning.ScaleStep.DOWN), 0.0001f)
    }

    @Test
    fun aValueOutsideTheRangeIsPulledBackIn() {
        assertEquals(LyricTuning.MAX_SCALE, LyricTuning.scale(3.0f, LyricTuning.ScaleStep.UP), 0.0001f)
        assertEquals(LyricTuning.MIN_SCALE, LyricTuning.scale(0.1f, LyricTuning.ScaleStep.DOWN), 0.0001f)
    }

    @Test
    fun stepsStayOnAFivePercentGrid() {
        // 反复加减不能漂成 1.0500001：设置页显示的是 "%.2f×"，
        // 漂了之后同一份设置在两处读出不同的数字。
        var v = 1.0f
        repeat(7) { v = LyricTuning.scale(v, LyricTuning.ScaleStep.UP) }
        assertEquals(1.35f, v, 0.0001f)
        repeat(14) { v = LyricTuning.scale(v, LyricTuning.ScaleStep.DOWN) }
        assertEquals(0.65f.coerceAtLeast(LyricTuning.MIN_SCALE), v, 0.0001f)
    }

    @Test
    fun scaleTextMatchesTheSettingsFormat() {
        assertEquals("1.00×", LyricTuning.scaleText(1.0f))
        assertEquals("1.35×", LyricTuning.scaleText(1.35f))
    }

    @Test
    fun offsetMovesInFiftyMillisecondSteps() {
        assertEquals(50, LyricTuning.offset(0, LyricTuning.OffsetStep.LATER))
        assertEquals(-50, LyricTuning.offset(0, LyricTuning.OffsetStep.EARLIER))
        assertEquals(0, LyricTuning.offset(50, LyricTuning.OffsetStep.EARLIER))
    }

    @Test
    fun offsetStaysInsideTheSliderRange() {
        assertEquals(500, LyricTuning.offset(500, LyricTuning.OffsetStep.LATER))
        assertEquals(500, LyricTuning.offset(480, LyricTuning.OffsetStep.LATER))
        assertEquals(-500, LyricTuning.offset(-500, LyricTuning.OffsetStep.EARLIER))
        assertEquals(-500, LyricTuning.offset(-480, LyricTuning.OffsetStep.EARLIER))
    }

    @Test
    fun offsetTextIsSignedOnlyWhenLate() {
        assertEquals("0 ms", LyricTuning.offsetText(0))
        assertEquals("+120 ms", LyricTuning.offsetText(120))
        assertEquals("-120 ms", LyricTuning.offsetText(-120))
    }

    @Test
    fun resettingIsOnlyOfferedWhenThereIsSomethingToReset() {
        assertFalse(LyricTuning.canResetOffset(0))
        assertTrue(LyricTuning.canResetOffset(50))
        assertTrue(LyricTuning.canResetOffset(-50))
    }

    @Test
    fun theStepButtonsAreDisabledAtTheEnds() {
        assertFalse(LyricTuning.canGrow(LyricTuning.MAX_SCALE))
        assertTrue(LyricTuning.canGrow(1.55f))
        assertFalse(LyricTuning.canShrink(LyricTuning.MIN_SCALE))
        assertTrue(LyricTuning.canShrink(0.75f))
    }
}
