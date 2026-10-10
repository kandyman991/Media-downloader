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
