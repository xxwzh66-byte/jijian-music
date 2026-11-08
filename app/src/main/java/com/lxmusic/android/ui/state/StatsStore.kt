package com.lxmusic.android.ui.state

import com.lxmusic.android.data.repo.MusicRepository
import com.lxmusic.android.data.stats.AnnualReport
import com.lxmusic.android.data.stats.PlayStats
import com.lxmusic.android.data.stats.StatsRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the play statistics screen: the selected window and the rows derived
 * from it. The aggregation itself lives in [PlayStats], which is pure, so this
 * class only fetches the window and hands it over.
 */
class StatsStore(
    private val repo: MusicRepository,
    private val scope: CoroutineScope,
) {

    private val _stats = MutableStateFlow(StatsUiState())
    val stats: StateFlow<StatsUiState> = _stats.asStateFlow()

    /**
     * This calendar year, summarised. Kept apart from [stats] because it is not
     * tied to the range chips - switching 今日/本周 must not re-read a year of
     * events, and the yearly card must not blink out while a range loads.
     */
    private val _annual = MutableStateFlow<AnnualReport?>(null)
    val annual: StateFlow<AnnualReport?> = _annual.asStateFlow()

    /** Switches the window and re-reads only that window's events. */
    fun setRange(range: StatsRange) {
        if (_stats.value.range == range) return
        _stats.value = _stats.value.copy(range = range)
        refresh()
    }

    fun refresh() {
        refreshAnnual()
        scope.launch {
            _stats.value = _stats.value.copy(loading = true, error = null)
            val range = _stats.value.range
            val now = System.currentTimeMillis()
            try {
                val records = repo.playRecords(PlayStats.windowStart(range, now), now)
                _stats.value = _stats.value.copy(
                    loading = false,
                    records = records,
                    tallies = PlayStats.tally(records),
                    buckets = PlayStats.buckets(records, range, now),
                    totalPlays = PlayStats.totalPlays(records),
                    error = null,
                )
            } catch (e: Exception) {
                _stats.value = _stats.value.copy(loading = false, error = e.message)
            }
        }
    }

    /** Re-reads the current year. Cheap enough: one indexed range scan. */
    fun refreshAnnual() {
        scope.launch {
            val year = PlayStats.currentYear()
            val records = repo.playRecords(PlayStats.yearStart(year), System.currentTimeMillis())
            _annual.value = PlayStats.annualReport(records, year)
        }
    }

    /** Wipes the play events; the tallies and the chart go empty with them. */
    fun clear() {
        scope.launch {
            repo.clearPlayEvents()
            refresh()
        }
    }

    /**
     * One year of listening, summarised. Re-reads the whole retention window
     * because the yearly card is not tied to the range chips.
     */
    fun annualReport(year: Int, onDone: (AnnualReport) -> Unit) {
        scope.launch {
            val now = System.currentTimeMillis()
            val from = PlayStats.yearStart(year)
            val records = repo.playRecords(from, now)
            onDone(PlayStats.annualReport(records, year))
        }
    }
}
