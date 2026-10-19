package dev.streamcatch.android

import android.content.Context
import android.os.Looper
import dev.streamcatch.android.core.HlsPlaylistParser
import dev.streamcatch.android.core.HlsRequestPolicy
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse

/**
 * HTTPS-only HLS transport through the same Gecko profile used for browsing.
 * Do not use android.webkit.CookieManager or forward authentication headers manually.
 * Gecko selects which cookies (if any) to send to each destination.
 */
object HlsHttp {
    private const val MAX_REDIRECTS = 5
    private const val MAX_PLAYLIST_BYTES = 2_000_000
    private const val FETCH_TIMEOUT_MS = 30_000L

    private fun response(
        context: Context, url: String, userAgent: String, referrer: String, stage: String
    ): WebResponse {
        // GeckoResult.poll must NEVER block a thread with a Looper (especially UI).
        check(Looper.myLooper() == null) { "HLS network access must run on a worker thread" }
        val executor = GeckoWebExecutor(StreamCatchGeckoRuntime.get(context))
        var current = url
        repeat(MAX_REDIRECTS + 1) { hop ->
            val host = HlsRequestPolicy.httpsHost(current)
                ?: throw IOException("Only HTTPS HLS URLs are permitted")
            val request = WebRequest.Builder(current).apply {
                header("User-Agent", userAgent)
                header("Accept", "*/*")
                HlsRequestPolicy.safeReferrerOrigin(referrer)?.let { referrer(it) }
            }.build()
            val result = try {
                executor.fetch(request, GeckoWebExecutor.FETCH_FLAGS_NO_REDIRECTS).poll(FETCH_TIMEOUT_MS)
                    ?: throw IOException("No HLS response from $host")
            } catch (error: Exception) {
                throw IOException("HLS $stage request failed for $host: ${error.javaClass.simpleName}", error)
            }
            if (result.statusCode in listOf(301, 302, 303, 307, 308)) {
                val location = result.headers.entries.firstOrNull {
                    it.key.equals("Location", ignoreCase = true)
                }?.value
                result.body?.close()
                if (hop == MAX_REDIRECTS || location.isNullOrBlank()) {
                    throw IOException("Too many HLS redirects ($stage)")
                }
                val next = HlsPlaylistParser.resolve(current, location)
                if (HlsRequestPolicy.httpsHost(next) == null) {
                    throw IOException("HLS redirect was not HTTPS")
                }
                current = next
            } else {
                if (result.statusCode != 200) {
                    result.body?.close()
                    // Never place a complete URL in notifications/logs: tokens may be embedded in it.
                    throw IOException("HLS $stage HTTP ${result.statusCode} from $host")
                }
                return result
            }
        }
        throw IOException("Too many HLS redirects")
    }

    fun loadPlaylist(context: Context, url: String, userAgent: String, referrer: String): String {
        val response = response(context, url, userAgent, referrer, "playlist")
        return (response.body ?: throw IOException("Empty HLS playlist response")).use { input ->
            val max = MAX_PLAYLIST_BYTES + 1
            val bytes = ByteArray(max)
            var count = 0
            while (count < max) {
                val n = input.read(bytes, count, max - count)
                if (n == -1) break
                if (n == 0) continue
                count += n
            }
            if (count > MAX_PLAYLIST_BYTES) throw IOException("HLS playlist too large")
            String(bytes, 0, count, Charsets.UTF_8)
        }
    }

    /**
     * Stream one user-selected unencrypted TS segment without buffering it in memory.
     * The existing MP4/TS output verification and cancellation logic remains unchanged.
     */
    fun appendTransportStream(
        context: Context, url: String, userAgent: String, referrer: String,
        output: java.io.OutputStream, cancelled: () -> Boolean
    ) {
        val response = response(context, url, userAgent, referrer, "segment")
        val body: InputStream = response.body ?: throw IOException("Empty HLS segment response")
        BufferedInputStream(body).use { input ->
            if (cancelled()) throw DownloadCancelled()
            val head = input.read()
            if (head != 0x47) throw IOException("Segment is not MPEG-TS (fMP4/audio-only unsupported)")
            output.write(head)
            val buffer = ByteArray(64 * 1024)
            var total = 1L
            while (true) {
                if (cancelled()) throw DownloadCancelled()
                val length = input.read(buffer)
                if (length == -1) break
                if (length == 0) continue
                output.write(buffer, 0, length)
                total += length
                if (total > 512L * 1024 * 1024) {
                    throw IOException("Unexpectedly large MPEG-TS segment")
                }
            }
        }
    }
}

class DownloadCancelled : IOException("Download cancelled")
