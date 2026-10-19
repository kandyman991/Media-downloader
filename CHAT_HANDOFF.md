# Chat Handoff — StreamCatch / Media Downloader

_Last curated: 2026-10-10._

## Recovery
Read `AGENTS.md`, this handoff, `README.md`, and `docs/ENGINEERING_DECISIONS.md`. Use `docs/FAILED_APPROACHES.md` to avoid regressions. Verify live `main` SHA, PRs and CI *before* resuming; this document is not a live GitHub snapshot.

## Role
Public Android-first WebView media detector and downloader; direct MP4/WebM and selected unencrypted HLS VOD workflows.

## Last documented direction
README describes M3.1 lossless HLS MPEG-TS-to-MP4 remux with a verified TS fallback. Latest main commit establishes persistent signed APK upgrades; its device upgrade outcome still requires actual verification.

## Binding constraints
- **Hybrid detection:** Use Android WebView/network resource observation to find browser media; do not assume every detected source is downloadable.
- **HLS first limits:** Support known validated unencrypted VOD forms; do not represent live, DRM or unsupported HLS/DASH formats as implemented.
- **Lossless remux:** Use the platform MediaExtractor/MediaMuxer where supported; verify MP4 output and fall back to a valid TS file instead of producing corruption.
- **Persistent signing:** Keep a stable release keystore in repository secrets, increment versionCode for app upgrades, and never expose signing secrets in public Git.
- **Zero Actions artifact storage:** Publish opt-in signed APKs through GitHub Releases, never upload-artifact; normal CI does not need APK publication.

## Next exact action
Verify that a signed GitHub Release APK built from the stable signing keystore updates in place, then proceed to M3.2 fragmented-MP4 HLS and later separated audio/video. Check current versionCode and GitHub status first.

## Handoff maintenance
After each meaningful PR/milestone record the exact commit, tested build/run link, real-device validation and blockers. Update decision and failure registers when evidence changes. Do **not** assert an APK is current from a past handoff. ChatGPT chats are not mirrored here automatically.

## 2026-10-19 GeckoView/uBlock migration work
- PR **#7** (`feature/geckoview-ublock-foundation`) introduces GeckoView, a separate media-observer WebExtension and installation/management of official signed uBlock Origin.
- **Do not infer green CI or real-device success from the PR.** Check current PR/CI; compare against main; fix compile/runtime issues as needed.
- Critical acceptance gap: Android DownloadManager/HLS do **not** share GeckoView browsing cookies. Session-authenticated download parity is not proven.
- Latest working signed release must remain available; do not squash with `[apk]` until feature passes actual device tests, including APK update certificate continuity.
- Next actions: review CI, complete native popup/media tests, validate cookie-aware authenticated downloading, then decide on merge/release.
