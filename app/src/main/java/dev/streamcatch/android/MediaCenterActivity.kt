package dev.streamcatch.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** StreamCatch media library: only app downloads and explicitly imported user files. */
class MediaCenterActivity : Activity() {
    companion object { private const val PICK_MEDIA = 9041 }
    private lateinit var list: LinearLayout
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 15, 22)
        window.navigationBarColor = Color.rgb(12, 15, 22)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 20.dp, 16.dp, 12.dp)
            setBackgroundColor(Color.rgb(12, 15, 22))
            fitsSystemWindows = true
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = Button(this).apply {
            text = "‹ Browser"
            setOnClickListener { finish() }
        }
        heading.addView(back)
        heading.addView(TextView(this).apply {
            text = "Offline Library"
            textSize = 23f
            setTextColor(Color.WHITE)
            setPadding(12.dp, 0, 0, 0)
        })
        root.addView(heading)
        root.addView(TextView(this).apply {
            text = "Your downloads, ready for travel. Playback uses LibVLC."
            textSize = 14f
            setTextColor(Color.rgb(190, 200, 215))
            setPadding(4.dp, 8.dp, 4.dp, 12.dp)
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val importButton = Button(this).apply {
            text = "Add local file"
            setOnClickListener { chooseLocalFile() }
        }
        actions.addView(importButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val refresh = Button(this).apply {
            text = "Refresh"
            setOnClickListener { reloadLibrary() }
        }
        actions.addView(refresh, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions)
        root.addView(TextView(this).apply {
            text = "LibVLC engine by VideoLAN · LGPL 2.1 · About"
            textSize = 12f
            setTextColor(Color.rgb(169, 203, 245))
            setPadding(4.dp, 4.dp, 4.dp, 4.dp)
            setOnClickListener {
                android.app.AlertDialog.Builder(this@MediaCenterActivity)
                    .setTitle("About the player")
                    .setMessage(
                        "StreamCatch uses the official VideoLAN LibVLC Android engine (3.6.1) " +
                        "to play your own offline files.\n\n" +
                        "LibVLC is licensed under LGPL-2.1. VLC project and source: " +
                        "https://www.videolan.org/ and https://code.videolan.org/videolan/vlc-android.\n\n" +
                        "No separate VLC app is required. Streaming-service DRM and expiration " +
                        "are not modified or bypassed."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        })
        summary = TextView(this).apply { textSize = 13f; setTextColor(Color.LTGRAY); setPadding(4.dp, 12.dp, 4.dp, 10.dp) }
        root.addView(summary)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        reloadLibrary()
    }

    private fun chooseLocalFile() {
        val select = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "video/*", "audio/*", "application/x-matroska", "video/mp2t", "application/ogg"
            ))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(select, PICK_MEDIA)
    }

    @Deprecated("Start activity for result is used without adding a framework dependency.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_MEDIA || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (uri.scheme != "content") return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            OfflineMediaLibrary.rememberDocument(this, uri)
            reloadLibrary()
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't keep access to this file", Toast.LENGTH_LONG).show()
        }
    }

    private fun reloadLibrary() {
        val all = OfflineMediaLibrary.list(this)
        summary.text = "${all.size} offline item${if (all.size == 1) "" else "s"}  ·  Downloads/StreamCatch + selected files"
        list.removeAllViews()
        if (all.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No saved videos yet. Download a video in the browser, or tap Add local file."
                textSize = 16f
                setTextColor(Color.LTGRAY)
                setPadding(8.dp, 30.dp, 8.dp, 30.dp)
            })
        }
        for (item in all) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp, 16.dp, 14.dp, 16.dp)
                setBackgroundColor(Color.rgb(29, 35, 47))
                setOnClickListener { openPlayer(item) }
            }
            row.addView(TextView(this).apply {
                text = item.title
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                textSize = 17f
                setTextColor(Color.WHITE)
            })
            val info = if (item.size > 0) "${item.size / (1024 * 1024)} MB" else "Unknown size"
            row.addView(TextView(this).apply {
                text = "${if (item.imported) "Local file" else "Downloaded"} · $info · Tap to play"
                textSize = 12f
                setTextColor(Color.LTGRAY)
            })
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp
            })
        }
    }

    private fun openPlayer(item: OfflineMedia) {
        startActivity(Intent(this, LibVlcPlayerActivity::class.java).apply {
            data = item.uri
            putExtra(LibVlcPlayerActivity.EXTRA_TITLE, item.title.take(256))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
