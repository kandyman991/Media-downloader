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
