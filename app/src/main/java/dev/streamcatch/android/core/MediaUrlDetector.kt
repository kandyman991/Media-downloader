package dev.streamcatch.android.core

import java.net.URI
import java.util.Locale

/** Pure platform-independent media URL classification. It does not fetch content. */
enum class MediaKind(val label: String, val isDirect: Boolean, val extension: String) {
    MP4("MP4", true, "mp4"),
    WEBM("WebM", true, "webm"),
    OTHER_VIDEO("Video", true, "mp4"),
    HLS("HLS playlist", false, "m3u8"),
    DASH("DASH manifest", false, "mpd")
}

object MediaUrlDetector {
    fun classify(url: String, mimeType: String? = null): MediaKind? {
        val uri = parseHttps(url) ?: return null
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val type = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)

        return when {
            path.endsWith(".m3u8") || type in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl") -> MediaKind.HLS
            path.endsWith(".mpd") || type == "application/dash+xml" -> MediaKind.DASH
            path.endsWith(".mp4") || path.endsWith(".m4v") || type == "video/mp4" -> MediaKind.MP4
            path.endsWith(".webm") || type == "video/webm" -> MediaKind.WEBM
            listOf(".mov", ".mkv", ".3gp", ".mpeg", ".mpg").any(path::endsWith) || type?.startsWith("video/") == true -> MediaKind.OTHER_VIDEO
            else -> null
        }
    }

    fun parseHttps(url: String): URI? = try {
        val uri = URI(url)
        if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) uri else null
    } catch (_: Exception) {
        null
    }

    fun safeFilename(url: String, kind: MediaKind, nonce: Long): String {
        val name = parseHttps(url)?.path.orEmpty().substringAfterLast('/')
        val base = name.substringBeforeLast('.', missingDelimiterValue = name)
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            .trim('.', ' ', '_')
            .take(70)
            .ifBlank { "video" }
        val extension = when (kind) {
            MediaKind.MP4 -> "mp4"
            MediaKind.WEBM -> "webm"
            MediaKind.OTHER_VIDEO -> name.substringAfterLast('.', "mp4").lowercase(Locale.ROOT)
                .takeIf { it in setOf("mov", "mkv", "3gp", "mpeg", "mpg") } ?: "mp4"
            else -> kind.extension
        }
        return "${base}_${nonce}.$extension"
    }
}