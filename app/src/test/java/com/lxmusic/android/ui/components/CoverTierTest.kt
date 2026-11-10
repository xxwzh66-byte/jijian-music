package com.lxmusic.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the cover decode-size policy.
 *
 * These pin the two places where a plausible-looking implementation is wrong:
 * rounding down (Coil then scales the bitmap up on the last row) and allowing
 * a zero size through (Coil reads 0 as "original size", which is the exact
 * allocation this policy exists to prevent).
 */
class CoverTierTest {

    @Test
    fun aListRowIsAThumbnail() {
        assertEquals(CoverTier.THUMB, CoverTier.forSizeDp(42))
        assertEquals(CoverTier.THUMB, CoverTier.forSizeDp(52))
        assertEquals(CoverTier.THUMB, CoverTier.forSizeDp(72))
    }

    @Test
    fun aGridTileSitsAboveTheThumbnailBoundary() {
        assertEquals(CoverTier.TILE, CoverTier.forSizeDp(73))
        assertEquals(CoverTier.TILE, CoverTier.forSizeDp(96))
        assertEquals(CoverTier.TILE, CoverTier.forSizeDp(132))
        assertEquals(CoverTier.TILE, CoverTier.forSizeDp(200))
    }

    @Test
    fun aFullBleedCoverIsLarge() {
        assertEquals(CoverTier.LARGE, CoverTier.forSizeDp(201))
        assertEquals(CoverTier.LARGE, CoverTier.forSizeDp(400))
    }

    @Test
    fun theRequestedPixelsFollowTheDensity() {
        assertEquals(156, CoverTier.pixels(sizeDp = 52, density = 3f))
        assertEquals(312, CoverTier.pixels(sizeDp = 156, density = 2f))
    }

    @Test
    fun theRequestedPixelsAreRoundedUpNotDown() {
        // 52 * 2.75 = 143.0 exactly; 53 * 2.75 = 145.75 must not become 145.
        assertEquals(146, CoverTier.pixels(sizeDp = 53, density = 2.75f))
        assertEquals(143, CoverTier.pixels(sizeDp = 52, density = 2.75f))
    }

    @Test
    fun aThumbnailIsCappedEvenOnAnAbsurdDensity() {
        // 72 dp at density 10 would be 720 px; the cap is what keeps a list of
        // forty rows from decoding forty megapixels.
        assertEquals(CoverTier.THUMB.maxPx, CoverTier.pixels(sizeDp = 72, density = 10f))
    }

    @Test
    fun theCapIsPerTier() {
        assertEquals(160, CoverTier.pixels(sizeDp = 60, density = 8f))
        assertEquals(320, CoverTier.pixels(sizeDp = 120, density = 8f))
        assertEquals(1080, CoverTier.pixels(sizeDp = 400, density = 8f))
    }

    @Test
    fun aZeroSizeNeverAsksForTheOriginal() {
        // A zero size on the request means "no target size" to Coil, i.e. the
        // full embedded picture. One pixel is a bug, but a bounded one.
        assertEquals(1, CoverTier.pixels(sizeDp = 0, density = 3f))
        assertEquals(1, CoverTier.pixels(sizeDp = -10, density = 3f))
    }

    @Test
    fun aTinySizeStillAsksForAtLeastOnePixel() {
        assertEquals(1, CoverTier.pixels(sizeDp = 1, density = 0.1f))
    }
}
