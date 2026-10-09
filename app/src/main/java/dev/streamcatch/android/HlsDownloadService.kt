package dev.streamcatch.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.Environment
import android.provider.MediaStore
import dev.streamcatch.android.core.HlsPlaylist
import dev.streamcatch.android.core.HlsPlaylistParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * M3.1: Download unencrypted MPEG-TS HLS VOD to temporary app storage,
 * remux into MP4 without re-encoding, and fall back to playable .ts on failure.
 */
class HlsDownloadService : Service() {
    companion object {
        const val ACTION_DOWNLOAD = "dev.streamcatch.android.DOWNLOAD_HLS"
        const val ACTION_CANCEL = "dev.streamcatch.android.CANCEL_HLS"
        const val EXTRA_URL = "url"
        const val EXTRA_REFERRER = "referrer"
        const val EXTRA_AGENT = "agent"
        private const val CHANNEL = "streamcatch_hls"
        private const val NOTIFICATION_ID = 3107
        private const val RESULT_ID = 3108
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val active = AtomicBoolean(false)
    private val cancel = AtomicBoolean(false)
    private lateinit var notifications: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "StreamCatch downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancel.set(true)
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_DOWNLOAD) return START_NOT_STICKY
        if (!active.compareAndSet(false, true)) return START_NOT_STICKY
        cancel.set(false)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val referrer = intent.getStringExtra(EXTRA_REFERRER).orEmpty()
        val agent = intent.getStringExtra(EXTRA_AGENT).orEmpty().ifBlank { "StreamCatch/0.3" }
        startForeground(NOTIFICATION_ID, progressNotification("Preparing stream", 0, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        worker.execute {
            try {
                download(url, agent, referrer)
            } catch (cancelled: DownloadCancelled) {
                showResult("HLS download cancelled", false, null, null)
            } catch (error: Exception) {
                showResult("HLS failed: ${error.message?.take(100) ?: "unknown error"}", false, null, null)
            } finally {
                active.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private data class SavedVideo(val uri: Uri, val name: String, val mime: String, val isMp4: Boolean)

    private fun download(url: String, agent: String, referrer: String) {
        val media = when (val playlist = HlsPlaylistParser.parse(
            url, HlsHttp.loadPlaylist(url, agent, referrer)
        )) {
            is HlsPlaylist.Video -> playlist
            is HlsPlaylist.Master -> throw IOException("Select a quality first")
        }
        if (cancel.get()) throw DownloadCancelled()
        // App-private temporary file is seekable, so MediaExtractor can read it.
        // All paths (including cancellation and failed remuxes) delete it.
        val temp = File.createTempFile("streamcatch_", ".ts", cacheDir)
        try {
            FileOutputStream(temp).use { output ->
                media.segments.forEachIndexed { index, segment ->
                    if (cancel.get()) throw DownloadCancelled()
                    HlsHttp.appendTransportStream(segment, agent, referrer, output) { cancel.get() }
                    notifications.notify(NOTIFICATION_ID, progressNotification(
                        "Downloading: ${index + 1}/${media.segments.size} segments",
                        index + 1, media.segments.size
                    ))
                }
                output.flush()
            }
            if (cancel.get()) throw DownloadCancelled()
            notifications.notify(NOTIFICATION_ID, progressNotification("Saving MP4 without re-encoding", 0, 0))
            val base = "StreamCatch_${System.currentTimeMillis()}"
            val converted: SavedVideo? = try {
                saveMp4(temp, base)
            } catch (cancelled: DownloadCancelled) {
                throw cancelled
            } catch (error: Exception) {
                if (cancel.get()) throw DownloadCancelled()
                android.util.Log.i("StreamCatch", "MP4 remux unavailable, falling back to transport stream: ${error.javaClass.simpleName}")
                null
            }
            val saved = if (converted != null) converted else {
                notifications.notify(NOTIFICATION_ID, progressNotification("Saving compatible TS fallback", 0, 0))
                saveTransportStream(temp, base)
            }
            val label = if (saved.isMp4) "MP4" else "TS (MP4 not supported for this stream/device)"
            showResult("Saved $label to Downloads/StreamCatch", true, saved.uri, saved.mime)
        } finally {
            temp.delete()
        }
    }

    private fun pendingVideo(name: String, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/StreamCatch")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Cannot create Downloads file")
    }

    private fun finalizeVideo(uri: Uri) {
        val finished = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        if (contentResolver.update(uri, finished, null, null) != 1) {
            throw IOException("Could not finalize downloaded video")
        }
    }

    private fun saveMp4(temp: File, base: String): SavedVideo {
        val name = "$base.mp4"
        val uri = pendingVideo(name, "video/mp4")
        var completed = false
        try {
            contentResolver.openFileDescriptor(uri, "rw")?.use { fd ->
                Mp4Remuxer.remux(temp, fd.fileDescriptor) { cancel.get() }
            } ?: throw IOException("Unable to open MP4 destination")
            if (cancel.get()) throw DownloadCancelled()
            // Verify after MediaMuxer has closed and flushed its output.
            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                Mp4Remuxer.verifyMp4(fd.fileDescriptor)
            } ?: throw IOException("Cannot verify MP4")
            if (cancel.get()) throw DownloadCancelled()
            finalizeVideo(uri)
            completed = true
            return SavedVideo(uri, name, "video/mp4", true)
        } finally {
            if (!completed) contentResolver.delete(uri, null, null)
        }
    }

    private fun saveTransportStream(temp: File, base: String): SavedVideo {
        val name = "$base.ts"
        val uri = pendingVideo(name, "video/mp2t")
        var completed = false
        try {
            contentResolver.openOutputStream(uri, "w")?.use { target ->
                FileInputStream(temp).use { input ->
                    val bytes = ByteArray(64 * 1024)
                    while (true) {
                        if (cancel.get()) throw DownloadCancelled()
                        val n = input.read(bytes)
                        if (n < 0) break
                        target.write(bytes, 0, n)
                    }
                    target.flush()
                }
            } ?: throw IOException("Cannot save TS fallback")
            if (cancel.get()) throw DownloadCancelled()
            finalizeVideo(uri)
            completed = true
            return SavedVideo(uri, name, "video/mp2t", false)
        } finally {
            if (!completed) contentResolver.delete(uri, null, null)
        }
    }

    private fun progressNotification(text: String, done: Int, total: Int): Notification {
        val cancelIntent = Intent(this, HlsDownloadService::class.java).setAction(ACTION_CANCEL)
        val cancelPending = PendingIntent.getService(
            this, 12, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("StreamCatch HLS")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total, done, total == 0)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPending
            ).build())
            .build()
    }

    private fun showResult(text: String, succeeded: Boolean, uri: Uri?, mime: String?) {
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(if (succeeded) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (succeeded) "StreamCatch complete" else "StreamCatch")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        if (uri != null && mime != null) {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val pending = PendingIntent.getActivity(
                this, 11, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pending)
        }
        notifications.notify(RESULT_ID, builder.build())
    }

    override fun onDestroy() {
        cancel.set(true)
        worker.shutdownNow()
        super.onDestroy()
    }
}