# Engineering Decisions — StreamCatch / Media Downloader

These are operating constraints and intended architecture, **not claims every future milestone is implemented**. Update when decisions are superseded; include a date and issue/PR/device evidence.

## D-001 — Hybrid detection

Use Android WebView/network resource observation to find browser media; do not assume every detected source is downloadable.

Source: `README.md` / project handoff; verify against actual code and test results.

## D-002 — HLS first limits

Support known validated unencrypted VOD forms; do not represent live, DRM or unsupported HLS/DASH formats as implemented.

Source: `README.md` / project handoff; verify against actual code and test results.

## D-003 — Lossless remux

Use the platform MediaExtractor/MediaMuxer where supported; verify MP4 output and fall back to a valid TS file instead of producing corruption.

Source: `README.md` / project handoff; verify against actual code and test results.

## D-004 — Persistent signing

Keep a stable release keystore in repository secrets, increment versionCode for app upgrades, and never expose signing secrets in public Git.

Source: `README.md` / project handoff; verify against actual code and test results.

## D-005 — Zero Actions artifact storage

Publish opt-in signed APKs through GitHub Releases, never upload-artifact; normal CI does not need APK publication.

Source: `README.md` / project handoff; verify against actual code and test results.

## D-900 — ChatGPT project continuity (2026-10-10)

Use curated `CHAT_HANDOFF.md` plus explicit decision/failed-approach records. Check GitHub live before action. No automatic conversation ingestion.

## D-006 — Real Firefox extension support
Use Mozilla GeckoView to run the authentic, Mozilla-signed uBlock Origin with its own popup, dashboard and filters. Keep StreamCatch's privileged built-in media-observation extension independent of uBO. Browser-engine changes must preserve existing download behavior or document specific parity gaps and block release. User approval is required for extension installation/permissions; never fork/bundle uBO into the APK. Source: PR #7 architecture review, pending device verification.

## D-007 — Independent offline media center (2026-10-19, stacked draft PR #11)
- Keep three separable subsystems: GeckoView + authentic uBO for browsing; existing HTTP/HLS services for authorized downloads; **VideoLAN LibVLC 3.6.1** for offline local playback (not an external VLC app intent).
- Media library reads only files owned/visible to the app under `Downloads/StreamCatch`, plus persisted SAF document URIs explicitly selected by the user. No broad storage grants or automatic scanning of private media.
- Play SAF and MediaStore `content://` URI via open file descriptor held for decoder lifetime. Never forward browser cookies, video URLs or DRM credentials to LibVLC's offline player.
- Use **stacked PR #11** targeting open GeckoView PR #7 to protect stable `main`; release only ARM64 experimental APKs via GitHub Releases, no Actions artifact storage.
- Pin LibVLC official Maven Android library, with LGPL-2.1 notices and applicable source/relinking obligations before stable redistribution.
- Validate package ABI, signature and size (<300 MiB) plus real-device A/V and offline behavior. CI success does not establish playback acceptance.
