package dev.streamcatch.android

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import org.videolan.libvlc.util.VLCVideoLayout
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Embedded LibVLC playback, *only* for locally stored files the user owns or
 * granted via SAF. The browser, HLS downloader and external network stay isolated.
 */
class LibVlcPlayerActivity : Activity() {
    companion object {
        const val EXTRA_TITLE = "local_title"
        private const val PREFS = "streamcatch_resume"
    }

    private var engine: LibVLC? = null
    private var player: MediaPlayer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var videoContainer: FrameLayout
    private lateinit var playPause: Button
    private lateinit var current: TextView
    private lateinit var total: TextView
    private lateinit var seek: SeekBar
    private lateinit var info: TextView
    private var userSeeking = false
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateControls()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            setPadding(12.dp, 8.dp, 12.dp, 12.dp)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(Button(this).apply {
            text = "‹ Library"
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE)?.take(256) ?: "Offline video"
            textSize = 17f
            setTextColor(Color.WHITE)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(12.dp, 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)
        // Keep the weighted sizing on a container: VLCVideoLayout itself uses
        // MATCH_PARENT when attached, and must receive real measured dimensions.
        videoContainer = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        videoLayout = VLCVideoLayout(this)
        videoContainer.addView(videoLayout, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(videoContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        info = TextView(this).apply {
            text = "Opening offline video…"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(6.dp, 4.dp, 6.dp, 4.dp)
        }
        root.addView(info)
        val timeline = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        current = timeLabel()
        timeline.addView(current)
        seek = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) { userSeeking = true }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    val p = player
                    val duration = p?.length ?: -1L
                    if (duration > 0) p?.time = duration * bar.progress / 1000
                    userSeeking = false
                }
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {}
            })
        }
        timeline.addView(seek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        total = timeLabel()
        timeline.addView(total)
        root.addView(timeline)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(Button(this).apply {
            text = "« 10s"
            setOnClickListener { seekRelative(-10_000) }
        })
        playPause = Button(this).apply {
            text = "Pause"
            setOnClickListener {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
                updateControls()
            }
        }
        controls.addView(playPause)
        controls.addView(Button(this).apply {
            text = "10s »"
            setOnClickListener { seekRelative(10_000) }
        })
        root.addView(controls)
        setContentView(root)
        openFile()
    }

    private fun timeLabel() = TextView(this).apply {
        text = "00:00"
        textSize = 13f
        setTextColor(Color.WHITE)
    }

    private fun clock(milliseconds: Long): String {
        val seconds = milliseconds.coerceAtLeast(0) / 1000
        return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60)
        else "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun seekRelative(changeMs: Long) {
        player?.let { p ->
            val length = p.length
            if (length > 0) p.time = (p.time + changeMs).coerceIn(0L, length)
        }
    }

    private fun openFile() {
        val uri: Uri = intent.data ?: run {
            Toast.makeText(this, "No offline file selected", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (uri.scheme != "content") {
            Toast.makeText(this, "Only local content files are supported", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        try {
            // A PFD keeps the file readable during VLC playback and seeking.
            // It is released only *after* VLC stops.
            descriptor = contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Cannot read selected file")
            val vlc = LibVLC(this, arrayListOf("--no-video-title-show"))
            engine = vlc
            val mediaPlayer = MediaPlayer(vlc)
            player = mediaPlayer
            // The high-level VideoHelper enables the video track, handles
            // surface lifetime, window size, aspect ratio and rotations.
            // TextureView avoids the surface-compositor path that produced
            // black frames while audio/time were still progressing on Samsung.
            mediaPlayer.attachViews(videoLayout, null, false, true)
            videoContainer.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left > 0 && bottom - top > 0 &&
                    (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
                ) {
                    player?.updateVideoSurfaces()
                }
            }
            mediaPlayer.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> runOnUiThread { info.text = "Playing locally · LibVLC" }
                    MediaPlayer.Event.EndReached -> runOnUiThread { info.text = "Playback finished" }
                    MediaPlayer.Event.EncounteredError -> runOnUiThread {
                        info.text = "Unable to play this file with LibVLC"
                        Toast.makeText(this, "Local playback failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
            val media = Media(vlc, descriptor!!.fileDescriptor)
            media.setHWDecoderEnabled(true, false)
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
            // Resume last position when appropriate; no tracking outside this device.
            val resume = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(uri.toString(), 0L)
            if (resume > 10_000L) {
                val pending = object : Runnable {
                    var tries = 0
                    override fun run() {
                        val mp = player ?: return
                        if (mp.length > 0) {
                            if (resume < mp.length - 15_000L) mp.time = resume
                        } else if (++tries < 15) {
                            handler.postDelayed(this, 400)
                        }
                    }
                }
                handler.postDelayed(pending, 400)
            }
        } catch (e: Exception) {
            info.text = "Could not open selected offline file"
            Toast.makeText(this, "LibVLC could not open this video", Toast.LENGTH_LONG).show()
            closePlayer()
        }
    }

    private fun updateControls() {
        val mp = player ?: return
        val duration = mp.length
        val elapsed = mp.time
        current.text = clock(elapsed)
        total.text = if (duration > 0L) clock(duration) else "--:--"
        if (!userSeeking && duration > 0) {
            seek.progress = ((elapsed.coerceIn(0L, duration) * 1000L) / duration).toInt()
        }
        playPause.text = if (mp.isPlaying) "Pause" else "Play"
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        val uri = intent.data
        val mp = player
        if (uri != null && mp != null) {
            val time = mp.time
            val length = mp.length
            val position = if (length > 0 && time >= length - 15_000L) 0L else time.coerceAtLeast(0L)
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(uri.toString(), position).apply()
            if (mp.isPlaying) mp.pause()
        }
        super.onPause()
    }

    private fun closePlayer() {
        player?.let { mp ->
            runCatching { mp.stop() }
            runCatching { mp.detachViews() }
            runCatching { mp.release() }
        }
        player = null
        runCatching { engine?.release() }
        engine = null
        runCatching { descriptor?.close() }
        descriptor = null
    }

    override fun onDestroy() {
        closePlayer()
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
