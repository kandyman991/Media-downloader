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

## 2026-10-19 — HLS playback succeeds but downloader segment 403; request-origin parity milestone

- User confirmed the supplied webpage `https://www.tinyzone.is/movie/leyla-and-mecnun-1983/` **plays normally in GeckoView** but StreamCatch Preview's downloader shows `HLS failed: HLS segment HTTP 403 from metonymicmosaic.website`. The playlist is successfully loaded/parsed and at least one media segment is denied during a separate request. **Do not assert access permission or a guaranteed root cause**. Site availability cannot be independently reproduced.
- Confirmed code mismatch: `MainActivity.prepareHls` previously used top-level page `currentUrl` for all HLS Referers. An embedded player can send a different frame/request origin or no Referer at all. A genuine GeckoView playback request differs from our `GeckoWebExecutor.fetch` HLS request.
- Updated built-in media WebExtension to observe actual `webRequest.onBeforeSendHeaders` requests and capture **only sanitized HTTPS Referer origin**, never Cookie, Authorization, Referer path or signed query. DOM discoveries carry their actual frame origin. `GeckoMediaBridge` and `MainActivity` retain per-candidate observed origin and prioritize actual request-header evidence; specifically preserve absence of browser Referer instead of fabricating a top-level one.
- HLS errors now include **failed segment index / total** before the already-redacted stage/status/host. Retain original HTTPS-only redirect, size, codec/DRM and TS→MP4 gates. No site-specific access-control bypass.
- Added `tools/test-media-bridge.cjs` executable Node regression tests for browser request origin, no-Referer, userinfo and token/header redaction, active-tab scoping, and iframe origin; CI executes the test in verification and publishing jobs.
- **Green Actions run** https://github.com/kandyman991/Media-downloader/actions/runs/39275658855: verify succeeded; separate ARM64 preview publication succeeded; stable signed release skipped; no Actions artifacts/caches. **Published source commit:** `f6cdb4849986c87e0f9dd00bb04c22d60ad401e6`.
- **API-verified release** https://github.com/kandyman991/Media-downloader/releases/tag/geckoview-preview-f6cdb4849986 with actual asset `StreamCatch-GeckoView-geckoview-preview-f6cdb4849986.apk` (**208,782,051 bytes**) at https://github.com/kandyman991/Media-downloader/releases/download/geckoview-preview-f6cdb4849986/StreamCatch-GeckoView-geckoview-preview-f6cdb4849986.apk . Experimental preview debug key rotates: uninstall previous **Preview only** before new installation.
- **Still unproven:** whether playback-authorized stream segments can be fetched separately after this referrer-context fix. Device must test the same HLS stream and report new index/host/status if still 403; never circumvent legitimate site access policy. Other Gecko/uBO real-device gates and issue #8's authenticated direct downloads remain open. **Keep PR #7 unmerged.**

## 2026-10-19 — Actual device regression test: previously denied HLS segments now download
- User retested the **same stream** using GeckoView ARM64 preview `geckoview-preview-f6cdb4849986` after request/frame-origin compatibility fix and reports: **"Now it's downloading the segments"**.
- This is direct **positive device evidence** that individual HLS segment requests now succeed, after previous `HLS segment HTTP 403 from metonymicmosaic.website`.
- **Not yet complete or accepted:** full segment download, successful MPEG-TS remux to MP4 or playable TS fallback, audio/video playback, notification, cleanup. Do not conflate segment progress with successful saved video.
- Exact next action: ask user whether it completes and whether saved file has playable audio/video; fix any observed remux/playback regression. PR #7 remains open for broader Gecko/uBO gates and issue #8.

## 2026-10-19 — Complete saved MP4 playable, but slow playback startup
- User's Samsung player screenshot shows StreamCatch-downloaded **MP4 visibly playing at 30:07/1:25:33** after the HLS segment-download fix in ARM64 preview `geckoview-preview-f6cdb4849986`. This is **actual on-device evidence of completed download, remux, and readable video**. **Audio not yet explicitly verified.**
- New issue **#9**: https://github.com/kandyman991/Media-downloader/issues/9 — user reports **very slow startup of local MP4 playback**; no measured duration, file size or comparison with VLC yet. `Mp4Remuxer` uses MediaExtractor/MediaMuxer and verifies only first video sample. Possible `moov` tail metadata or another indexing/seeking issue; not established from screenshot. Any future fast-start atom reorder must validate `stco`/`co64` and preserve original MP4 on failure without disrupting TS fallback.
- PR #7 retains independent open blockers: uBO and other GeckoView device acceptance, direct Gecko authenticated downloads #8, and MP4 playback performance #9. **Do not merge solely on one playable video.**

