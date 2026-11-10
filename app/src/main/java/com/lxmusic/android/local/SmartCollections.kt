package com.lxmusic.android.local

import com.lxmusic.android.data.model.MusicInfo

/**
 * One automatically assembled shelf of tracks.
 *
 * A collection is not a playlist: nobody created it, nothing is stored, and it
 * is recomputed from the library plus the play log every time it is asked for.
 * That is the whole point - a shelf that has to be curated is a shelf nobody
 * maintains.
 */
data class SmartCollection(
    val key: String,
    val title: String,
    /** Short descriptor shown under the title, already formatted for display. */
    val subtitle: String,
    val tracks: List<MusicInfo>,
) {
    val count: Int get() = tracks.size
    val isEmpty: Boolean get() = tracks.isEmpty()
}

/**
 * Builds the shelves: recently added, most played, never played, and long
 * forgotten.
 *
 * [played] is keyed by [MusicInfo.id] and carries what the play log knows about
 * that track. A track missing from the map has never been played - that is the
 * distinction "never played" is built on, so the map must not carry zero-count
 * entries.
 *
 * Everything here is a pure function of (tracks, played, now, timeZone), which
 * is why the thresholds are parameters rather than reads of the clock.
 */
object SmartCollections {

    /** "Recently added" looks back this far. */
    const val RECENT_DAYS = 30

    /** "Long forgotten" means played, but not in this many days. */
    const val STALE_DAYS = 90

    /** How many tracks a shelf holds. Long shelves stop being scannable. */
    const val SHELF_SIZE = 30

    /** What the play log knows about one track. */
    data class PlayStat(val count: Int, val lastPlayedAt: Long)

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /**
     * All four shelves, in display order, with empty ones dropped.
     *
     * Order is fixed rather than "smart": a shelf that moves around between
     * visits is a shelf the user has to re-find every time.
     */
    fun build(
        tracks: List<MusicInfo>,
        played: Map<String, PlayStat>,
        now: Long,
    ): List<SmartCollection> = listOf(
        recentlyAdded(tracks, now),
        mostPlayed(tracks, played),
        neverPlayed(tracks, played),
        longForgotten(tracks, played, now),
    ).filterNot { it.isEmpty }

    /**
     * Tracks whose file showed up in the last [RECENT_DAYS] days, newest first.
     *
     * `dateAdded` of 0 means the scanner never saw a date - an old cached row or
     * a provider that does not report one. Those are treated as "not recent"
     * rather than "added in 1970", otherwise the shelf fills with the whole
     * library on the first upgrade.
     */
    fun recentlyAdded(
        tracks: List<MusicInfo>,
        now: Long,
        days: Int = RECENT_DAYS,
    ): SmartCollection {
        val since = now - days * DAY_MS
        val picked = tracks
            .filter { it.dateAdded > since && it.dateAdded <= now }
            .sortedByDescending { it.dateAdded }
            .take(SHELF_SIZE)
        return SmartCollection(
            key = "recent",
            title = "最近添加",
            subtitle = "近 $days 天入库 · ${picked.size} 首",
            tracks = picked,
        )
    }

    /** The [SHELF_SIZE] tracks with the highest play count, most played first. */
    fun mostPlayed(
        tracks: List<MusicInfo>,
        played: Map<String, PlayStat>,
    ): SmartCollection {
        val picked = tracks
            .mapNotNull { t -> played[t.id]?.let { t to it } }
            .filter { it.second.count > 0 }
            // Ties break on recency so the order does not depend on the order
            // the library happened to be scanned in.
            .sortedWith(
                compareByDescending<Pair<MusicInfo, PlayStat>> { it.second.count }
                    .thenByDescending { it.second.lastPlayedAt },
            )
            .take(SHELF_SIZE)
            .map { it.first }
        return SmartCollection(
            key = "top",
            title = "最常播放",
            subtitle = picked.size.toString() + " 首 · 按播放次数",
            tracks = picked,
        )
    }

    /**
     * Tracks the play log has never seen, in library order.
     *
     * Only meaningful once something has been played: on a fresh install every
     * track is unplayed, and a shelf that is simply the whole library tells the
     * user nothing. So it stays empty until at least one track has a play.
     */
    fun neverPlayed(
        tracks: List<MusicInfo>,
        played: Map<String, PlayStat>,
    ): SmartCollection {
        val picked = if (played.isEmpty()) {
            emptyList()
        } else {
            tracks.filter { played[it.id] == null }.take(SHELF_SIZE)
        }
        return SmartCollection(
            key = "unplayed",
            title = "从未播放",
            subtitle = picked.size.toString() + " 首 · 库里还没轮到",
            tracks = picked,
        )
    }

    /**
     * Played before, but not in the last [STALE_DAYS] days, stalest first.
     *
     * This is the shelf that answers "what did I used to like" - it needs both
     * halves of the condition, so a track never played is not "forgotten", it is
     * merely unheard.
     */
    fun longForgotten(
        tracks: List<MusicInfo>,
        played: Map<String, PlayStat>,
        now: Long,
        days: Int = STALE_DAYS,
    ): SmartCollection {
        val before = now - days * DAY_MS
        val picked = tracks
            .mapNotNull { t -> played[t.id]?.let { t to it } }
            .filter { it.second.lastPlayedAt in 1 until before }
            .sortedBy { it.second.lastPlayedAt }
            .take(SHELF_SIZE)
            .map { it.first }
        return SmartCollection(
            key = "stale",
            title = "很久没听",
            subtitle = "超过 $days 天没播 · ${picked.size} 首",
            tracks = picked,
        )
    }
}
