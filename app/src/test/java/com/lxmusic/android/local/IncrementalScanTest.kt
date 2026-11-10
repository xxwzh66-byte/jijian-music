package com.lxmusic.android.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the "is this file worth re-reading" decision.
 *
 * The whole point of the rule is that a wrong `true` silently freezes stale
 * metadata: the title never updates, the duration stays wrong, and no error is
 * ever raised. So every case below is about *when not to trust the cache*.
 */
class IncrementalScanTest {

    private fun stamp(mtime: Long, size: Long) = IncrementalScan.Stamp(mtime, size)

    @Test
    fun matchingStampMeansTheCachedRowIsReused() {
        val cached = stamp(1_700_000_000_000L, 8_388_608L)
        val onDisk = stamp(1_700_000_000_000L, 8_388_608L)
        assertTrue(IncrementalScan.isUnchanged(cached, onDisk))
        assertFalse(IncrementalScan.isChanged(cached, onDisk))
    }

    @Test
    fun aFileThePreviousScanNeverSawIsAlwaysRead() {
        assertFalse(IncrementalScan.isUnchanged(null, stamp(1L, 1L)))
        assertTrue(IncrementalScan.isChanged(null, stamp(1L, 1L)))
    }

    @Test
    fun aTouchedFileIsReadAgain() {
        val cached = stamp(1_700_000_000_000L, 8_388_608L)
        val onDisk = stamp(1_700_000_000_001L, 8_388_608L)
        assertFalse(IncrementalScan.isUnchanged(cached, onDisk))
    }

    @Test
    fun aFileRewrittenInPlaceIsReadAgain() {
        // Same second, different length: the mtime alone would say "unchanged"
        // and keep serving the old duration.
        val cached = stamp(1_700_000_000_000L, 8_388_608L)
        val onDisk = stamp(1_700_000_000_000L, 4_194_304L)
        assertFalse(IncrementalScan.isUnchanged(cached, onDisk))
    }

    @Test
    fun aZeroMtimeIsNeverEvidenceOfBeingUnchanged() {
        // Some providers report 0 for both fields. Two zeroes matching each
        // other is the absence of information, not a match.
        val cached = stamp(0L, 8_388_608L)
        val onDisk = stamp(0L, 8_388_608L)
        assertFalse(IncrementalScan.isUnchanged(cached, onDisk))
    }

    @Test
    fun aStampIsKnownOnlyWhenBothNumbersWereReported() {
        assertTrue(stamp(1L, 0L).isKnown)
        assertFalse(stamp(0L, 0L).isKnown)
        assertFalse(stamp(0L, 10L).isKnown)
        // A negative size cannot come from a real file, so it is not a fact.
        assertFalse(stamp(1L, -1L).isKnown)
    }

    @Test
    fun aCachedRowWithNoStampCannotBeReused() {
        // Rows written before this feature (or from the MediaStore path) carry
        // no lastModified/size; the scanner must treat them as unknown.
        val cached = stamp(0L, 0L)
        assertFalse(IncrementalScan.isUnchanged(cached, stamp(1_700_000_000_000L, 100L)))
    }

    @Test
    fun bothSidesHaveToBeKnownForAReuse() {
        val now = stamp(1_700_000_000_000L, 100L)
        assertFalse(IncrementalScan.isUnchanged(stamp(0L, 100L), now))
        assertFalse(IncrementalScan.isUnchanged(stamp(1_700_000_000_000L, 100L), stamp(0L, 100L)))
    }
}
