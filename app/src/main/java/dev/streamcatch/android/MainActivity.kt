package dev.streamcatch.android

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
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
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import dev.streamcatch.android.core.MediaKind
import dev.streamcatch.android.core.MediaUrlDetector
import org.json.JSONArray
import org.json.JSONTokener
import java.util.LinkedHashMap

/** Android-first MVP: HTTPS page inspection + OS-managed direct downloads. */
class MainActivity : Activity() {
    private data class MediaCandidate(val url: String, val kind: MediaKind, val discoveredBy: String)

    private lateinit var webView: WebView
    private lateinit var address: EditText
    private lateinit var mediaButton: Button
    private lateinit var progress: ProgressBar
    private val media = LinkedHashMap<String, MediaCandidate>()
    private var closed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeUi()
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            openSharedPage(intent) || navigate("https://example.org")
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
        toolbar.addView(btn("‹") { if (webView.canGoBack()) webView.goBack() }, LinearLayout.LayoutParams(54.dp, ViewGroup.LayoutParams.WRAP_CONTENT))
        address = EditText(this).apply {
            hint = "Enter a website URL"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true }
        }
        toolbar.addView(address, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(btn("Go") { navigate(address.text.toString()) })
        layout.addView(toolbar)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        layout.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 3.dp))

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            isHorizontalScrollBarEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    if (request.isForMainFrame && MediaUrlDetector.parseHttps(request.url.toString()) == null) {
                        Toast.makeText(this@MainActivity, "Only HTTPS pages are supported", Toast.LENGTH_SHORT).show()
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    clearMedia()
                    address.setText(url.orEmpty())
                    this@MainActivity.progress.progress = 0
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    scanPage()
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                    if (request.method.equals("GET", ignoreCase = true)) {
                        registerMedia(request.url.toString(), null, "Network")
                    }
                    // Never proxy or modify playback. Let WebView perform the request.
                    return null
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@MainActivity.progress.progress = newProgress
                }
            }
            setDownloadListener(DownloadListener { url, userAgent, _, mimeType, _ ->
                // MIME types can reveal videos without filename extensions.
                val kind = MediaUrlDetector.classify(url, mimeType)
                if (kind != null) {
                    registerMedia(url, mimeType, "Browser download")
                    if (kind.isDirect) downloadDirect(url, kind, userAgent)
                    else Toast.makeText(this@MainActivity, "${kind.label} detected; stream engine is not implemented yet", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Unsupported download type", Toast.LENGTH_LONG).show()
                }
            })
        }
        layout.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

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
        webView.loadUrl(url)
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

    private fun registerMedia(url: String, mimeType: String?, foundBy: String) {
        val kind = MediaUrlDetector.classify(url, mimeType) ?: return
        runOnUiThread {
            if (closed || media.containsKey(url) || media.size >= 100) return@runOnUiThread
            media[url] = MediaCandidate(url, kind, foundBy)
            mediaButton.text = "Videos (${media.size})"
        }
    }

    private fun scanPage() {
        // No addJavascriptInterface on third-party pages: only read safe JSON output.
        val js = """(function() {
          const urls = new Set();
          for (const el of document.querySelectorAll('video, video source, source')) {
            if (el.currentSrc) urls.add(el.currentSrc);
            if (el.src) urls.add(el.src);
          }
          for (const entry of performance.getEntriesByType('resource')) {
            if (/\.(m3u8|mpd|mp4|m4v|webm|mov)(?:[?#]|$)/i.test(entry.name)) urls.add(entry.name);
          }
          return JSON.stringify(Array.from(urls).slice(0, 250));
        })();""".trimIndent()
        webView.evaluateJavascript(js) { encoded ->
            try {
                val json = JSONTokener(encoded).nextValue() as? String ?: return@evaluateJavascript
                val urls = JSONArray(json)
                for (i in 0 until urls.length()) registerMedia(urls.getString(i), null, "Page")
            } catch (_: Exception) {
                // A cross-origin frame or page navigation may prevent inspection.
            }
        }
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
                if (item.kind.isDirect) downloadDirect(item.url, item.kind, webView.settings.userAgentString)
                else AlertDialog.Builder(this)
                    .setTitle(item.kind.label)
                    .setMessage("Stream was found, but downloading HLS/DASH playlists will be added in a later milestone. No download has started.")
                    .setPositiveButton("OK", null).show()
            }
            .setNegativeButton("Close", null)
            .show()
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
            // Only use media-domain cookies; never send the current page's cookies to another host.
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) request.addRequestHeader("Cookie", cookie)
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Downloading to Downloads/StreamCatch", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            Toast.makeText(this, "Download couldn't start: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        closed = true
        webView.destroy()
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
