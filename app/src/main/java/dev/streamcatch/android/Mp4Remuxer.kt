package dev.streamcatch.android

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import dev.streamcatch.android.core.Mp4RemuxPolicy
import java.io.File
import java.io.FileInputStream
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Lossless container conversion: Android extracts H.264/H.265 + optional AAC
 * from downloaded MPEG-TS and muxes their encoded samples directly into MP4.
 *
 * No re-encoding, FFmpeg, or cloud processing. Unsupported device/codec combinations
 * throw, allowing HlsDownloadService to retain its known-good .ts fallback.
 */
object Mp4Remuxer {
    fun remux(transportStream: File, output: FileDescriptor, cancelled: () -> Boolean) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        var stopped = false
        try {
            // Some Android extractors use another process. A seekable FD is safer than
            // passing a path inside the app sandbox.
            FileInputStream(transportStream).use { input ->
                extractor.setDataSource(input.fd)
            }
            val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            val mimes = formats.map { it.getString(MediaFormat.KEY_MIME).orEmpty() }
            if (!Mp4RemuxPolicy.canRemux(mimes)) {
                throw IOException("TS contains codecs/tracks not supported for MP4 remux")
            }

            val target = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = target
            val mappedTracks = mutableMapOf<Int, Int>()
            var maxSampleSize = 2 * 1024 * 1024
            for (index in formats.indices) {
                val mime = mimes[index]
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    mappedTracks[index] = target.addTrack(formats[index])
                    extractor.selectTrack(index)
                    val declaredSize = if (formats[index].containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        formats[index].getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    } else 0
                    maxSampleSize = maxOf(maxSampleSize, declaredSize.coerceAtMost(16 * 1024 * 1024))
                }
            }
            val samples = ByteBuffer.allocateDirect(maxSampleSize)
            val info = MediaCodec.BufferInfo()
            target.start()
            started = true

            var firstTimestamp = -1L
            var videoSamples = 0
            var audioSamples = 0
            while (true) {
                if (cancelled()) throw DownloadCancelled()
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val mapped = mappedTracks[track] ?: throw IOException("Unexpected extractor track")
                samples.clear()
                val count = extractor.readSampleData(samples, 0)
                if (count < 0) break
                if (count > samples.capacity()) throw IOException("MPEG-TS sample exceeds buffer")
                val time = extractor.sampleTime
                if (time < 0) throw IOException("Missing MPEG-TS sample timestamp")
                if (firstTimestamp < 0) firstTimestamp = time
                if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) {
                    throw IOException("Encrypted transport-stream samples are unsupported")
                }
                val flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else 0
                info.set(0, count, Mp4RemuxPolicy.relativePts(time, firstTimestamp), flags)
                samples.position(0)
                samples.limit(count)
                if (count > 0) {
                    target.writeSampleData(mapped, samples, info)
                    if (mimes[track].startsWith("video/")) videoSamples++ else audioSamples++
                }
                if (!extractor.advance()) break
            }
            if (videoSamples == 0 || (mimes.any { it.startsWith("audio/") } && audioSamples == 0)) {
                throw IOException("No complete audio/video frames to mux")
            }
            if (cancelled()) throw DownloadCancelled()
            target.stop()
            stopped = true
        } finally {
            if (started && !stopped) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    /** Validate the produced MP4 before exposing the pending Downloads entry. */
    fun verifyMp4(output: FileDescriptor) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(output)
            val mimes = (0 until extractor.trackCount).map {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty()
            }
            if (!Mp4RemuxPolicy.canRemux(mimes)) throw IOException("MP4 output has unexpected tracks")
            val videoTrack = mimes.indexOfFirst { it.startsWith("video/") }
            extractor.selectTrack(videoTrack)
            val sample = ByteBuffer.allocateDirect(1024 * 1024)
            if (extractor.readSampleData(sample, 0) <= 0) {
                throw IOException("MP4 has no readable video frames")
            }
        } finally {
            extractor.release()
        }
    }
}