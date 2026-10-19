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

## Verification evidence — 2026-10-19
- Feature branch source commit `550bab526ea0c64af7f3ca8d534adaedf1f78c53` passed Android CI: `https://github.com/kandyman991/Media-downloader/actions/runs/39250226188`.
- CI `verify` completed SUCCESS: unit tests, debug assemble and disposable-key signed release APK smoke test. The `publish-signed-apk` job was SKIPPED; **no APK or Actions artifact was uploaded**.
- Successful compiler toolchain in this feature branch: Android compile SDK 37.0, AGP 9.1.1, Gradle 9.3.1, Kotlin Gradle plugin 2.4.20 with temporary AGP built-in Kotlin opt-out; targetSdk remains 35.
- **Still NOT device-verified**: authentic uBO install/popup/dashboard/site filtering, GeckoView media detection and video playback, HLS/MP4/TS regressions, authenticated downloads, real device signing/update. PR #7 must remain open and unpublished pending device validation and issue #8.
- **Next exact action:** test a debug preview APK on Android (without replacing installed signed app), verify GeckoView/uBO popup and media detection, then implement and test cookie-safe Gecko-native downloads before releasing.

## 2026-10-19 — Preview HLS HTTP 403 on Android, Gecko network transport
- User-installed `geckoview-preview-cc13f3213a90` reported notification `StreamCatch — HLS failed: Media server returned HTTP 403` while fetching HLS. No site URL supplied; do not assert a unique root cause.
- Confirmed PR #7's HLS downloader still used Android `CookieManager` even though browsing moved to GeckoView (separate cookies/session). Fixed on branch by sharing one GeckoRuntime between browser and foreground service and fetching HTTPS HLS playlists/TS segments through `GeckoWebExecutor` with profile-managed cookies (no manual cookie forwarding), bounded redirects, sanitized referrer origin, and stage/host/HTTP-status errors without logging signed URLs.
- Added pure unit tests for referrer token redaction, HTTPS enforcement, userinfo rejection and IPv6 origin formatting. Direct MP4/WebM via Android DownloadManager still lacks Gecko authenticated-session parity: see issue #8.
- **Verified CI run** https://github.com/kandyman991/Media-downloader/actions/runs/39256226689 completed SUCCESS: `verify` and `publish-geckoview-preview` succeeded; stable `publish-signed-apk` skipped. Preview APK was built, identity/signed checked and published **directly to GitHub Releases** with zero Actions artifact storage.
- **New preview release:** https://github.com/kandyman991/Media-downloader/releases/tag/geckoview-preview-89788d1e583a
- **Exact release source commit:** `89788d1e583adb3d805cfcdd801067ab78d146e5`. Preview package `dev.streamcatch.android.preview`; debug-signed (uninstall the older *preview* before installing new preview; do not uninstall stable StreamCatch).
- **Device test remains open:** Retry the SAME HLS stream on the new preview; determine whether 403 is from playlist or segment. 403 could still reflect expired signatures, legitimate server permissions or other policy; never claim bypass. Check uBO popup/filters, direct downloads and HLS/MP4/TS behavior independently.
- **Next exact action:** obtain the new preview's device result and fix any confirmed regression; keep PR #7 open and unmerged until real-device acceptance and issue #8's direct-download auth parity are resolved.

## 2026-10-19 — Preview APK 404 + oversized universal GeckoView fixed
- User reported **HTTP 404 downloading the APK**, not the HLS video. Root cause verified via GitHub Releases API: the previous preview release stored the actual filename `app-preview.apk`; `gh release upload PATH#LABEL` only applied a *display label*, while published links incorrectly targeted `StreamCatch-GeckoView-...apk`.
- GitHub release workflow fixed to **physically rename/copy** the APK to its intended asset basename before upload and to publish a matching SHA-256 named checksum.
- Previous universal GeckoView preview APK: **542,452,660 bytes** (~542 MB). Mozilla officially publishes `geckoview-arm64-v8a` at our pinned version `158.0.20261007201254`; the preview now uses that **ARM64-only AAR**, leaving debug/release universal, with `ndk.abiFilters.add("arm64-v8a")`.
- Preview CI verifies package `dev.streamcatch.android.preview`, APK signature, ARM64-only `lib/` entries and a **300 MiB max-size gate**, without consuming Actions artifact storage.
- **Verified green run:** https://github.com/kandyman991/Media-downloader/actions/runs/39268496434. Both `verify` and `publish-geckoview-preview` completed success; production release publishing skipped. Actual new APK **208,781,547 bytes** (~209 MB), 61.5% smaller.
- **Verified GitHub API release tag and actual asset URL:** https://github.com/kandyman991/Media-downloader/releases/tag/geckoview-preview-f7fd8e391aac
  APK https://github.com/kandyman991/Media-downloader/releases/download/geckoview-preview-f7fd8e391aac/StreamCatch-GeckoView-geckoview-preview-f7fd8e391aac.apk
- **Release source SHA:** `f7fd8e391aacdeb8f907a1eac164e2bd0d0c4d33` (before this documentation-only commit). Debug signing remains ephemeral. **Uninstall only the previous StreamCatch Preview**, not the stable app, to install this preview.
- Next exact action: collect device validation of genuine uBO install/filter UI and whether the earlier HLS 403 reproduces with Gecko profile HLS transport. Do not merge PR #7 on CI alone. Issue #8 direct authenticated MP4/WebM still open.
