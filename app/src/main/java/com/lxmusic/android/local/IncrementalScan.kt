package com.lxmusic.android.local

/**
 * Decides whether a cached track row can be trusted for a file that was just
 * listed again.
 *
 * The expensive part of a scan is not walking the tree — it is
 * [android.media.MediaMetadataRetriever] opening every file to read its title,
 * artist and duration. On a 3000-track folder that is minutes of I/O. Almost
 * none of those files changed since the last scan, so the cheap pair of numbers
 * a SAF provider already exposes (`lastModified`, `length`) is enough to decide.
 *
 * Two rules keep this honest:
 *
 * - **Both stamps must exist.** Some providers report `0` for both. A zero is
 *   not evidence that the file is unchanged, it is evidence that nothing was
 *   reported, so those rows always get re-read.
 * - **Both must match.** A file whose mtime is unchanged but whose size changed
 *   was rewritten in place within the same second; trusting the mtime alone
 *   would keep serving the old duration.
 */
object IncrementalScan {

    /** The two numbers a listing gives us for free, and what they replace. */
    data class Stamp(val lastModified: Long, val size: Long) {

        /** False when the provider told us nothing — that is not "unchanged". */
        val isKnown: Boolean get() = lastModified > 0L && size >= 0L
    }

    /**
     * True when [cached] still describes the file whose stamp is [current].
     *
     * [cached] is null for a file the previous scan never saw, which is by
     * definition a read.
     */
    fun isUnchanged(cached: Stamp?, current: Stamp): Boolean {
        if (cached == null) return false
        if (!cached.isKnown || !current.isKnown) return false
        return cached.lastModified == current.lastModified && cached.size == current.size
    }

    /**
     * True when [current] differs from [cached] in a way that matters.
     *
     * Provided so callers can express "this is news" without double negatives;
     * it is exactly `!isUnchanged(cached, current)`.
     */
    fun isChanged(cached: Stamp?, current: Stamp): Boolean = !isUnchanged(cached, current)
}
