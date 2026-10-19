package dev.streamcatch.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMediaPolicyTest {
    @Test fun recognizesSupportedOfflineVideoAndAudioRegardlessOfCase() {
        assertTrue(OfflineMediaPolicy.supports("StreamCatch_movie.MP4"))
        assertTrue(OfflineMediaPolicy.supports("lesson.ts"))
        assertTrue(OfflineMediaPolicy.supports("concert.MkV"))
        assertTrue(OfflineMediaPolicy.supports("audio.FLAC"))
        assertEquals("video", OfflineMediaPolicy.category("film.mp4"))
        assertEquals("audio", OfflineMediaPolicy.category("song.m4a"))
    }

    @Test fun refusesWebLinksAndUnrecognizedFileTypes() {
        assertFalse(OfflineMediaPolicy.supports("subtitles.srt"))
        assertFalse(OfflineMediaPolicy.supports("url.m3u8"))
        assertFalse(OfflineMediaPolicy.supports("movie.exe"))
        assertFalse(OfflineMediaPolicy.supports(""))
        assertEquals("unsupported", OfflineMediaPolicy.category("unknown.dat"))
    }
}
