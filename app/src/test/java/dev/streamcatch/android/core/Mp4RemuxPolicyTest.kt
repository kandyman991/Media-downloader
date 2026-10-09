package dev.streamcatch.android.core

import org.junit.Assert.*
import org.junit.Test

class Mp4RemuxPolicyTest {
    @Test fun supportsAvcWithOptionalAacAudio() {
        assertTrue(Mp4RemuxPolicy.canRemux(listOf("video/avc")))
        assertTrue(Mp4RemuxPolicy.canRemux(listOf("video/avc", "audio/mp4a-latm")))
        assertTrue(Mp4RemuxPolicy.canRemux(listOf("audio/mp4a-latm", "video/hevc")))
        assertTrue(Mp4RemuxPolicy.canRemux(listOf("video/avc", "audio/mp4a-latm", "application/id3")))
    }

    @Test fun doesNotSilentlyDiscardUnsupportedAudioOrVideo() {
        assertFalse(Mp4RemuxPolicy.canRemux(listOf("video/avc", "audio/mpeg")))
        assertFalse(Mp4RemuxPolicy.canRemux(listOf("video/mp2v", "audio/mp4a-latm")))
        assertFalse(Mp4RemuxPolicy.canRemux(listOf("audio/mp4a-latm")))
        assertFalse(Mp4RemuxPolicy.canRemux(listOf("video/avc", "video/hevc")))
        assertFalse(Mp4RemuxPolicy.canRemux(listOf("video/avc", "audio/mp4a-latm", "audio/mp4a-latm")))
        assertFalse(Mp4RemuxPolicy.canRemux(listOf("video/avc", "text/vtt")))
        assertFalse(Mp4RemuxPolicy.canRemux(emptyList()))
    }

    @Test fun rebaseTimestampsWithoutNegativeValues() {
        assertEquals(0L, Mp4RemuxPolicy.relativePts(9_000_000L, 9_000_000L))
        assertEquals(100_000L, Mp4RemuxPolicy.relativePts(9_100_000L, 9_000_000L))
        assertEquals(0L, Mp4RemuxPolicy.relativePts(8_999_999L, 9_000_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingTimestampsFail() {
        Mp4RemuxPolicy.relativePts(-1L, 9_000_000L)
    }
}