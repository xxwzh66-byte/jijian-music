package com.lxmusic.android.ui.components

import com.lxmusic.android.data.model.MusicInfo

/**
 * What a row says when you cannot see it.
 *
 * V1.4. Before this, every list row was a pile of anonymous `Text` nodes and
 * every cover an `AsyncImage` with `contentDescription = null`: a screen reader
 * read the title, then the singer, then the album, as three separate stops, and
 * never said that the row plays something. One merged description per row is
 * both shorter to listen to and honest about the action.
 *
 * Kept as pure functions (no Compose imports) so the wording is testable.
 */
object TrackDescription {

    /**
     * One-line description of a track row: "播放 歌曲名，歌手，专辑，时长 3:24".
     *
     * [duration] is passed in rather than read from the track because the row
     * hides it on COMPACT lists — announcing a duration the screen does not
     * show would describe a different row than the one being read.
     */
    fun row(music: MusicInfo, duration: String? = null): String {
        val parts = buildList {
            add(music.name.ifBlank { "未知曲目" })
            if (music.singer.isNotBlank()) add(music.singer)
            if (music.albumName.isNotBlank()) add(music.albumName)
        }
        val tail = duration?.takeIf { it.isNotBlank() }?.let { "，时长 $it" } ?: ""
        return "播放 " + parts.joinToString("，") + tail
    }

    /**
     * Description for a cover image.
     *
     * A cover carries no information a sighted user needs beyond "this is the
     * artwork of X", so it is described by its owner rather than by itself —
     * otherwise every list reads its title twice.
     */
    fun cover(owner: String?): String? =
        owner?.takeIf { it.isNotBlank() }?.let { "$it 的封面" }

    /** "共 N 首，时长 X" for an album or artist header. */
    fun group(title: String, count: Int, totalDuration: String): String =
        "$title，共 $count 首，时长 $totalDuration"

    /**
     * "打开 名称，N 首" for a row that navigates somewhere rather than playing.
     *
     * A row that opens an album must not say "播放": the action word is the one
     * part of the description a listener acts on, and getting it wrong sends
     * them into a page they did not ask for.
     */
    fun open(title: String, count: Int): String =
        "打开 ${title.ifBlank { "未命名" }}，$count 首"

    /** "继续播放 歌曲名，歌手" for the resume card at the top of the library. */
    fun resume(music: MusicInfo): String {
        val parts = buildList {
            add(music.name.ifBlank { "未知曲目" })
            if (music.singer.isNotBlank()) add(music.singer)
        }
        return "继续播放 " + parts.joinToString("，")
    }

    /**
     * Same wording as [row] for callers that only kept the two text fields.
     *
     * The queue row carries name and singer as plain strings (it never holds a
     * whole [MusicInfo]), so it cannot call [row] — and an overload is cheaper
     * than teaching the queue about the model it does not otherwise need.
     */
    fun row(name: String, singer: String): String {
        val parts = buildList {
            add(name.ifBlank { "未知曲目" })
            if (singer.isNotBlank()) add(singer)
        }
        return "播放 " + parts.joinToString("，")
    }

    /**
     * "打开播放页 歌名，歌手" for the mini player.
     *
     * The bar looks like a row that plays, but tapping it opens the full player
     * instead — the description has to say so, or a listener taps expecting
     * playback and lands on a page with no obvious way back.
     */
    fun miniPlayer(name: String?, singer: String?): String {
        val parts = buildList {
            add(name?.takeIf { it.isNotBlank() } ?: "当前没有播放")
            singer?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        return "打开播放页 " + parts.joinToString("，")
    }

    /** "播放 名称，N 首" for a card that starts a whole collection right away. */
    fun play(owner: String, count: Int): String =
        "播放 ${owner.ifBlank { "未命名" }}，$count 首"
}
