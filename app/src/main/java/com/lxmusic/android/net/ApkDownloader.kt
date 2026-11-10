package com.lxmusic.android.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads a release APK into a local file, streaming, with byte progress.
 *
 * Separate from [HttpBridge] because that one buffers the whole body in memory
 * before returning - fine for a few KB of JSON, fatal for a 16 MB installer.
 *
 * V1.1.8: an interrupted transfer resumes instead of starting over. Whatever
 * already reached disk stays in `<dest>.part`, and the next attempt asks the
 * server for the remainder with a `Range` header, so a download killed in a
 * tunnel costs the missing megabytes rather than all fifteen.
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
     * Streams [url] into [dest], resuming from an earlier partial file.
     *
     * Writes to `<dest>.part` and renames only after a complete transfer, so a
     * half-written file can never be mistaken for a finished APK. When a usable
     * `<dest>.part` exists and the server honours range requests, only the
     * missing tail is fetched; if the server answers with the whole body
     * instead, the partial file is discarded rather than appended to.
     *
     * [onProgress] receives (bytesWritten, totalBytes), both counting the whole
     * file including bytes carried over from the previous attempt. total is 0
     * when the server sends no Content-Length; callers should show an
     * indeterminate bar in that case.
     */
    fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit): Result<File> = runCatching {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + PART_SUFFIX)

        // Bytes already on disk from an earlier attempt; this is the resume point.
        var carried = if (part.exists()) part.length() else 0L

        var response = fetch(url, carried)
        var resume = false

        if (carried > 0L) {
            if (canAppend(response.code, carried)) {
                resume = true
            } else {
                // 200 means the server ignored Range and is sending the whole
                // file again; appending that to a partial file would produce a
                // corrupt APK. Start clean instead.
                response.close()
                part.delete()
                carried = 0L
                response = fetch(url, 0L)
            }
        }

        response.use { resp ->
            if (!resp.isSuccessful) error("下载失败：HTTP ${resp.code}")
            val body = resp.body ?: error("下载失败：响应为空")

            // A 206 response reports only the remaining length, so the finished
            // file is that plus whatever we already had.
            val declared = body.contentLength()
            val total = if (declared > 0L) declared + carried else 0L

            var written = carried
            onProgress(written, total)
            body.byteStream().use { input ->
                FileOutputStream(part, resume).use { out ->
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

            // A body that ended early is not a finished download: without this
            // check a dropped connection would rename a truncated file into place.
            if (total > 0L && written < total) {
                error("下载中断：已收到 $written / $total 字节，可重试继续")
            }
        }

        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) error("下载失败：无法写入安装包")
        dest
    }

    /** One HTTP exchange; the caller decides whether to keep or redo it. */
    private fun fetch(url: String, from: Long) = runCatching {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        if (from > 0L) builder.header("Range", "bytes=$from-")
        client.newCall(builder.build()).execute()
    }.getOrElse { throw it }

    companion object {
        private const val PART_SUFFIX = ".part"
        private const val BUFFER_SIZE = 64 * 1024
        private const val USER_AGENT = "jijian-music"

        /** Partial Content: the server honoured the range request. */
        const val HTTP_PARTIAL = 206

        /**
         * Whether a response to a ranged request can be appended to the bytes
         * already on disk.
         *
         * Only 206 does. Every other status - 200 (whole body, Range ignored),
         * 416 (the part file is longer than the asset, i.e. it belongs to
         * another version), 403/404 from a CDN edge - leaves the offset
         * meaning something else, so the safe answer is to start over. Getting
         * this wrong writes a corrupt APK that only fails at install time.
         */
        fun canAppend(statusCode: Int, carried: Long): Boolean =
            carried > 0L && statusCode == HTTP_PARTIAL

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
