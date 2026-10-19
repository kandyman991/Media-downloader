package dev.streamcatch.android

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns

/**
 * Library entries belong either to StreamCatch's own MediaStore downloads or to
 * explicitly user-granted Storage Access Framework documents. No broad device
 * storage permissions, filesystem crawling, or automatic remote media access.
 */
data class OfflineMedia(val uri: Uri, val title: String, val size: Long, val savedAt: Long, val imported: Boolean)

object OfflineMediaLibrary {
    private const val PREFS = "streamcatch_media_center"
    private const val IMPORTS = "saf_imported_uris"
    private val supported = setOf("mp4", "m4v", "webm", "mkv", "ts", "mov", "mp3", "m4a", "flac", "ogg")

    fun supports(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in supported
    }

    fun rememberDocument(context: Context, uri: Uri) {
        require(uri.scheme == "content") { "Only user-selected content URIs may be imported" }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(IMPORTS, (prefs.getStringSet(IMPORTS, emptySet()).orEmpty() + uri.toString()).toSet()).apply()
    }

    fun list(context: Context): List<OfflineMedia> {
        val result = LinkedHashMap<String, OfflineMedia>()
        val resolver = context.contentResolver
        val columns = arrayOf(
            MediaStore.Downloads._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED
        )
        try {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                columns,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("%/StreamCatch/%"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext() && result.size < 1000) {
                    val name = cursor.getString(nameCol) ?: continue
                    if (!supports(name)) continue
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(idCol))
                    result[uri.toString()] = OfflineMedia(uri, name, cursor.getLong(sizeCol), cursor.getLong(dateCol) * 1000L, false)
                }
            }
        } catch (_: SecurityException) {
            // Android's scoped-storage rules may restrict older downloads.
            // Explicit SAF selection below still works without broad permissions.
        }

        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(IMPORTS, emptySet()).orEmpty()
        for (text in saved.take(200)) {
            val uri = try { Uri.parse(text) } catch (_: Exception) { continue }
            if (uri.scheme != "content") continue
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (!c.moveToFirst()) return@use
                    val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) c.getString(nameIndex) ?: "Imported video" else "Imported video"
                    if (!supports(name)) return@use
                    val size = if (sizeIndex >= 0 && !c.isNull(sizeIndex)) c.getLong(sizeIndex) else -1L
                    result[uri.toString()] = OfflineMedia(uri, name, size, 0L, true)
                }
            } catch (_: Exception) {
                // The user can revoke an SAF grant; do not crash the library.
            }
        }
        return result.values.sortedWith(compareByDescending<OfflineMedia> { it.savedAt }.thenBy { it.title })
    }
}
