package dev.streamcatch.android

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
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
    private var useTextureView = true
    private var resumeAfterSurfaceSwitch: Long? = null
    private lateinit var topControls: LinearLayout
    private lateinit var bottomControls: LinearLayout
    private lateinit var fullscreenButton: Button
    private lateinit var rendererButton: Button
    private lateinit var speedButton: Button
    private var fullscreen = false
    private var controlsVisible = true
    private var speedIndex = 0
    private val speeds = floatArrayOf(1f, 1.25f, 1.5f, 2f, 0.75f)
    private val hideControls = Runnable {
        if (player?.isPlaying == true && !userSeeking) setControlsVisible(false)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateControls()
            handler.postDelayed(this, 500)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.black)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        fullscreen = savedInstanceState?.getBoolean("fullscreen")
            ?: (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)

        // Full-bleed video is the base layer. Every control is an overlay,
        // not an extra row reducing the video playback viewport.
        videoContainer = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        videoLayout = VLCVideoLayout(this)
        videoContainer.addView(videoLayout, FrameLayout.LayoutParams(-1, -1))
        setContentView(videoContainer)

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                setControlsVisible(!controlsVisible)
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Common VLC/YouTube interaction: double-tap edges to jump 10 seconds.
                if (e.x < videoContainer.width * 0.4f) seekRelative(-10_000)
                else if (e.x > videoContainer.width * 0.6f) seekRelative(10_000)
                else player?.let { if (it.isPlaying) it.pause() else it.play() }
                setControlsVisible(true)
                return true
            }
        })
        val touchLayer = View(this).apply {
            contentDescription = "Tap to show playback controls; double-tap sides to seek"
            isClickable = true
            setOnTouchListener { _, event -> detector.onTouchEvent(event); true }
        }
        videoContainer.addView(touchLayer, FrameLayout.LayoutParams(-1, -1))

        topControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp, 10.dp, 16.dp, 24.dp)
            background = overlayBackground(true)
        }
        topControls.addView(playerButton("‹", "Back to library") { finish() })
        topControls.addView(TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE)?.take(256) ?: "Offline video"
            textSize = 17f
            setTextColor(Color.WHITE)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(14.dp, 0, 6.dp, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        fullscreenButton = playerButton("⛶", "Toggle landscape fullscreen") { toggleFullscreen() }
        topControls.addView(fullscreenButton)
        videoContainer.addView(topControls, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        bottomControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 36.dp, 16.dp, 16.dp)
            background = overlayBackground(false)
        }
        info = TextView(this).apply {
            text = "Opening offline video…"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 6.dp)
        }
        bottomControls.addView(info)
        val timeline = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        current = timeLabel()
        timeline.addView(current)
        seek = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(49, 208, 174))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.rgb(49, 208, 174))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.GRAY)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) {
                    userSeeking = true
                    handler.removeCallbacks(hideControls)
                }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    val p = player
                    val duration = p?.length ?: -1L
                    if (duration > 0) p?.time = duration * bar.progress / 1000L
                    userSeeking = false
                    scheduleHideControls()
                }
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val length = player?.length ?: 0L
                        if (length > 0) current.text = clock(length * progress / 1000L)
                    }
                }
            })
        }
        timeline.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        total = timeLabel()
        timeline.addView(total)
        bottomControls.addView(timeline)

        val playback = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        playback.addView(playerButton("−10", "Rewind ten seconds") {
            seekRelative(-10_000)
            scheduleHideControls()
        })
        playPause = playerButton("⏸", "Pause or resume playback") {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            updateControls()
            scheduleHideControls()
        }
        playback.addView(playPause)
        playback.addView(playerButton("+10", "Forward ten seconds") {
            seekRelative(10_000)
            scheduleHideControls()
        })
        playback.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        speedButton = playerButton("1×", "Playback speed") {
            speedIndex = (speedIndex + 1) % speeds.size
            player?.rate = speeds[speedIndex]
            speedButton.text = "${speeds[speedIndex]}×"
            scheduleHideControls()
        }
        playback.addView(speedButton)
        rendererButton = playerButton("⋮", "Switch video renderer if screen is black") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Video output")
                .setItems(arrayOf("Texture (recommended)", "Surface (compatibility)")) { _, which ->
                    if ((which == 0) != useTextureView) switchVideoOutput()
                    setControlsVisible(true)
                }
                .show()
        }
        playback.addView(rendererButton)
        bottomControls.addView(playback)
        videoContainer.addView(bottomControls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        applySystemBars()
        updateFullscreenButton()
        openFile()
        scheduleHideControls()
    }

    private fun playerButton(label: String, description: String, click: () -> Unit) =
        Button(this).apply {
            text = label
            contentDescription = description
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            minWidth = 46.dp
            minimumHeight = 44.dp
            setPadding(8.dp, 0, 8.dp, 0)
            setOnClickListener { click() }
        }

    private fun overlayBackground(top: Boolean) = GradientDrawable(
        if (top) GradientDrawable.Orientation.TOP_BOTTOM
        else GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(Color.argb(205, 0, 0, 0), Color.TRANSPARENT)
    )

    private fun timeLabel() = TextView(this).apply {
        text = "00:00"
        textSize = 13f
        setTextColor(Color.WHITE)
    }

    private fun setControlsVisible(show: Boolean) {
        controlsVisible = show
        topControls.visibility = if (show) View.VISIBLE else View.GONE
        bottomControls.visibility = if (show) View.VISIBLE else View.GONE
        handler.removeCallbacks(hideControls)
        if (show) scheduleHideControls()
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControls)
        if (controlsVisible && !userSeeking && player?.isPlaying == true) {
            handler.postDelayed(hideControls, 4500)
        }
    }

    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        updateFullscreenButton()
        applySystemBars()
        setControlsVisible(true)
    }

    private fun updateFullscreenButton() {
        fullscreenButton.text = if (fullscreen) "⤢" else "⛶"
        fullscreenButton.contentDescription =
            if (fullscreen) "Exit landscape fullscreen" else "Enter landscape fullscreen"
    }

    private fun applySystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) {
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                } else {
                    controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (fullscreen)
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            else View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // VideoHelper's TextureView remains attached through rotations.
        // No decoder/file restart just to enter or leave landscape.
        fullscreen = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateFullscreenButton()
        applySystemBars()
        setControlsVisible(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("fullscreen", fullscreen)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Handled to exit fullscreen without destroying local video playback.")
    override fun onBackPressed() {
        if (fullscreen) toggleFullscreen() else super.onBackPressed()
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

    /**
     * Samsung device fallback: allow switching LibVLC's display backend in-place,
     * preserving the seek position and source descriptor. No re-download needed.
     * The default remains the VideoHelper-managed TextureView.
     */
    private fun switchVideoOutput() {
        val mp = player ?: return
        val at = mp.time.coerceAtLeast(0L)
        val nextTextureMode = !useTextureView
        try {
            mp.stop()
            mp.detachViews()
            mp.attachViews(videoLayout, null, false, nextTextureMode)
            useTextureView = nextTextureMode
            resumeAfterSurfaceSwitch = at
            info.text = "Switching to ${if (useTextureView) "Texture" else "Surface"} rendering…"
            mp.play()
        } catch (error: Exception) {
            info.text = "Could not switch video renderer"
            android.util.Log.w("StreamCatch-VLC", "Renderer switch failed", error)
            Toast.makeText(this, "Could not switch video output", Toast.LENGTH_LONG).show()
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
            // VideoHelper enables the video track and automatically handles
            // surface lifetime, size, aspect ratio and screen rotation.
            // Use TextureView first because the raw SurfaceView path rendered
            // black video (even though audio and position were correct).
            mediaPlayer.attachViews(videoLayout, null, false, useTextureView)
            mediaPlayer.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> runOnUiThread {
                        if (player !== mediaPlayer) return@runOnUiThread
                        resumeAfterSurfaceSwitch?.let { position ->
                            resumeAfterSurfaceSwitch = null
                            if (position > 0L) mediaPlayer.time = position
                        }
                        info.text = "Playing locally · LibVLC (${if (useTextureView) "Texture" else "Surface"})"
                    }
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
        playPause.text = if (mp.isPlaying) "⏸" else "▶"
    }

    override fun onResume() {
        super.onResume()
        applySystemBars()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        scheduleHideControls()
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
