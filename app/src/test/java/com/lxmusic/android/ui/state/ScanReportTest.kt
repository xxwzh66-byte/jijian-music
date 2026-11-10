package com.lxmusic.android.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanReportTest {

    @Test
    fun aFreshReportHasNotRun() {
        assertFalse(ScanReport().hasRun)
    }

    @Test
    fun aReportWithAnyActivityHasRun() {
        assertTrue(ScanReport(total = 10).hasRun)
        assertTrue(ScanReport(reused = 3).hasRun)
        assertTrue(ScanReport(read = 3).hasRun)
    }

    @Test
    fun reusedFractionIsTheShareOfTheScanThatWasCache() {
        assertEquals(0.75f, ScanReport(total = 100, reused = 75, read = 25).reusedFraction, 0.0001f)
    }

    @Test
    fun reusedFractionIsZeroWhenNothingWasScanned() {
        assertEquals(0f, ScanReport().reusedFraction, 0.0001f)
        assertEquals(0f, ScanReport(total = 5).reusedFraction, 0.0001f)
    }

    @Test
    fun elapsedTextSwitchesFromMillisecondsToSeconds() {
        assertEquals("480 ms", ScanReport(elapsedMs = 480).elapsedText)
        assertEquals("1.5 s", ScanReport(elapsedMs = 1500).elapsedText)
        assertEquals("12.0 s", ScanReport(elapsedMs = 12_000).elapsedText)
    }
}
