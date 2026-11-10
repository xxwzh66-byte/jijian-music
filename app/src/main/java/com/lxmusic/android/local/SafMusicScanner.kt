package com.lxmusic.android.local

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.lxmusic.android.data.model.MusicInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Scans user-authorised folders (SAF) for audio files.
 *
 * Folders are granted with [android.content.Intent.ACTION_OPEN_DOCUMENT_TREE] and
 * must be persisted with
 * [android.content.ContentResolver.takePersistableUriPermission], otherwise the
 * grant disappears after a reboot.
 */
class SafMusicScanner(private val context: Context) {

    companion object {
        private const val TAG = "SafMusicScanner"

        /** Extensions we treat as playable audio. */
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "wma", "ape", "mp4", "mka",
        )

        /** Directory names never descended into. */
        private val SKIP_DIRS = setOf("Android", ".thumbnails")

        fun extensionOf(name: String): String =
            name.substringAfterLast('.', "").lowercase()

        fun isAudioFile(name: String, mime: String?): Boolean {
            if (mime != null && mime.startsWith("audio/")) return true
            return extensionOf(name) in AUDIO_EXTENSIONS
        }
    }

    /** Cached cover directory (cacheDir/covers). */
    private val coverDir: File by lazy {
        File(context.cacheDir, "covers").apply { if (!exists()) mkdirs() }
    }

    /**
     * Scans every tree in [treeUris].
     *
     * @param cached rows from the previous scan, keyed by [MusicInfo.id]. A row
     *   whose file still carries the same lastModified/size is reused as-is
     *   instead of re-reading its tags — see [IncrementalScan] for why both
     *   numbers have to match.
     * @param onProgress called with the running track count so the UI can show progress.
     * @param onReused called once per cached row that was reused, so the caller
     *   can report "skipped N unchanged" rather than silently doing less work.
     */
    suspend fun scanFolders(
        treeUris: List<String>,
        readEmbeddedCover: Boolean = true,
        cached: Map<String, MusicInfo> = emptyMap(),
        onProgress: (Int) -> Unit = {},
        onReused: (Int) -> Unit = {},
    ): List<MusicInfo> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, MusicInfo>()
        val reuse = ReuseTracker(cached, onReused)
        for (tree in treeUris) {
            coroutineContext.ensureActive()
            val uri = runCatching { Uri.parse(tree) }.getOrNull() ?: continue
            val root = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull() ?: continue
            walk(root, "folder", readEmbeddedCover, out, onProgress, reuse)
        }
        out.values.toList()
    }

    /**
     * Counts cache hits so the scan can say what it skipped.
     *
     * Kept as a small mutable holder rather than a `var` in the call site
     * because the recursion in [walk] needs the same counter at every depth.
     */
    private class ReuseTracker(
        private val cached: Map<String, MusicInfo>,
        private val onReused: (Int) -> Unit,
    ) {
        private var hits = 0

        fun tryReuse(id: String, stamp: IncrementalScan.Stamp): MusicInfo? {
            val row = cached[id] ?: return null
            if (!IncrementalScan.isUnchanged(row.stampOrNull(), stamp)) return null
            hits++
            onReused(hits)
            return row
        }
    }

    /** Reads metadata for explicitly added single files (T3). */
    suspend fun readSingle(uri: Uri, readEmbeddedCover: Boolean = true): MusicInfo? =
        withContext(Dispatchers.IO) {
            val file = DocumentFile.fromSingleUri(context, uri) ?: return@withContext null
            runCatching { readFile(file, uri.toString(), "manual", readEmbeddedCover) }.getOrNull()
        }

    private suspend fun walk(
        dir: DocumentFile,
        origin: String,
        readEmbeddedCover: Boolean,
        out: MutableMap<String, MusicInfo>,
        onProgress: (Int) -> Unit,
        reuse: ReuseTracker,
    ) {
        coroutineContext.ensureActive()
        val children = runCatching { dir.listFiles() }.getOrElse { return }
        for (child in children) {
            coroutineContext.ensureActive()
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            if (child.isDirectory) {
                if (name in SKIP_DIRS) continue
                if (File(name).name == ".nomedia") continue
                walk(child, origin, readEmbeddedCover, out, onProgress, reuse)
            } else if (isAudioFile(name, child.type)) {
                val uriString = child.uri.toString()
                val docId = documentIdOf(uriString)
                val id = trackIdOf(docId)
                // The listing already gave us mtime and size; only pay for
                // MediaMetadataRetriever when those say the file changed.
                val stamp = IncrementalScan.Stamp(child.lastModified(), child.length())
                val info = reuse.tryReuse(id, stamp)
                    ?: runCatching { readFile(child, uriString, origin, readEmbeddedCover, docId, id) }.getOrNull()
                if (info != null) {
                    out[info.id] = info
                    onProgress(out.size)
                }
            }
        }
    }

    private fun documentIdOf(uriString: String): String =
        runCatching { DocumentsContract.getDocumentId(Uri.parse(uriString)) }.getOrNull() ?: uriString

    private fun trackIdOf(docId: String): String = "saf_" + md5(docId).take(16)

    private fun readFile(
        file: DocumentFile,
        uriString: String,
        origin: String,
        readEmbeddedCover: Boolean,
        docId: String = documentIdOf(uriString),
        id: String = trackIdOf(docId),
    ): MusicInfo? {
        val name = file.name ?: "Unknown"

        var title = name.substringBeforeLast('.')
        var artist = "未知歌手"
        var album = ""
        var durationMs = 0L
        var coverPath: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uriString))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }?.let { album = it }
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (readEmbeddedCover) {
                coverPath = retriever.embeddedPicture?.let { saveCover(id, it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "metadata failed for $uriString: ${e.message}")
        } finally {
            runCatching { retriever.release() }
        }

        val seconds = (durationMs / 1000).toInt()
        return MusicInfo(
            id = id,
            source = MusicInfo.SOURCE_LOCAL,
            songmid = docId,
            name = title,
            singer = artist,
            albumName = album,
            interval = formatTime(seconds.toLong()),
            intervalSeconds = seconds,
            img = coverPath,
            localPath = uriString,
            localDurationMs = durationMs,
            localAlbumId = album,
            dateAdded = System.currentTimeMillis(),
            extra = mapOf(
                "uri" to uriString,
                "documentId" to docId,
                "origin" to origin,
                "lastModified" to file.lastModified().toString(),
                "size" to file.length().toString(),
            ),
        )
    }

    /** Persists embedded artwork into cacheDir/covers; returns the file path. */
    private fun saveCover(id: String, bytes: ByteArray): String? = runCatching {
        val f = File(coverDir, "$id.jpg")
        if (!f.exists()) {
            FileOutputStream(f).use { it.write(bytes) }
        }
        f.absolutePath
    }.getOrNull()

    private fun formatTime(seconds: Long): String =
        if (seconds <= 0) "00:00" else "%02d:%02d".format(seconds / 60, seconds % 60)

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}

/** The stamp a cached row was written with, or null when it carries none. */
private fun MusicInfo.stampOrNull(): IncrementalScan.Stamp? {
    val mtime = extra["lastModified"]?.toLongOrNull() ?: return null
    val size = extra["size"]?.toLongOrNull() ?: return null
    return IncrementalScan.Stamp(mtime, size)
}
