package com.lxmusic.android.ui.components

import kotlin.math.ceil

/**
 * How much of an album cover is worth decoding.
 *
 * Every cover in this app is a local file (`cacheDir/covers/<id>.jpg`, extracted
 * from the embedded picture by `SafMusicScanner`), and those files are whatever
 * resolution the tag carried — commonly 1000×1000, sometimes larger. Coil will
 * happily decode all of it into an ARGB_8888 bitmap: 1000×1000 is 4 MB, and a
 * list of 40 rows decoding at 4 MB each is 160 MB of bitmap churn for thumbnails
 * that are 52 dp tall.
 *
 * Coil does infer a target size from the layout constraints, so the obvious
 * cases are already handled. What it cannot know is the *policy*: that a
 * full-bleed cover behind the lyrics is faded to 12 % alpha and therefore does
 * not need its full resolution either, and that nothing in this app should ever
 * decode a cover larger than a phone screen.
 *
 * The tiers below are that policy, kept as a pure function so it can be tested
 * without a Compose harness.
 */
enum class CoverTier(val maxPx: Int) {

    /** List rows, chips, the mini player. */
    THUMB(160),

    /** Grid tiles and the album header. */
    TILE(320),

    /** Full-screen artwork: the now-playing stage. */
    LARGE(1080),
    ;

    companion object {

        /**
         * The tier a cover displayed at [sizeDp] belongs to.
         *
         * The boundary is on the `dp` value rather than on pixels because the
         * tier is picked in composables, which only know dp; the density
         * conversion happens once, in [pixels].
         */
        fun forSizeDp(sizeDp: Int): CoverTier = when {
            sizeDp <= 72 -> THUMB
            sizeDp <= 200 -> TILE
            else -> LARGE
        }

        /**
         * Pixels to ask Coil for, for a cover laid out at [sizeDp].
         *
         * Rounds up — a thumbnail displayed at 52 dp on a 3× screen is 156 px,
         * and asking for 155 would make Coil scale the decoded bitmap up on
         * the last row. Values are clamped into `1..tier.maxPx`: the lower
         * bound because a zero-size request means "decode at full resolution"
         * to Coil, which is the exact behaviour this is removing.
         */
        fun pixels(sizeDp: Int, density: Float): Int {
            if (sizeDp <= 0) return 1
            val raw = ceil(sizeDp * density).toInt()
            return raw.coerceIn(1, forSizeDp(sizeDp).maxPx)
        }
    }
}
