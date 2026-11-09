package com.lxmusic.android.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of an in-app update that can go wrong without a network: naming the
 * file, reading progress, and deciding whether a download is the one GitHub
 * published.
 */
class ApkDownloaderTest {

    // ---------------- fileNameFor ----------------

    @Test
    fun aNormalAssetNameIsKept() {
        assertEquals(
            "jijian-music-1.1.7-vc12.apk",
            ApkDownloader.fileNameFor("jijian-music-1.1.7-vc12.apk"),
        )
    }

    @Test
    fun aNameWithoutTheApkSuffixGetsOne() {
        assertEquals("release.apk", ApkDownloader.fileNameFor("release"))
    }

    @Test
    fun aMissingNameFallsBackInsteadOfProducingAnEmptyPath() {
        assertEquals(ApkDownloader.FALLBACK_NAME, ApkDownloader.fileNameFor(null))
        assertEquals(ApkDownloader.FALLBACK_NAME, ApkDownloader.fileNameFor("   "))
    }

    @Test
    fun onlyTheLastPathSegmentSurvives() {
        // A name with a slash would otherwise escape the directory the store
        // hands to the downloader.
        assertEquals("app.apk", ApkDownloader.fileNameFor("../../etc/app.apk"))
        assertEquals("app.apk", ApkDownloader.fileNameFor("dir\\app.apk"))
    }

    // ---------------- fraction ----------------

    @Test
    fun progressIsAFractionOfTheAdvertisedSize() {
        assertEquals(0.5f, ApkDownloader.fraction(50L, 100L), 0.0001f)
        assertEquals(1f, ApkDownloader.fraction(100L, 100L), 0.0001f)
    }

    @Test
    fun anUnknownTotalReportsZeroRatherThanDividingByIt() {
        assertEquals(0f, ApkDownloader.fraction(1234L, 0L), 0.0001f)
        assertEquals(0f, ApkDownloader.fraction(1234L, -1L), 0.0001f)
    }

    @Test
    fun anOverlongBodyStopsAtOne() {
        // Content-Length lies sometimes; the bar must not overshoot.
        assertEquals(1f, ApkDownloader.fraction(200L, 100L), 0.0001f)
    }

    // ---------------- digestMatches ----------------

    @Test
    fun aMatchingDigestPasses() {
        assertTrue(ApkDownloader.digestMatches("sha256:AbCdEf", "abcdef"))
    }

    @Test
    fun aBareHexDigestIsAcceptedToo() {
        assertTrue(ApkDownloader.digestMatches("abcdef", "abcdef"))
    }

    @Test
    fun aMismatchedDigestIsRejected() {
        assertFalse(ApkDownloader.digestMatches("sha256:abcdef", "abcde0"))
    }

    @Test
    fun aReleaseWithoutADigestIsNotBlocked() {
        // Older releases were published before GitHub exposed `digest`; they
        // must stay installable.
        assertTrue(ApkDownloader.digestMatches(null, "abcdef"))
        assertTrue(ApkDownloader.digestMatches("  ", "abcdef"))
    }

    @Test
    fun anUnreadableFileDoesNotSilentlyPassAMissingCheck() {
        // No digest published + unreadable file is still a pass (nothing to
        // compare), but a published digest with no hash is a mismatch.
        assertFalse(ApkDownloader.digestMatches("sha256:abcdef", null))
    }
}
