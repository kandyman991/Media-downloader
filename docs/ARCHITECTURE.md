# Architecture — Android-first hybrid

Stock Firefox for Android does not permit the desktop Firefox native-messaging + Python host architecture. Android is the standalone primary app.

Embedded WebView browser → media request and DOM scan → deduplicated media sources → Android DownloadManager for direct downloads.

For HLS/DASH: future in-app background downloading and packaging engine with explicit user choice of variant/quality. AndroidX Media3 offline cache alone does not guarantee an ordinary MP4 export.

An optional Firefox for Android add-on may detect sources while browsing Firefox and share user-selected URLs with the app. Browser auth/cookies must not be silently copied; shared links will need validation.

Security: HTTPS only, no addJavascriptInterface on untrusted pages, no passive bulk downloading, no DRM circumvention.

Development milestones: M0/M1 browser/direct media → M2 HLS → M3 DASH → M4 queue/resume → M5 Firefox companion → M6 release.
