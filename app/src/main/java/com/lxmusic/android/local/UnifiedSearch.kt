package com.lxmusic.android.local

import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.repo.PlaylistWithTracks

/**
 * Everything one query found, split by where it was found.
 *
 * The sections are deliberately not merged into one ranked list: a track, a
 * playlist and an album are different kinds of answer, and a user who typed
 * "夜" is asking a different question depending on which section they look at.
 * Rows inside a section keep the order they arrived in (the library's own sort
 * for tracks), so the page does not reshuffle as the query is refined.
 */
data class SearchResults(
    val query: String = "",
    /** Tracks whose title, artist or album matched, in library order. */
    val tracks: List<MusicInfo> = emptyList(),
    /** Matching favourites that are not already in [tracks]. */
    val favorites: List<MusicInfo> = emptyList(),
    /** Albums owning at least one matching track. */
    val albums: List<MusicGroup> = emptyList(),
    /** Artists owning at least one matching track. */
    val artists: List<MusicGroup> = emptyList(),
    /** Playlists whose name or contents matched. */
    val playlists: List<PlaylistWithTracks> = emptyList(),
) {
    val isEmpty: Boolean
        get() = query.isBlank() ||
            (tracks.isEmpty() && favorites.isEmpty() && albums.isEmpty() &&
                artists.isEmpty() && playlists.isEmpty())

    /** Section headers say "N", so this counts every row the page will draw. */
    val totalCount: Int
        get() = tracks.size + favorites.size + albums.size + artists.size + playlists.size
}

/**
 * The one search behind the search page.
 *
 * [tracks] arrives already filtered - the caller owns the library index and its
 * sort, and re-matching here would mean a second implementation of "what counts
 * as a hit" that could drift from the first. What this object does own is the
 * other three sections, which the library index knows nothing about.
 */
object UnifiedSearch {

    /** Albums / artists are a shortcut, not a result page - six is enough. */
    const val MAX_GROUPS = 6

    const val MAX_PLAYLISTS = 6

    fun run(
        query: String,
        tracks: List<MusicInfo>,
        favorites: List<MusicInfo>,
        playlists: List<PlaylistWithTracks>,
    ): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults(query = query)
        val needle = q.lowercase()
        val trackIds = tracks.mapTo(HashSet(tracks.size)) { it.id }
        return SearchResults(
            query = query,
            tracks = tracks,
            // A favourite already listed under 歌曲 would otherwise appear twice
            // on one page, which reads as a bug rather than as two sections.
            favorites = favorites.filter { it.id !in trackIds && matches(needle, it) },
            // Grouped from the *matching* tracks: "which of my albums contain
            // this" is the question, and an album with no match is not an answer.
            albums = LocalLibrary.byAlbum(tracks).take(MAX_GROUPS),
            artists = LocalLibrary.byArtist(tracks).take(MAX_GROUPS),
            playlists = playlists.filter { matches(needle, it) }.take(MAX_PLAYLISTS),
        )
    }

    private fun matches(needle: String, m: MusicInfo): Boolean =
        m.name.lowercase().contains(needle) ||
            m.singer.lowercase().contains(needle) ||
            m.albumName.lowercase().contains(needle)

    /**
     * A playlist matches by its own name or by anything inside it.
     *
     * The contents matter: "夜" should surface the playlist that holds three
     * songs called 夜…, even if the playlist itself is called 「晚上听」.
     */
    private fun matches(needle: String, p: PlaylistWithTracks): Boolean =
        p.name.lowercase().contains(needle) || p.tracks.any { matches(needle, it) }
}
