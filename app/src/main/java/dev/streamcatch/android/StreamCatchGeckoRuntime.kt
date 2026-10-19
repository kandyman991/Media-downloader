package dev.streamcatch.android

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime

/**
 * Single process-wide Gecko profile shared by the browser Activity and foreground downloader.
 * Do not create a different GeckoRuntime for the HLS service: that would lose the
 * browser's cookie context after switching from Android WebView.
 */
object StreamCatchGeckoRuntime {
    @Volatile private var current: GeckoRuntime? = null

    @Synchronized
    fun get(context: Context): GeckoRuntime {
        current?.let { return it }
        return GeckoRuntime.create(context.applicationContext).also { current = it }
    }
}
