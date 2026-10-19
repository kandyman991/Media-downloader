package dev.streamcatch.android

import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * An independent built-in extension observes browser URLs; uBlock Origin remains an
 * unmodified, separately installed Mozilla-signed extension.
 *
 * All messages are untrusted and must pass MediaUrlDetector checks in MainActivity.
 */
class GeckoMediaBridge(
    private val onMedia: (String, String, String?) -> Unit
) {
    private var nativePort: WebExtension.Port? = null

    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/media-bridge/",
            "streamcatch-media@local"
        ).accept({ extension ->
            extension?.setMessageDelegate(object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    nativePort = port
                    port.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, port: WebExtension.Port) {
                            val json = message as? JSONObject ?: return
                            if (json.optString("type") != "media") return
                            val url = json.optString("url")
                            if (url.length !in 1..8192) return
                            onMedia(url, json.optString("source", "Gecko"), json.optString("referrerOrigin").takeIf { it.isNotBlank() })
                        }
                        override fun onDisconnect(port: WebExtension.Port) {
                            if (nativePort === port) nativePort = null
                        }
                    })
                }
            }, "streamcatch")
        }, { error ->
            Log.e("StreamCatch", "Media bridge failed to initialize", error)
        })
    }

    fun scan() {
        nativePort?.postMessage(JSONObject().put("type", "scan"))
    }
}
