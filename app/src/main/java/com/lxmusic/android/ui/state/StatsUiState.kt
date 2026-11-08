package com.lxmusic.android.ui.state

import com.lxmusic.android.data.stats.PlayRecord
import com.lxmusic.android.data.stats.PlayTally
import com.lxmusic.android.data.stats.StatsBucket
import com.lxmusic.android.data.stats.StatsRange

/**
 * The play-count statistics screen.
 *
 * [records] is the raw event list for the selected window; the tallies and the
 * histogram are derived from it so switching range only re-queries the database
 * instead of re-deriving everything twice.
 */
data class StatsUiState(
    val range: StatsRange = StatsRange.WEEK,
    val loading: Boolean = false,
    val records: List<PlayRecord> = emptyList(),
    val tallies: List<PlayTally> = emptyList(),
    val buckets: List<StatsBucket> = emptyList(),
    val totalPlays: Int = 0,
    val error: String? = null,
)
