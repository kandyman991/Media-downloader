package dev.streamcatch.android.core

import java.net.URI
import java.util.Locale

/** Supports unencrypted, single-rendition MPEG-TS VOD streams. Refuse unsupported media safely. */
sealed class HlsPlaylist {
    data class Master(val variants: List<HlsVariant>) : HlsPlaylist()
    data class Video(val segments: List<String>) : HlsPlaylist()
}

data class HlsVariant(val url: String, val bandwidth: Long, val resolution: String?, val codecs: String?) {
    val displayName: String get() = listOfNotNull(
        resolution,
        bandwidth.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
        codecs?.take(35)
    ).joinToString(" · ").ifBlank { "Auto" }
}

class UnsupportedHlsException(message: String) : IllegalArgumentException(message)

object HlsPlaylistParser {
    private const val MAX_PLAYLIST_CHARS = 2_000_000
    private const val MAX_VARIANTS = 48
    private const val MAX_SEGMENTS = 30_000

    fun resolve(baseUrl: String, target: String): String {
        require(target.isNotBlank()) { "Empty HLS URI" }
        val base = MediaUrlDetector.parseHttps(baseUrl) ?: throw UnsupportedHlsException("Invalid HTTPS playlist")
        val uri = base.resolve(target.trim())
        if (MediaUrlDetector.parseHttps(uri.toString()) == null ||
            uri.userInfo != null || uri.fragment != null) {
            throw UnsupportedHlsException("Only safe HTTPS HLS links are supported")
        }
        return uri.toString()
    }

    fun parse(sourceUrl: String, text: String): HlsPlaylist {
        require(MediaUrlDetector.parseHttps(sourceUrl) != null) { "Invalid playlist URL" }
        require(text.length in 1..MAX_PLAYLIST_CHARS) { "Playlist is empty or too large" }
        val lines = text.removePrefix("\uFEFF").lines().map(String::trim).filter(String::isNotBlank)
        require(lines.firstOrNull() == "#EXTM3U") { "Not an HLS playlist" }
        return if (lines.any { it.startsWith("#EXT-X-STREAM-INF:") }) parseMaster(sourceUrl, lines)
        else parseMedia(sourceUrl, lines)
    }

    private fun parseMaster(url: String, lines: List<String>): HlsPlaylist.Master {
        val variants = mutableListOf<HlsVariant>()
        var meta: Map<String, String>? = null
        for (line in lines.drop(1)) {
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> {
                    if (meta != null) throw UnsupportedHlsException("Missing variant URL")
                    meta = attributes(line.substringAfter(':'))
                }
                line.startsWith("#") -> Unit
                meta != null -> {
                    val attrs = meta
                    if (attrs.containsKey("AUDIO")) {
                        throw UnsupportedHlsException("Separate audio tracks are not supported yet")
                    }
                    val bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L
                    val res = attrs["RESOLUTION"]?.takeIf { it.matches(Regex("\\d{2,5}x\\d{2,5}")) }
                    variants.add(HlsVariant(resolve(url, line), bandwidth, res, attrs["CODECS"]))
                    if (variants.size > MAX_VARIANTS) throw UnsupportedHlsException("Too many variants")
                    meta = null
                }
                else -> throw UnsupportedHlsException("Unrecognized master playlist URI")
            }
        }
        if (meta != null || variants.isEmpty()) throw UnsupportedHlsException("Master has no usable variants")
        return HlsPlaylist.Master(variants.sortedWith(compareByDescending<HlsVariant> { it.bandwidth }.thenByDescending { it.resolution ?: "" }))
    }

    private fun parseMedia(url: String, lines: List<String>): HlsPlaylist.Video {
        if ("#EXT-X-ENDLIST" !in lines) throw UnsupportedHlsException("Live streams are not supported yet")
        if (lines.any { it.startsWith("#EXT-X-MAP:") }) throw UnsupportedHlsException("Fragmented MP4 HLS is not supported yet")
        if (lines.any { it.startsWith("#EXT-X-BYTERANGE:") || it.startsWith("#EXT-X-I-FRAMES-ONLY") ||
                it.startsWith("#EXT-X-DISCONTINUITY") || it.startsWith("#EXT-X-PART:") }) {
            throw UnsupportedHlsException("This advanced HLS playlist is not supported yet")
        }
        val segments = mutableListOf<String>()
        var durationPending = false
        for (line in lines.drop(1)) {
            when {
                line.startsWith("#EXT-X-KEY:") -> {
                    val attrs = attributes(line.substringAfter(':'))
                    if (attrs["METHOD"]?.uppercase(Locale.ROOT) != "NONE") {
                        throw UnsupportedHlsException("Encrypted/DRM-protected HLS is not supported")
                    }
                }
                line.startsWith("#EXTINF:") -> {
                    if (durationPending) throw UnsupportedHlsException("Missing segment after EXTINF")
                    durationPending = true
                }
                line.startsWith("#") -> Unit
                else -> {
                    if (!durationPending) throw UnsupportedHlsException("Segment has no EXTINF duration")
                    val segment = resolve(url, line)
                    val path = URI(segment).path.lowercase(Locale.ROOT)
                    if (listOf(".m4s", ".mp4", ".aac", ".vtt", ".webm").any(path::endsWith)) {
                        throw UnsupportedHlsException("Only MPEG-TS HLS segments are supported")
                    }
                    segments.add(segment)
                    if (segments.size > MAX_SEGMENTS) throw UnsupportedHlsException("Too many segments")
                    durationPending = false
                }
            }
        }
        if (durationPending || segments.isEmpty()) throw UnsupportedHlsException("Incomplete HLS media playlist")
        return HlsPlaylist.Video(segments)
    }

    /** Parse comma-separated HLS attributes, respecting quoted codec lists. */
    private fun attributes(line: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var i = 0
        while (i < line.length) {
            val keyStart = i
            while (i < line.length && line[i] != '=' && line[i] != ',') i++
            if (i == line.length || line[i] != '=') throw UnsupportedHlsException("Malformed HLS attributes")
            val key = line.substring(keyStart, i++).trim()
            if (key.isEmpty()) throw UnsupportedHlsException("Empty HLS attribute")
            val value: String
            if (i < line.length && line[i] == '"') {
                val start = ++i
                while (i < line.length && line[i] != '"') i++
                if (i == line.length) throw UnsupportedHlsException("Unterminated HLS attribute")
                value = line.substring(start, i++)
                if (i < line.length && line[i] != ',') throw UnsupportedHlsException("Malformed HLS attribute separator")
            } else {
                val start = i
                while (i < line.length && line[i] != ',') i++
                value = line.substring(start, i)
            }
            result[key] = value
            if (i < line.length && line[i] == ',') i++
        }
        return result
    }
}