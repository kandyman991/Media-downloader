package dev.streamcatch.android

import android.webkit.CookieManager
import dev.streamcatch.android.core.HlsPlaylistParser
import java.io.BufferedInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Network access for user-selected streams. Cookies are looked up separately for each
 * destination (including redirects), never copied from an unrelated page/host.
 */
object HlsHttp {
    private const val MAX_REDIRECTS = 5
    private const val MAX_PLAYLIST_BYTES = 2_000_000

    fun open(url: String, userAgent: String, referrer: String): HttpURLConnection {
        var current = HlsPlaylistParser.resolve(url, url)
        repeat(MAX_REDIRECTS + 1) { hop ->
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "*/*")
                if (referrer.startsWith("https://")) setRequestProperty("Referer", referrer)
                val cookie = CookieManager.getInstance().getCookie(current)
                if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
            }
            val status = conn.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (hop == MAX_REDIRECTS || location.isNullOrBlank()) throw IOException("Too many HLS redirects")
                current = HlsPlaylistParser.resolve(current, location)
            } else {
                if (status != 200) {
                    conn.disconnect()
                    throw IOException("Media server returned HTTP $status")
                }
                return conn
            }
        }
        throw IOException("Too many redirects")
    }

    fun loadPlaylist(url: String, userAgent: String, referrer: String): String {
        val conn = open(url, userAgent, referrer)
        return try {
            if (conn.contentLengthLong > MAX_PLAYLIST_BYTES) throw IOException("HLS playlist too large")
            conn.inputStream.use { input ->
                val bytes = ByteArray(MAX_PLAYLIST_BYTES + 1)
                var count = 0
                while (count < bytes.size) {
                    val n = input.read(bytes, count, bytes.size - count)
                    if (n == -1) break
                    count += n
                }
                if (count > MAX_PLAYLIST_BYTES) throw IOException("HLS playlist too large")
                String(bytes, 0, count, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Download one segment without holding it in RAM. Reject non-TS data, even for
     * extensionless streams, to avoid creating broken MPEG-TS concatenations.
     */
    fun appendTransportStream(
        url: String, userAgent: String, referrer: String,
        output: java.io.OutputStream, cancelled: () -> Boolean
    ) {
        val conn = open(url, userAgent, referrer)
        try {
            BufferedInputStream(conn.inputStream).use { input ->
                val buffer = ByteArray(64 * 1024)
                val head = input.read()
                if (head != 0x47) throw IOException("Segment is not MPEG-TS (fMP4/audio-only is not yet supported)")
                output.write(head)
                var total = 1L
                while (true) {
                    if (cancelled()) throw DownloadCancelled()
                    val length = input.read(buffer)
                    if (length == -1) break
                    output.write(buffer, 0, length)
                    total += length
                    if (total > 512L * 1024 * 1024) throw IOException("Unexpectedly large MPEG-TS segment")
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}

class DownloadCancelled : IOException("Download cancelled")