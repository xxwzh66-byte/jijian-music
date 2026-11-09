package com.lxmusic.android.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V1.0.2: 「检查更新」的纯逻辑部分（解析 + 版本比较）。
 *
 * 这里刻意不碰网络：`fetchLatest` 的 HTTP 分派依赖 OkHttp 的真实连接，
 * 难以在 JVM 上断言；可断言的是「拿到一段 JSON 之后怎么读」和
 * 「两个版本号谁大」，这两块错了界面就会说反话。
 */
class UpdateCheckerTest {

    // ---------------- compareVersions ----------------

    @Test
    fun theLeadingVPrefixIsNotPartOfTheVersion() {
        assertEquals(0, UpdateChecker.compareVersions("v1.0.1", "1.0.1"))
        assertEquals(0, UpdateChecker.compareVersions("V1.0.1", "v1.0.1"))
    }

    @Test
    fun segmentsCompareAsNumbersNotAsText() {
        // 文本比较会得出 "1.0.10" < "1.0.2"，那就永远提示不到 1.0.10。
        assertTrue(UpdateChecker.compareVersions("1.0.10", "1.0.2") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.2", "1.0.10") < 0)
        assertTrue(UpdateChecker.compareVersions("2.0.0", "1.9.9") > 0)
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertEquals(0, UpdateChecker.compareVersions("1.0", "1.0.0"))
        assertEquals(0, UpdateChecker.compareVersions("1", "1.0.0"))
        assertTrue(UpdateChecker.compareVersions("1.0.1", "1") > 0)
    }

    @Test
    fun aReleaseWithoutSuffixBeatsAPrerelease() {
        assertTrue(UpdateChecker.compareVersions("1.0.2", "1.0.2-beta") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.2-beta", "1.0.2") < 0)
    }

    @Test
    fun aLongerVersionIsNewerWhenThePrefixMatches() {
        assertTrue(UpdateChecker.compareVersions("1.0.2.1", "1.0.2") > 0)
    }

    @Test
    fun isNewerOnlySaysYesForAGenuinelyNewerTag() {
        assertTrue(UpdateChecker.isNewer("v1.0.3", "1.0.2"))
        assertFalse(UpdateChecker.isNewer("v1.0.1", "1.0.2"))
        assertFalse(UpdateChecker.isNewer("1.0.2", "1.0.2"))
    }

    // ---------------- parseRelease ----------------

    private val full = """
        {
          "tag_name": "v1.0.1",
          "name": "极简music 1.0.1 (vc2)",
          "body": "修复三处。\n\n- 上一首\n- 耳机\n- 顺序",
          "html_url": "https://github.com/xxwzh66-byte/jijian-music/releases/tag/v1.0.1",
          "assets": [
            {"name": "source.zip", "size": 1234},
            {"name": "jijian-music-1.0.1-vc2-debug.apk", "size": 23399318}
          ]
        }
    """.trimIndent()

    @Test
    fun parseReleasePicksTheOnlyApkAndIgnoresEverythingElse() {
        val info = UpdateChecker.parseRelease(full)
        assertEquals("v1.0.1", info.tagName)
        assertEquals("极简music 1.0.1 (vc2)", info.title)
        assertEquals("jijian-music-1.0.1-vc2-debug.apk", info.apkName)
        assertEquals(23399318L, info.apkSize)
        assertEquals(
            "https://github.com/xxwzh66-byte/jijian-music/releases/tag/v1.0.1",
            info.url,
        )
        // 更新内容公告的正文就是 release body，原样交给对话框。
        assertTrue(info.notes.contains("上一首"))
    }

    @Test
    fun theReleaseBuildWinsWhenBothApksArePublished() {
        // 实际情况就是这样：debug 包在资产列表里排前面，而它用 debug key
        // 签名，装到正式版上必然签名冲突，所以不能按顺序取第一个。
        val info = UpdateChecker.parseRelease(
            """
            {
              "tag_name": "v1.1.6",
              "assets": [
                {"name": "jijian-music-1.1.6-vc11-debug.apk", "size": 23038164,
                 "browser_download_url": "https://example/debug.apk",
                 "digest": "sha256:aaa"},
                {"name": "jijian-music-1.1.6-vc11.apk", "size": 15882842,
                 "browser_download_url": "https://example/release.apk",
                 "digest": "sha256:bbb"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals("jijian-music-1.1.6-vc11.apk", info.apkName)
        assertEquals(15882842L, info.apkSize)
        assertEquals("https://example/release.apk", info.apkUrl)
        assertEquals("sha256:bbb", info.apkDigest)
    }

    @Test
    fun theDebugBuildIsStillUsableWhenItIsAllThereIs() {
        val info = UpdateChecker.parseRelease(
            """{"tag_name":"v1.0.1","assets":[{"name":"app-debug.apk","size":5,
               "browser_download_url":"https://example/a.apk"}]}""",
        )
        assertEquals("app-debug.apk", info.apkName)
        assertEquals("https://example/a.apk", info.apkUrl)
    }

    @Test
    fun parseReleaseReadsTheDownloadUrlAndDigestOfTheChosenApk() {
        val info = UpdateChecker.parseRelease(
            """
            {"tag_name":"v1.1.7","assets":[
              {"name":"jijian-music-1.1.7-vc12.apk","size":12345,
               "browser_download_url":"https://example/dl/1.1.7.apk",
               "digest":"sha256:deadbeef"}
            ]}
            """.trimIndent(),
        )
        assertEquals("https://example/dl/1.1.7.apk", info.apkUrl)
        assertEquals("sha256:deadbeef", info.apkDigest)
    }

    @Test
    fun aReleaseWithoutADigestLeavesItNull() {
        val info = UpdateChecker.parseRelease(
            """{"tag_name":"v1.0.0","assets":[{"name":"app.apk","size":10,
               "browser_download_url":"https://example/a.apk"}]}""",
        )
        assertEquals("https://example/a.apk", info.apkUrl)
        assertNull(info.apkDigest)
    }

    @Test
    fun parseReleaseFallsBackToTheTagNameWhenTheTitleIsBlank() {
        val info = UpdateChecker.parseRelease("""{"tag_name":"v1.0.2","name":"   "}""")
        assertEquals("v1.0.2", info.tagName)
        assertEquals("v1.0.2", info.title)
    }

    @Test
    fun parseReleaseDegradesInsteadOfThrowingOnMissingFields() {
        val info = UpdateChecker.parseRelease("{}")
        assertEquals("", info.tagName)
        assertEquals("", info.notes)
        assertEquals("", info.url)
        assertNull(info.apkName)
        assertEquals(0L, info.apkSize)
    }

    @Test
    fun parseReleaseReportsNoApkWhenTheAssetsHaveNone() {
        val info = UpdateChecker.parseRelease(
            """{"tag_name":"v1.0.1","assets":[{"name":"notes.txt","size":10}]}""",
        )
        assertNull(info.apkName)
        assertEquals(0L, info.apkSize)
    }

    @Test
    fun parseReleaseIsCaseInsensitiveAboutTheApkSuffix() {
        val info = UpdateChecker.parseRelease(
            """{"tag_name":"v1.0.1","assets":[{"name":"app.APK","size":5}]}""",
        )
        assertEquals("app.APK", info.apkName)
    }
}
