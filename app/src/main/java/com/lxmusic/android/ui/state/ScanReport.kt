package com.lxmusic.android.ui.state

/**
 * What the last finished library scan actually did.
 *
 * Reported because an incremental scan's success is invisible: it finishes fast
 * and finds the same tracks. Without the read/reused split there is no way to
 * tell "skipped 2 400 unchanged files" from "scanned 2 400 files and nothing
 * changed".
 */
data class ScanReport(
    val total: Int = 0,
    /** Cached rows handed back untouched because mtime and size both matched. */
    val reused: Int = 0,
    /** Files that actually went through MediaMetadataRetriever. */
    val read: Int = 0,
    val elapsedMs: Long = 0,
) {
    /** False before the first scan, so the UI can stay quiet. */
    val hasRun: Boolean get() = total > 0 || reused > 0 || read > 0

    /** Fraction of the scan that was cache hits, 0f when nothing was scanned. */
    val reusedFraction: Float
        get() {
            val sum = reused + read
            return if (sum <= 0) 0f else reused.toFloat() / sum
        }

    val elapsedText: String
        get() = if (elapsedMs < 1000) "${elapsedMs} ms" else "%.1f s".format(elapsedMs / 1000f)
}
