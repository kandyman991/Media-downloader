package dev.streamcatch.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsRequestPolicyTest {
    @Test fun sanitizesSignedReferrerWithoutExposingQueryOrFragment() {
        assertEquals(
            "https://watch.example.org/",
            HlsRequestPolicy.safeReferrerOrigin("https://watch.example.org/player?secret=signed-token#fragment")
        )
        assertEquals(
            "https://watch.example.org:8443/",
            HlsRequestPolicy.safeReferrerOrigin("https://watch.example.org:8443/watch?id=9")
        )
    }

    @Test fun permitsOnlyHttpsAndRejectsEmbeddedCredentials() {
        assertNull(HlsRequestPolicy.safeReferrerOrigin("http://watch.example.org/"))
        assertNull(HlsRequestPolicy.safeReferrerOrigin("https://user:password@watch.example.org/"))
        assertNull(HlsRequestPolicy.httpsHost("http://media.example.org/playlist.m3u8"))
        assertNull(HlsRequestPolicy.httpsHost("https://user:password@media.example.org/playlist.m3u8"))
        assertEquals("media.example.org", HlsRequestPolicy.httpsHost("https://media.example.org/playlist.m3u8?token=abc"))
    }

    @Test fun canonicalizesIpv6Origins() {
        assertEquals("https://[::1]/", HlsRequestPolicy.safeReferrerOrigin("https://[::1]:443/movie"))
    }
}
