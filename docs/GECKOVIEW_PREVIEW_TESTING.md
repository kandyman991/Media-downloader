# StreamCatch GeckoView / uBlock Origin side-by-side preview

Experimental test only — **PR #7 must not be merged on CI alone**.

## Distribution and safety
- PR #7's `publish-geckoview-preview` job runs **only on our own same-repository feature branch** and **only after `verify` succeeds**.
- It builds `app-preview.apk`, checks its application ID/signature, and uploads that APK and SHA-256 **directly to GitHub Releases**. No `upload-artifact`, Gradle caches or Actions artifact storage.
- Package ID `dev.streamcatch.android.preview`; home-screen label **StreamCatch Preview**. This cannot overwrite the released package `dev.streamcatch.android`.
- Each hosted CI run creates a temporary debug signing certificate. **Uninstall an older StreamCatch Preview** before installing a newly built preview. Do **not** uninstall the stable StreamCatch app.
- Public preview assets are intentionally test-only: browsing, downloads and ad blocking are not claimed device-verified, and the existing working release remains available.
- Do not sign previews with release credentials, share personal login tokens, or use the preview for sensitive authenticated browsing while its security and cookie handling remain unverified.

## Getting the APK
1. Open [GitHub Releases](https://github.com/kandyman991/Media-downloader/releases).
2. Find the newest **StreamCatch GeckoView geckoview-preview-… (EXPERIMENTAL)** prerelease.
3. Under Assets select `StreamCatch-GeckoView-geckoview-preview-<commit>.apk`.
4. Install it in parallel with the existing app. Verify both launcher icons appear separately.
5. Report the preview release tag, device/Android version and failed test steps in PR #7.

## Device acceptance walkthrough (record pass/fail and diagnostics)
1. Open `https://example.org`, navigate to a second HTTPS page, back/forward and relaunch the app. Verify no crash and correct location.
2. Tap **uBO** then **Install**, inspect and approve the genuine AMO permissions. Verify it persists after restart.
3. On a page with ads/trackers, verify network and cosmetic blocking. Open the **uBO** popup, per-site power toggle, dashboard, filter lists, enable/disable and update actions.
4. Test media detection with a legal, unprotected direct MP4, e.g. `https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4`. Tap **Scan page**, then **Videos**; try direct downloading and playing the saved file.
5. On previously working **unencrypted HLS VOD** content, verify quality picker, playback, HLS-to-MP4 remux, and valid TS fallback. Test background download progress and cancellation.
6. Check that a blocked advertising resource is not mistakenly shown as downloadable media. Temporarily disable uBO for a legitimate site that needs it, then turn it back on.
7. Verify Android's original StreamCatch app still works and its app data remains intact.
8. **Authenticated video parity (release blocker)**: the Gecko cookie jar is *not* the Android DownloadManager/HLS service cookie jar. Record failures in issue #8; do not mark this passing without a cookie-safe Gecko transport and device evidence.
9. Never test DRM circumvention; unsupported DASH and fragmented HLS are outside this migration's scope.

## Diagnostic handoff
Include the preview release tag/SHA, app version shown, device and Android version, attempted site (redact signed URLs or secrets), steps to reproduce, and observable outcome. Logs must not include cookies or authentication tokens.

## Release gate
Only after PR #7 passes applicable CI + device acceptance and issue #8 is resolved or an explicitly scoped degraded-mode gate is approved may we release/merge the new browser as the default.
