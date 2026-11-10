package com.lxmusic.android.ui.components

import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * V1.4: 读屏文案是纯函数，所以能像别的东西一样断言。
 *
 * 这些测试盯的不是措辞好不好听，而是三件会被用户直接感知的事：动作词对不对
 * （"播放" vs "打开"）、空字段会不会念出"， ，"、以及隐藏的信息会不会被念出来。
 */
class TrackDescriptionTest {

    private fun track(
        name: String = "夜曲",
        singer: String = "周杰伦",
        album: String = "十一月的萧邦",
        interval: String = "03:24",
    ) = MusicInfo(
        id = "t1",
        source = MusicInfo.SOURCE_LOCAL,
        songmid = "t1",
        name = name,
        singer = singer,
        albumName = album,
        interval = interval,
    )

    @Test
    fun aRowSaysPlayThenTitleSingerAndAlbum() {
        assertEquals("播放 夜曲，周杰伦，十一月的萧邦", TrackDescription.row(track()))
    }

    @Test
    fun theDurationIsAppendedOnlyWhenTheRowShowsIt() {
        assertEquals(
            "播放 夜曲，周杰伦，十一月的萧邦，时长 03:24",
            TrackDescription.row(track(), duration = "03:24"),
        )
        assertEquals(
            "播放 夜曲，周杰伦，十一月的萧邦",
            TrackDescription.row(track(), duration = null),
        )
        assertEquals(
            "播放 夜曲，周杰伦，十一月的萧邦",
            TrackDescription.row(track(), duration = "   "),
        )
    }

    @Test
    fun aBlankTitleIsAnnouncedAsUnknownRatherThanSilence() {
        assertEquals("播放 未知曲目", TrackDescription.row(track(name = "", singer = "", album = "")))
    }

    @Test
    fun blankFieldsDoNotLeaveStraySeparators() {
        assertEquals("播放 夜曲，周杰伦", TrackDescription.row(track(album = "")))
        assertEquals("播放 夜曲，十一月的萧邦", TrackDescription.row(track(singer = "")))
    }

    @Test
    fun aCoverIsDescribedByItsOwner() {
        assertEquals("十一月的萧邦 的封面", TrackDescription.cover("十一月的萧邦"))
        assertNull(TrackDescription.cover(null))
        assertNull(TrackDescription.cover("   "))
    }

    @Test
    fun anAlbumHeaderReadsAsOneSentence() {
        assertEquals(
            "十一月的萧邦，共 12 首，时长 48 分",
            TrackDescription.group("十一月的萧邦", 12, "48 分"),
        )
    }

    @Test
    fun aNavigationRowSaysOpenAndNeverPlay() {
        val label = TrackDescription.open("十一月的萧邦", 12)
        assertEquals("打开 十一月的萧邦，12 首", label)
        assertEquals(false, label.startsWith("播放"))
    }

    @Test
    fun anUntitledGroupStillReadsAsSomething() {
        assertEquals("打开 未命名，3 首", TrackDescription.open("", 3))
    }

    @Test
    fun theResumeCardSaysContinueAndDropsTheAlbum() {
        assertEquals("继续播放 夜曲，周杰伦", TrackDescription.resume(track()))
    }

    @Test
    fun aCollectionCardSaysPlayAndItsSize() {
        assertEquals("播放 最常播放，30 首", TrackDescription.play("最常播放", 30))
        assertEquals("播放 未命名，0 首", TrackDescription.play("", 0))
    }

    @Test
    fun theQueueRowOverloadMatchesTheModelOne() {
        assertEquals(
            TrackDescription.row(track(album = "")),
            TrackDescription.row("夜曲", "周杰伦"),
        )
    }

    @Test
    fun theMiniPlayerSaysItOpensThePlayerInstead() {
        assertEquals("打开播放页 夜曲，周杰伦", TrackDescription.miniPlayer("夜曲", "周杰伦"))
        assertEquals("打开播放页 夜曲", TrackDescription.miniPlayer("夜曲", "  "))
        assertEquals("打开播放页 当前没有播放", TrackDescription.miniPlayer(null, null))
        assertEquals("打开播放页 当前没有播放", TrackDescription.miniPlayer("", ""))
    }
}
