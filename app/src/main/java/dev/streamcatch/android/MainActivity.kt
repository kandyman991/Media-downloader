package dev.streamcatch.android

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.WindowInsets
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import dev.streamcatch.android.core.MediaKind
import dev.streamcatch.android.core.MediaUrlDetector
import dev.streamcatch.android.core.HlsPlaylist
import dev.streamcatch.android.core.HlsPlaylistParser
import java.util.LinkedHashMap
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

/** Android-first MVP: HTTPS page inspection + OS-managed direct downloads. */
class MainActivity : Activity() {
    private data class MediaCandidate(
        val url: String, val kind: MediaKind, val discoveredBy: String,
        val referrerOrigin: String? = null, val referrerEvidence: Int = 0
    )

    private lateinit var geckoView: GeckoView
    private lateinit var browserSession: GeckoSession
    private lateinit var runtime: GeckoRuntime
    private lateinit var mediaBridge: GeckoMediaBridge
    private lateinit var blocker: UblockOriginManager
    private var currentUrl = ""
    private var canGoBack = false
    private lateinit var address: EditText
    private lateinit var mediaButton: Button
    private lateinit var blockerButton: Button
    private lateinit var progress: ProgressBar
    private val media = LinkedHashMap<String, MediaCandidate>()
    private var closed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = StreamCatchGeckoRuntime.get(applicationContext)
        browserSession = GeckoSession()
        browserSession.open(runtime)
        mediaBridge = GeckoMediaBridge { url, source, observedOrigin -> registerMedia(url, null, source, observedOrigin) }
        makeUi()
        mediaBridge.install(runtime)
        blocker = UblockOriginManager(this, runtime, browserSession) { active ->
            runOnUiThread { if (!closed) blockerButton.text = if (active) "uBO ✓" else "uBO" }
        }
        blocker.initialize()
        if (savedInstanceState != null) {
            navigate(savedInstanceState.getString("lastUrl").orEmpty().ifBlank { "https://example.org" })
        } else {
            if (!openSharedPage(intent)) navigate("https://example.org")
        }
    }

    private fun makeUi() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            fitsSystemWindows = true
            if (Build.VERSION.SDK_INT >= 30) {
                setOnApplyWindowInsetsListener { root, insets ->
                    val edges = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    root.setPadding(edges.left, edges.top, edges.right, edges.bottom)
                    insets
                }
            }
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun btn(text: String, action: () -> Unit): Button = Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }
        toolbar.addView(btn("‹") { if (canGoBack) browserSession.goBack() }, LinearLayout.LayoutParams(54.dp, ViewGroup.LayoutParams.WRAP_CONTENT))
        address = EditText(this).apply {
            hint = "Enter a website URL"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true }
        }
        toolbar.addView(address, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(btn("Go") { navigate(address.text.toString()) })
        blockerButton = btn("uBO") { blocker.showControls() }
        toolbar.addView(blockerButton)
        layout.addView(toolbar)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        layout.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 3.dp))

        geckoView = GeckoView(this)
        browserSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@MainActivity.canGoBack = canGoBack
            }
            override fun onLocationChange(
                session: GeckoSession, url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                currentUrl = url.orEmpty()
                address.setText(currentUrl)
            }
            override fun onLoadRequest(
                session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val url = request.uri
                if (!url.startsWith("https://") && !url.startsWith("about:") &&
                    !url.startsWith("moz-extension:")) {
                    Toast.makeText(this@MainActivity, "Only HTTPS sites are supported", Toast.LENGTH_SHORT).show()
                    return GeckoResult.deny()
                }
                return null
            }
        }
        browserSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                clearMedia()
                currentUrl = url
                address.setText(url)
                this@MainActivity.progress.progress = 0
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (success) scanPage()
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                this@MainActivity.progress.progress = progress
            }
        }
        browserSession.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                try {
                    val mime = response.headers.entries.firstOrNull {
                        it.key.equals("Content-Type", ignoreCase = true)
                    }?.value
                    val kind = MediaUrlDetector.classify(response.uri, mime)
                    if (kind != null) {
                        registerMedia(response.uri, mime, "Browser download")
                        if (kind.isDirect) downloadDirect(response.uri, kind, GeckoSession.getDefaultUserAgent())
                        else if (kind == MediaKind.HLS) prepareHls(response.uri)
                    }
                } finally {
                    response.body?.close()
                }
            }
        }
        geckoView.setSession(browserSession)
        runtime.webExtensionController.setTabActive(browserSession, true)
        layout.addView(geckoView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(btn("Scan page") { scanPage() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        mediaButton = btn("Videos (0)") { showMedia() }
        footer.addView(mediaButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(footer)
        setContentView(layout)
    }

    private fun navigate(input: String): Boolean {
        val url = input.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        if (MediaUrlDetector.parseHttps(url) == null) {
            Toast.makeText(this, "Enter a valid HTTPS URL", Toast.LENGTH_SHORT).show()
            return false
        }
        browserSession.loadUri(url)
        return true
    }

    private fun openSharedPage(received: Intent?): Boolean {
        if (received?.action != Intent.ACTION_SEND || received.type != "text/plain") return false
        val shared = received.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = Regex("https://[^\\s<>\\\"']+").find(shared)?.value?.trimEnd('.', ',', ')', ']', ';')
        return url?.let(::navigate) ?: false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSharedPage(intent)
    }

    private fun clearMedia() {
        media.clear()
        mediaButton.text = "Videos (0)"
    }

    private fun registerMedia(url: String, mimeType: String?, foundBy: String, observedOrigin: String? = null) {
        val kind = MediaUrlDetector.classify(url, mimeType) ?: return
        // The origin must come from an actual observed page/frame or Gecko request.
        // Never trust an arbitrary URL as a Referer and never pass signed URL queries.
        val origin = observedOrigin?.let { dev.streamcatch.android.core.HlsRequestPolicy.safeReferrerOrigin(it) }
        val evidence = when {
            origin == null -> 0
            foundBy == "Browser headers" -> 2
            else -> 1
        }
        runOnUiThread {
            if (closed) return@runOnUiThread
            val previous = media[url]
            if (previous != null) {
                // onBeforeRequest arrives before onBeforeSendHeaders: upgrade the
                // candidate when Gecko's actual Referer header is observed.
                if (evidence > previous.referrerEvidence) {
                    media[url] = previous.copy(referrerOrigin = origin, referrerEvidence = evidence)
                }
                return@runOnUiThread
            }
            if (media.size >= 100) return@runOnUiThread
            media[url] = MediaCandidate(url, kind, foundBy, origin, evidence)
            mediaButton.text = "Videos (${media.size})"
        }
    }

    private fun scanPage() {
        // GeckoView isolates DOM / network observation in a privileged extension.
        mediaBridge.scan()
    }

    private fun showMedia() {
        if (media.isEmpty()) {
            scanPage()
            Toast.makeText(this, "No video found yet. Start playback and scan again.", Toast.LENGTH_LONG).show()
            return
        }
        val items = media.values.toList()
        val labels = items.map {
            val host = MediaUrlDetector.parseHttps(it.url)?.host.orEmpty()
            "${it.kind.label} · $host · ${it.discoveredBy}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Detected videos (${items.size})")
            .setItems(labels) { _, index ->
                val item = items[index]
                if (item.kind.isDirect) downloadDirect(item.url, item.kind, GeckoSession.getDefaultUserAgent())
                else if (item.kind == MediaKind.HLS) prepareHls(item.url, item.referrerOrigin)
                else AlertDialog.Builder(this)
                    .setTitle(item.kind.label)
                    .setMessage("DASH is detected, but downloading it is planned for milestone M3.")
                    .setPositiveButton("OK", null).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Fetch and parse the selected HLS playlist away from the UI thread.
     * A master playlist yields a quality picker; a media playlist downloads directly.
     */
    private fun prepareHls(url: String, observedReferrerOrigin: String? = null) {
        val ua = GeckoSession.getDefaultUserAgent()
        val referrer = observedReferrerOrigin ?: currentUrl
        Toast.makeText(this, "Checking HLS qualities…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val playlist = HlsPlaylistParser.parse(url, HlsHttp.loadPlaylist(applicationContext, url, ua, referrer))
                runOnUiThread {
                    if (closed) return@runOnUiThread
                    when (playlist) {
                        is HlsPlaylist.Video -> startHls(url, ua, referrer)
                        is HlsPlaylist.Master -> {
                            AlertDialog.Builder(this)
                                .setTitle("Choose video quality")
                                .setItems(playlist.variants.map { it.displayName }.toTypedArray()) { _, index ->
                                    startHls(playlist.variants[index].url, ua, referrer)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!closed) AlertDialog.Builder(this)
                        .setTitle("Unsupported HLS stream")
                        .setMessage(error.message ?: "Could not read playlist")
                        .setPositiveButton("OK", null).show()
                }
            }
        }.start()
    }

    private fun startHls(url: String, userAgent: String, referrer: String) {
        val intent = Intent(this, HlsDownloadService::class.java).apply {
            action = HlsDownloadService.ACTION_DOWNLOAD
            putExtra(HlsDownloadService.EXTRA_URL, url)
            putExtra(HlsDownloadService.EXTRA_AGENT, userAgent)
            putExtra(HlsDownloadService.EXTRA_REFERRER, referrer)
        }
        try {
            startForegroundService(intent)
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 204)
            }
            Toast.makeText(this, "HLS download started. Check notifications for progress.", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            Toast.makeText(this, "Could not start download: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadDirect(url: String, kind: MediaKind, userAgent: String?) {
        if (MediaUrlDetector.parseHttps(url) == null || !kind.isDirect) return
        try {
            val filename = MediaUrlDetector.safeFilename(url, kind, System.currentTimeMillis())
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(filename)
                .setDescription("Downloaded with StreamCatch")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "StreamCatch/$filename")
            if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
            // Gecko's browser cookie jar is independent of Android WebView/DownloadManager.
            // Do not attach unrelated WebView cookies to a Gecko-discovered URL.
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Downloading to Downloads/StreamCatch", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            Toast.makeText(this, "Download couldn't start: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (canGoBack) browserSession.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("lastUrl", currentUrl)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::runtime.isInitialized && ::browserSession.isInitialized) {
            runtime.webExtensionController.setTabActive(browserSession, true)
        }
    }

    override fun onPause() {
        if (::runtime.isInitialized && ::browserSession.isInitialized) {
            runtime.webExtensionController.setTabActive(browserSession, false)
        }
        super.onPause()
    }

    override fun onDestroy() {
        closed = true
        runtime.webExtensionController.setPromptDelegate(null)
        geckoView.releaseSession()
        browserSession.close()
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
