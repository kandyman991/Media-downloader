package dev.streamcatch.android.core

import java.net.URI

/**
 * Request metadata that may be passed to the Gecko network transport.
 * Never attach a URL query, fragment, userinfo or signed playlist token as a Referer.
 * Gecko itself selects each destination's cookie scope.
 */
object HlsRequestPolicy {
    fun httpsHost(url: String): String? {
        val uri = MediaUrlDetector.parseHttps(url) ?: return null
        return uri.host
    }

    fun safeReferrerOrigin(url: String): String? {
        val uri = MediaUrlDetector.parseHttps(url) ?: return null
        if (uri.rawUserInfo != null) return null
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return "https://${uri.host}${port}/"
    }
}
