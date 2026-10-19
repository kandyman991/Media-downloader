package dev.streamcatch.android.core

/**
 * Conservative MP4-remux support. Reject unexpected tracks rather than silently dropping
 * speech or video and publishing a partially playable MP4.
 */
object Mp4RemuxPolicy {
    const val AVC = "video/avc"
    const val HEVC = "video/hevc"
    const val AAC = "audio/mp4a-latm"

    fun canRemux(mimes: List<String>): Boolean {
        val videos = mimes.filter { it.startsWith("video/") }
        val audios = mimes.filter { it.startsWith("audio/") }
        val other = mimes.filterNot { it.startsWith("video/") || it.startsWith("audio/") }
        return videos.size == 1 &&
            videos.single() in setOf(AVC, HEVC) &&
            audios.size <= 1 &&
            audios.all { it == AAC } &&
            other.all { it.startsWith("application/") }
    }

    /**
     * Missing audio is acceptable only if the selected source itself has no
     * detected audio; in that case the app must clearly label video-only output.
     * A source that actually contained audio must never silently lose it.
     */
    fun preservesAudio(sourceHasAudio: Boolean, outputHasAudio: Boolean): Boolean =
        !sourceHasAudio || outputHasAudio

    /** TS may carry non-zero clock offsets. MP4 samples should start near time zero. */
    fun relativePts(sampleTimeUs: Long, firstTimeUs: Long): Long {
        require(firstTimeUs >= 0 && sampleTimeUs >= 0) { "Invalid transport-stream timestamps" }
        return (sampleTimeUs - firstTimeUs).coerceAtLeast(0L)
    }
}