## 2026-10-19 — Audio missing from downloaded MP4; prioritize over small playback delay

- **User's actual device result:** the 1:25:33 downloaded MP4 opens and visibly plays, but **audio is not working**. Startup delay was clarified as merely **a few seconds**, so MP4 fast-start is **not the primary blocker**. Issue #9 is lower-priority pending timing evidence.
- Confirmed existing architectural gap: `Mp4RemuxPolicy.canRemux` accepts one AVC/HEVC video track and **zero audio tracks**, and old `Mp4Remuxer.verifyMp4` only verified a video sample, so a video-only MP4 was wrongly presented as a fully successful movie download. Existing `HlsPlaylistParser.parseMaster` **rejects separate AUDIO rendition groups**; a directly observed video-only media playlist bypasses that check and has no companion audio in the TS.
- **New issue #10:** https://github.com/kandyman991/Media-downloader/issues/10 — root-cause diagnosis and later support for DRM-free HLS alternate audio renditions with correct A/V sync. Do NOT claim it is implemented.
- **Code changes on feature PR #7:** `Mp4Remuxer.remux` now returns source audio/sample presence; `verifyMp4` checks an actual audio sample if the track exists and rejects losing source audio; `Mp4RemuxPolicy.preservesAudio` and unit tests enforce parity. `HlsDownloadService` clearly labels a video-only MP4 as **no audio track detected** rather than claiming working A/V. Existing TS fallback, download and signing architecture preserved. This is a **diagnostic/safety improvement**, not yet a fix for a separate audio rendition or silent playback despite a track being present.
- **Next exact step:** obtain the result of CI then install and test a preview only when the above audio diagnosis is needed; ask user to play already downloaded MP4 in VLC (without redownloading) and compare audio. If VLC has sound, inspect Samsung playback compatibility and mux timing; if neither has sound, inspect input/output audio tracks and whether there is a distinct HLS audio rendition. Keep PR #7 unmerged and issue #8 open.

