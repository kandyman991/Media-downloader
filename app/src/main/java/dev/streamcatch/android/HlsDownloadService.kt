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
import android.os.Build
import android.os.IBinder
import android.os.Environment
import android.provider.MediaStore
import dev.streamcatch.android.core.HlsPlaylist
import dev.streamcatch.android.core.HlsPlaylistParser
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Unencrypted, single-track MPEG-TS HLS VOD -> .ts in Downloads/StreamCatch. */
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
        val agent = intent.getStringExtra(EXTRA_AGENT).orEmpty().ifBlank { "StreamCatch/0.2" }
        val initial = progressNotification("Preparing stream", 0, 0)
        startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        worker.execute {
            try {
                download(url, agent, referrer)
            } catch (cancelled: DownloadCancelled) {
                showResult("HLS download cancelled", false, null)
            } catch (error: Exception) {
                showResult("HLS failed: ${error.message?.take(100) ?: "unknown error"}", false, null)
            } finally {
                active.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun download(url: String, agent: String, referrer: String) {
        val media = when (val playlist = HlsPlaylistParser.parse(
            url, HlsHttp.loadPlaylist(url, agent, referrer)
        )) {
            is HlsPlaylist.Video -> playlist
            is HlsPlaylist.Master -> throw IOException("Select a quality first")
        }
        if (cancel.get()) throw DownloadCancelled()
        val filename = "StreamCatch_${System.currentTimeMillis()}.ts"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp2t")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/StreamCatch")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Cannot create Downloads file")
        var success = false
        try {
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                media.segments.forEachIndexed { index, segment ->
                    if (cancel.get()) throw DownloadCancelled()
                    HlsHttp.appendTransportStream(segment, agent, referrer, output) { cancel.get() }
                    notifications.notify(NOTIFICATION_ID, progressNotification(
                        "Downloading video: ${index + 1}/${media.segments.size} segments",
                        index + 1, media.segments.size
                    ))
                }
                output.flush()
            } ?: throw IOException("Cannot write Downloads file")
            if (cancel.get()) throw DownloadCancelled()
            val finished = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (contentResolver.update(uri, finished, null, null) != 1) {
                throw IOException("Could not finalize video")
            }
            success = true
            showResult("Saved $filename to Downloads/StreamCatch", true, uri)
        } finally {
            if (!success) contentResolver.delete(uri, null, null)
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

    private fun showResult(text: String, succeeded: Boolean, uri: Uri?) {
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(if (succeeded) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (succeeded) "StreamCatch complete" else "StreamCatch")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        if (uri != null) {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp2t")
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