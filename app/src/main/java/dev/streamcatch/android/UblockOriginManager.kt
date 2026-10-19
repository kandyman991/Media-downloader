package dev.streamcatch.android

import android.app.Activity
import android.app.AlertDialog
import android.widget.FrameLayout
import android.widget.Toast
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Installs genuine Mozilla-signed uBlock Origin. The APK does not bundle/fork uBO.
 * User approval is required before installation and for newly requested permissions.
 */
class UblockOriginManager(
    private val activity: Activity,
    private val runtime: GeckoRuntime,
    private val browserSession: GeckoSession,
    private val onStateChanged: (Boolean) -> Unit
) {
    companion object {
        const val EXTENSION_ID = "uBlock0@raymondhill.net"
        private const val OFFICIAL_XPI =
            "https://addons.mozilla.org/firefox/downloads/latest/uBlock0@raymondhill.net/latest.xpi"
    }
    private val controller = runtime.webExtensionController
    private var extension: WebExtension? = null
    private var installationApproved = false
    private var defaultAction: WebExtension.Action? = null
    private var sessionAction: WebExtension.Action? = null

    fun initialize() {
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                val accepted = installationApproved && extension.id == EXTENSION_ID
                installationApproved = false
                return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(accepted, false, false))
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>
            ): GeckoResult<AllowOrDeny> =
                askForPermissions("Additional uBlock Origin permissions requested", permissions, origins)

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<String>,
                newOrigins: Array<String>,
                newDataCollectionPermissions: Array<String>
            ): GeckoResult<AllowOrDeny> =
                askForPermissions("uBlock Origin update requires new permissions", newPermissions, newOrigins)
        })
        controller.list().accept({ addons ->
            addons?.firstOrNull { it.id == EXTENSION_ID }?.let(::activate)
            onStateChanged(extension != null)
        }, { error ->
            showError("Could not load add-ons: ${error?.message ?: "Unknown error"}")
        })
    }

    private fun askForPermissions(title: String, permissions: Array<String>, origins: Array<String>): GeckoResult<AllowOrDeny> {
        val result = GeckoResult<AllowOrDeny>()
        val details = (permissions.toList() + origins.toList()).joinToString("\n").ifBlank { "Additional permissions" }
        activity.runOnUiThread {
            AlertDialog.Builder(activity).setTitle(title).setMessage(details)
                .setPositiveButton("Allow") { _, _ -> result.complete(AllowOrDeny.ALLOW) }
                .setNegativeButton("Deny") { _, _ -> result.complete(AllowOrDeny.DENY) }
                .setOnCancelListener { result.complete(AllowOrDeny.DENY) }
                .show()
        }
        return result
    }

    private fun activate(addon: WebExtension) {
        extension = addon
        val actions = object : WebExtension.ActionDelegate {
            override fun onBrowserAction(
                extension: WebExtension,
                session: GeckoSession?,
                action: WebExtension.Action
            ) {
                if (session == null) defaultAction = action
                else if (session === browserSession) sessionAction = action
            }

            override fun onTogglePopup(
                extension: WebExtension,
                action: WebExtension.Action
            ): GeckoResult<GeckoSession> = GeckoResult.fromValue(openPopup())

            override fun onOpenPopup(
                extension: WebExtension,
                action: WebExtension.Action
            ): GeckoResult<GeckoSession> = GeckoResult.fromValue(openPopup())
        }
        addon.setActionDelegate(actions)
        browserSession.webExtensionController.setActionDelegate(addon, actions)
        onStateChanged(addon.metaData.enabled)
    }

    private fun openPopup(url: String? = null): GeckoSession {
        val popupSession = GeckoSession()
        popupSession.open(runtime)
        val geckoView = GeckoView(activity)
        geckoView.setSession(popupSession)
        val frame = FrameLayout(activity)
        frame.addView(geckoView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, (460 * activity.resources.displayMetrics.density).toInt()
        ))
        val dialog = AlertDialog.Builder(activity)
            .setTitle("uBlock Origin")
            .setView(frame)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            geckoView.releaseSession()
            popupSession.close()
        }
        dialog.show()
        if (url != null) popupSession.loadUri(url)
        return popupSession
    }

    fun showControls() {
        val addon = extension
        if (addon == null) {
            AlertDialog.Builder(activity)
                .setTitle("Install uBlock Origin?")
                .setMessage("Download the official Mozilla-signed uBlock Origin extension from addons.mozilla.org. It needs access to browser tabs and web requests to block ads and trackers. Installation requires internet access.")
                .setPositiveButton("Install") { _, _ ->
                    installationApproved = true
                    controller.install(OFFICIAL_XPI, WebExtensionController.INSTALLATION_METHOD_MANAGER)
                        .accept({ installed ->
                            installationApproved = false
                            if (installed != null && installed.id == EXTENSION_ID) activate(installed)
                            else showError("Unexpected extension identity")
                        }, { error ->
                            installationApproved = false
                            showError("uBlock Origin installation failed: ${error?.message ?: "Unknown error"}")
                        })
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val enabled = addon.metaData.enabled
        val choices = arrayOf(
            "Open uBlock Origin popup",
            "Open dashboard and filter lists",
            if (enabled) "Disable uBlock Origin" else "Enable uBlock Origin",
            "Check for updates"
        )
        AlertDialog.Builder(activity).setTitle("uBlock Origin")
            .setItems(choices) { _, index ->
                when (index) {
                    0 -> (sessionAction?.let { action ->
                        defaultAction?.let(action::withDefault) ?: action
                    } ?: defaultAction)?.click() ?: showError("The extension popup is not ready; reload the page")
                    1 -> addon.metaData.optionsPageUrl?.let(::openPopup)
                        ?: showError("This extension has no dashboard page")
                    2 -> {
                        val result = if (enabled) controller.disable(addon, WebExtensionController.EnableSource.USER)
                            else controller.enable(addon, WebExtensionController.EnableSource.USER)
                        result.accept({ updated ->
                            if (updated != null) activate(updated) else showError("Extension state change returned no extension")
                        }, { error -> showError("Could not change extension state: ${error?.message ?: "Unknown error"}") })
                    }
                    3 -> controller.update(addon).accept({
                        if (it != null) activate(it)
                        else showError("uBlock Origin is already up to date")
                    }, { showError("Extension update failed: ${it?.message ?: "Unknown error"}") })
                }
            }.setNegativeButton("Close", null).show()
    }

    private fun showError(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
