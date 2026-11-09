package com.lxmusic.android.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads a release APK into a local file, streaming, with byte progress.
 *
 * Separate from [HttpBridge] because that one buffers the whole body in memory
 * before returning - fine for a few KB of JSON, fatal for a 16 MB installer.
 */
class ApkDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build(),
) {

    /**
     * Streams [url] into [dest], replacing whatever was there.
     *
     * Writes to `<dest>.part` first and renames on success, so an interrupted
     * download can never be mistaken for a finished APK. [onProgress] is called
     * with (bytesWritten, totalBytes); total is 0 when the server sends no
     * Content-Length, and callers should fall back to an indeterminate bar.
     */
    fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit): Result<File> = runCatching {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + PART_SUFFIX)
        if (part.exists()) part.delete()

        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("下载失败：HTTP ${resp.code}")
            val body = resp.body ?: error("下载失败：响应为空")
            val total = body.contentLength()
            var written = 0L
            onProgress(0L, total)
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        written += n
                        onProgress(written, total)
                    }
                }
            }
        }
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) error("下载失败：无法写入安装包")
        dest
    }

    companion object {
        private const val PART_SUFFIX = ".part"
        private const val BUFFER_SIZE = 64 * 1024
        private const val USER_AGENT = "jijian-music"

        /**
         * SHA-256 of [bytes] as lowercase hex. GitHub publishes release assets
         * with a `digest` of the form `sha256:<hex>`.
         */
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }

        /** SHA-256 of a finished file; null when it cannot be read. */
        fun sha256(file: File): String? = runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()

        /**
         * True when the file's digest matches the release's published digest.
         *
         * A missing or unrecognised [expected] digest counts as a pass: the
         * check exists to catch a truncated or substituted download, and a
         * release published without one should still be installable.
         */
        fun digestMatches(expected: String?, actualSha256: String?): Boolean {
            val want = expected?.trim()?.removePrefix("sha256:")?.lowercase()
            // Nothing published to compare against: the file is as good as it gets.
            if (want.isNullOrBlank()) return true
            // Published but unreadable is a failure, not a pass - that is
            // exactly the case a corrupted download shows up as.
            if (actualSha256 == null) return false
            return want == actualSha256.lowercase()
        }

        /** 0f..1f, or 0f when the size is unknown. */
        fun fraction(written: Long, total: Long): Float {
            if (total <= 0L) return 0f
            return (written.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
        }

        /** Best file name for a downloaded asset; never empty, always one `.apk`. */
        fun fileNameFor(apkName: String?): String {
            val name = apkName?.trim()?.substringAfterLast('/')?.substringAfterLast('\\')
            if (name.isNullOrBlank()) return FALLBACK_NAME
            return if (name.endsWith(".apk", ignoreCase = true)) name else "$name.apk"
        }

        const val FALLBACK_NAME = "jijian-music-update.apk"
    }
}
