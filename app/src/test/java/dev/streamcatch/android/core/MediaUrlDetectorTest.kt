package dev.streamcatch.android.core

import org.junit.Assert.*
import org.junit.Test

class MediaUrlDetectorTest {
    @Test fun identifiesVideoAndPlaylistsWithQueries() {
        assertEquals(MediaKind.MP4, MediaUrlDetector.classify("https://media.example/video.MP4?token=123"))
        assertEquals(MediaKind.HLS, MediaUrlDetector.classify("https://media.example/master.m3u8?k=3"))
        assertEquals(MediaKind.DASH, MediaUrlDetector.classify("https://media.example/file", "application/dash+xml"))
        assertEquals(MediaKind.WEBM, MediaUrlDetector.classify("https://example.com/a.webm"))
    }

    @Test fun blocksUnsafeSchemesAndNonVideos() {
        assertNull(MediaUrlDetector.classify("http://example.org/video.mp4"))
        assertNull(MediaUrlDetector.classify("file:///sdcard/video.mp4"))
        assertNull(MediaUrlDetector.classify("javascript:alert(1)"))
        assertNull(MediaUrlDetector.classify("https://example.org/photo.jpg"))
    }

    @Test fun producesConstrainedFilename() {
        assertEquals("a_b_42.mp4", MediaUrlDetector.safeFilename("https://example.org/a%20b.mp4", MediaKind.MP4, 42L))
    }
}
