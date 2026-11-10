package com.lxmusic.android.local

import com.lxmusic.android.data.model.MusicInfo

/**
 * A library prepared for repeated filtering.
 *
 * [LocalLibrary.search] lowercases three fields per track on every call, and
 * the caller calls it on **every keystroke** — once the library is a few
 * thousand tracks that is tens of thousands of string allocations per
 * character typed, all of it recomputing text that never changes between
 * keystrokes.
 *
 * This index does the lowercasing once, when the track list itself changes, and
 * then a query is a `contains` per already-lowered field. The sort is applied
 * once too, so [songs] is the single ordered list every later filter walks.
 *
 * Rebuild cost is O(n); query cost is O(n) but allocation-free. The index is
 * deliberately not a `StateFlow` or observable — it is a value the store
 * replaces wholesale, which keeps "when is this stale" a question with one
 * answer.
 */
class LocalIndex private constructor(
    val tracks: List<MusicInfo>,
    private val entries: List<Entry>,
) {

    /** One track plus the lowercased text a query is matched against. */
    private class Entry(
        val track: MusicInfo,
        val name: String,
        val singer: String,
        val album: String,
    )

    val size: Int get() = entries.size

    /**
     * Tracks matching [query], in index order.
     *
     * A blank query returns everything, which is the common case — the UI calls
     * this on every recomposition, not only when the user types.
     */
    fun search(query: String): List<MusicInfo> {
        if (query.isBlank()) return tracks
        val q = query.trim().lowercase()
        val out = ArrayList<MusicInfo>(minOf(entries.size, 64))
        for (e in entries) {
            if (e.name.contains(q) || e.singer.contains(q) || e.album.contains(q)) {
                out.add(e.track)
            }
        }
        return out
    }

    /** Albums of [subset], sharing the same key derivation as [LocalLibrary.byAlbum]. */
    fun albums(subset: List<MusicInfo>): List<MusicGroup> = LocalLibrary.byAlbum(subset)

    /** Artists of [subset], sharing the same key derivation as [LocalLibrary.byArtist]. */
    fun artists(subset: List<MusicInfo>): List<MusicGroup> = LocalLibrary.byArtist(subset)

    companion object {

        /**
         * Builds an index over [tracks] sorted by [order].
         *
         * The sort happens here, not at query time: the order only changes when
         * the user changes it, while the query changes per keystroke.
         */
        fun of(tracks: List<MusicInfo>, order: LocalSort): LocalIndex {
            val sorted = LocalLibrary.sort(tracks, order)
            val entries = sorted.map {
                Entry(
                    track = it,
                    name = it.name.lowercase(),
                    singer = it.singer.lowercase(),
                    album = it.albumName.lowercase(),
                )
            }
            return LocalIndex(sorted, entries)
        }
    }
}