## 2026-10-19 — CORRECTION: VLC plays downloaded MP4 with sound; Samsung player is silent
- User **tested the exact same StreamCatch-downloaded movie MP4 in VLC on Android and confirmed both video and audio work correctly**. Samsung's built-in video player showed video but no sound. Local playback startup took only **a few seconds**.
- This **overrides the preceding hypotheses** suggesting missing downloaded soundtrack for this stream. HLS segments, lossless TS→MP4 remux and saved audio are functionally verified **for this one file/device under VLC**. Do not redo downloads or implement separate audio renditions merely to fix this movie.
- [Issue #9](https://github.com/kandyman991/Media-downloader/issues/9) now tracks **Samsung player MP4 compatibility (video works, no sound)**. Capture codecs, audio channel/sample rate, track metadata and sample timestamps with nonsecret, authorized test content before altering MediaMuxer, timestamp handling, or atom layout; fast-start is NOT supported as a root-cause fix.
- [Issue #10](https://github.com/kandyman991/Media-downloader/issues/10) remains an independent **future feature** for sites that genuinely separate HLS audio and video. Confirmed not responsible for this file. Keep the new defensive checks (audio parity verification and honest video-only output labels) but do not claim they fix this report.
- The user can currently watch their downloaded movie with audio in VLC without re-downloading. PR #7 is still open pending full Gecko/uBlock and download-transport gates, including issue #8.

## 2026-10-19 — Media Center M1 / LibVLC integrated in stacked PR #11

- User requested to turn StreamCatch into an offline media center, inspired by successful audio+video playback of a StreamCatch-downloaded MP4 in the *external* VLC app, whereas Samsung's player was silent. Goal: **internal LibVLC playback**, library, eventually posters/series/subtitles/travel mode; preserve working browser/download service.
- Open separate **draft stacked PR #11** https://github.com/kandyman991/Media-downloader/pull/11 on `feature/media-center-libvlc`, targeting `feature/geckoview-ublock-foundation` (PR #7), not `main`. **Neither PR is merged** and existing signed release remains intact.
- Implementation: official `org.videolan.android:libvlc-all:3.6.1` (LGPL-2.1) linked inside app; new `MediaCenterActivity` + `LibVlcPlayerActivity`. Browser has **Library ▶** footer; library lists app-visible `Downloads/StreamCatch` media via MediaStore, plus SAF-imported user media with persistent read grants; no broad storage permissions. Player opens `content://` file descriptor into LibVLC, plays locally with pause/seek/±10s, remembers position, closes all decoder/file resources. Dark UI and attribution. Policy unit tests added. `docs/MEDIA_CENTER_ROADMAP.md` lists future steps and acceptance contract.
- First stacked PR #11 source at `91537999684066c29bcff4a708bb9d05d44481fe` passed verify run https://github.com/kandyman991/Media-downloader/actions/runs/39293802754 (compilation, tests, signing smoke). A subsequent preview-workflow refactor inadvertently produced **invalid GitHub Actions YAML** (JS String.replace replacement containing `$'` interpolated trailing source). Reconstructed from last green PR #7 version, reapplying only isolated PR #11 preview condition with function-style replacements. Record as **failed approach; don't repeat**.
- Final verified successful workflow **https://github.com/kandyman991/Media-downloader/actions/runs/39295335266** for exact code commit **`5a99e77826748fb00d304c1173960b47f9dc1320`**. `verify` and `publish-geckoview-preview` both SUCCESS, production `publish-signed-apk` skipped. No Actions artifact/cache uploads. Preview package `dev.streamcatch.android.preview`, debug-signed with ephemeral key, ARM64-only with `libvlc.so` presence check, valid APK signature, below 300 MiB limit.
- **Actual preview APK 253,847,660 bytes (~254 MB)**, up ~45 MB from previous GeckoView-only 208,782,051 bytes. Published and actual asset URL verified from GitHub Releases API:
  https://github.com/kandyman991/Media-downloader/releases/tag/media-center-preview-5a99e7782674
  https://github.com/kandyman991/Media-downloader/releases/download/media-center-preview-5a99e7782674/StreamCatch-MediaCenter-media-center-preview-5a99e7782674.apk
- **Not yet Android-device validated:** actually opening `Library ▶`, importing the previously saved movie from `Downloads/StreamCatch` using `Add local file` after uninstalling the old preview, LibVLC audio+video playback, seeking, resume, and true airplane-mode local playback. Do NOT call M1 device-accepted until tested.
- **Next exact action:** ask user to uninstall ONLY old **StreamCatch Preview** (ephemeral signing cert); install new 254 MB media-center preview; open `Library ▶`, tap **Add local file** to select existing downloaded movie (important if previous preview file lost MediaStore app ownership after uninstall); play in integrated LibVLC, confirm video and sound, controls and airplane mode. If failure, capture screenshot and logs with no private URLs/secrets. Keep #11 draft until verified and PR7 gates are respected.

## 2026-10-19 — Media Center black video, working audio: managed LibVLC output fix
- **User real-device evidence:** first Media Center preview `media-center-preview-5a99e7782674` showed black video rectangle in landscape while VLC player status/timeline advanced (`19:26 / 1:25:33`) and **audio was working**. The same MP4 plays picture+sound in standalone VLC. This is an embedded video-output defect, not evidence of missing downloaded video data. See [issue #12](https://github.com/kandyman991/Media-downloader/issues/12).
- Root-cause **hypothesis** from code review, not proven without new phone test: our player used a bare `SurfaceView` with `setBackgroundColor(Color.BLACK)`, attached the low-level `vlcVout` without LibVLC's managed surface layout, and started playback before sizing. Time/audio alone don't prove video output exists.
- Draft stacked PR [#11](https://github.com/kandyman991/Media-downloader/pull/11) now uses the official `VLCVideoLayout` inside a weighted FrameLayout and calls `MediaPlayer.attachViews(videoLayout, null, false, true)` to manage native video track/surface and use TextureView by default. Cleanup calls `mp.detachViews()`. A visible **Video output: Texture/Surface** switch allows user to retry alternative rendering without redownloading, preserving position. No download/browser changes.
- **Verified GREEN CI run** https://github.com/kandyman991/Media-downloader/actions/runs/39297741510 on code commit `9d960cfa8a3cfa804a669b5a252e4e4d3f85d7e8`: verification/tests/debug compile/release signing smoke passed; separate experimental preview publishing passed; stable release publishing skipped. Zero GitHub Actions artifacts/cache. Actual ARM64 APK **253,847,660 bytes**, under 300 MiB and includes LibVLC.
- **Verified actual GitHub API asset** https://github.com/kandyman991/Media-downloader/releases/tag/media-center-preview-9d960cfa8a3c ; APK https://github.com/kandyman991/Media-downloader/releases/download/media-center-preview-9d960cfa8a3c/StreamCatch-MediaCenter-media-center-preview-9d960cfa8a3c.apk .
- **Next exact action:** user uninstalls old **StreamCatch Preview** (ephemeral debug signing), installs new 254 MB preview, uses Library > Add local file if prior downloaded movie lost MediaStore ownership, and tests same saved movie for visible frames+audio, seeking/rotation. If Texture stays black, tap Video output to switch Surface and report which option works. **Do not claim runtime fix successful until observed device result**; keep #11 draft, #7 open and stable app unchanged.
