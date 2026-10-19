# StreamCatch Media Center — staged implementation contract

## Architecture

**One app, two independent engines:** GeckoView + uBlock Origin for web browsing/media discovery; the existing Gecko-aware HLS/MP4 download services for saving lawful unprotected content; VideoLAN's **LibVLC 3.6.1** for decoding user-accessible *offline local media*. Media center never transfers cookies, bypasses DRM or starts a download on its own.

This feature is deliberately **stacked on** `feature/geckoview-ublock-foundation`, pending #7's independent acceptance. Do not merge the VLC media center into `main` separately from a verified Gecko integration or broaden unrelated release risk. Preserve `main` release and signing keys.

## M1 — Offline Library + LibVLC player (implemented; CI/device gates pending)

- Browser footer has **Library ▶** button. Library indexes only MediaStore downloads under `Downloads/StreamCatch` owned/visible to this app, and user-selected SAF document URIs with persisted grants. No permission to browse arbitrary private Android files.
- Local file picker supports video and audio; metadata list includes filename and size. Tap item → **embedded LibVLC player** (not external app); pause/play, ±10s, time display, timeline seek, last position saved locally. Playback in airplane mode does not contact services.
- The player only accepts `content://` with OS-granted read access. A local `ParcelFileDescriptor` remains open while LibVLC is playing and is released on lifecycle cleanup; media never needs a world-readable storage path.
- Fixed LibVLC version: **org.videolan.android:libvlc-all:3.6.1**, available from Maven Central; engine under LGPL v2.1. We must include license notices and comply with source/relinking requirements prior to production redistribution. App uses no source fork of VLC.
- Existing preview ARM64 ABI filter applies to LibVLC native libraries too. Guard against APK bloat; no GitHub Actions artifact storage, no repo-stored binary libraries, no caches.

## M2 — Library experience
- Poster thumbnails asynchronously generated from video files with bounded resources, metadata (duration, size, codec); search, sort, manual grouping Movies / Series / Other, favorites.
- Continue watching cards, resume progress, watched status, rename/display-only names without changing source files.
- Optional delete requires explicit confirmation and Android's scoped-storage user approval. Never destroy user media implicitly.
- Tablet/landscape layout, remote/keyboard navigation.

## M3 — Player parity / travel
- Subtitle track chooser (embedded/external .srt with user-selected URI), audio track selection, speed, picture-in-picture, aspect ratio, resume and audio-output choice.
- Airplane-mode readiness and download-completeness verification; optional watchlist, no dependency on external apps or internet.
- Potential SMB/NAS later, but keep it separate from true offline files. Streaming subscriptions' DRM/expiration are not bypassed.

## Acceptance gates

1. `gradle :app:testDebugUnitTest :app:assembleDebug :app:assemblePreview` succeeds; preview APK has only ARM64 `lib/` entries, valid signature/package, size under the existing 300 MiB guard (revise only based on evidence).
2. Download existing authorized MPEG-TS HLS → MP4 without regression; verify previous issue #8 authenticated direct downloads are tracked separately.
3. Library shows downloaded file immediately after save/return to library; persists across process restart. User-imported file stays accessible across restart, revocation fails safely.
4. Play same Samsung-silent MP4 with sound through integrated LibVLC, video seek, duration, pause/resume; audio and video remain synchronized. Test .ts, .mkv, .webm and an audio-only file using user-authorized samples.
5. Enter airplane mode and play a fully local file; browse tab does not interrupt active libVLC playback or accidentally resume network navigation when returning.
6. Verify app home screen/background life cycle releases decoder and descriptor; no crashes after repeated opens. Full uBO browser acceptance gates on PR #7 still apply.
7. Measure ARM64 APK size vs 209 MB pre-VLC preview and memory use; document codec licenses, notice and relinking requirements before shipping stable.
8. PR stays separate/open until device evidence. Do not auto-merge on green CI.

## Known scope limits / safety
- No media files bundled. No external VLC installation required, but app will grow because native LibVLC libraries ship inside.
- No automatic scanning of every storage folder or private app content.
- No DRM removal, no modification of Netflix/Prime download policies.
- Preview uses disposable debug key, so uninstall only old **StreamCatch Preview** before new preview tests.
