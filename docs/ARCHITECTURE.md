# Architecture — Android-first hybrid

Stock Firefox for Android does not permit the desktop Firefox native-messaging + Python host architecture. Android is the standalone primary app.

Embedded WebView browser → media request and DOM scan → deduplicated media sources → Android DownloadManager for direct downloads.

For HLS/DASH: future in-app background downloading and packaging engine with explicit user choice of variant/quality. AndroidX Media3 offline cache alone does not guarantee an ordinary MP4 export.

An optional Firefox for Android add-on may detect sources while browsing Firefox and share user-selected URLs with the app. Browser auth/cookies must not be silently copied; shared links will need validation.

Security: HTTPS only, no addJavascriptInterface on untrusted pages, no passive bulk downloading, no DRM circumvention.

Development milestones: M0/M1 browser/direct media → M2 HLS → M3 DASH → M4 queue/resume → M5 Firefox companion → M6 release.

## GeckoView / uBlock Origin migration (PR #7, under test)

- GeckoView renders the browser; the official **Mozilla-signed** uBlock Origin XPI is installed **only with user approval** from addons.mozilla.org and persists separately from app code.
- A separate app-owned `assets/media-bridge/` WebExtension observes HTTPS network media requests plus video/source DOM and performance entries. Native messaging sends only URL/source observations. It never blocks responses, executes injected page scripts or transfers cookies. The existing Kotlin `MediaUrlDetector` validates each candidate.
- GeckoView exposes the uBO browser action. StreamCatch provides a GeckoSession-backed action popup and dashboard; enable/disable/update are explicit user actions. Filter-list configuration belongs to uBO.
- Direct MP4/WebM and the existing HLS download/remux service are reused, not rewritten. APK signing identity and GitHub Releases publication are unchanged. No Actions artifact uploads or caches.
- **Known gap:** Android DownloadManager and HLS network transports do not inherit GeckoView session cookies. Signed-in / cookie-protected downloads are **not** validated and require a Gecko-aware download transport. Do not substitute Android WebView cookies.
- **Gate:** CI must compile, test and verify signing; then test real-device browser navigation, uBO page filtering, popup/settings, direct downloads, HLS quality/MP4/TS fallback, back navigation, process recreation, popup handling and signed APK update. Do not merge/publish merely because CI is green.
