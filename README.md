# Media-downloader — StreamCatch Android

Android-first video detector/downloader prototype. **Android 10+**, Kotlin, Android WebView.

### MVP features
- Open HTTPS webpages and detect media using WebView request observation, HTML video elements, and resource timings.
- Recognize MP4, WebM, HLS (`.m3u8`), and DASH (`.mpd`) sources.
- Download direct MP4/WebM videos through Android DownloadManager into `Downloads/StreamCatch/`.
- Accept webpage links from Firefox or other Android browsers via Share → StreamCatch.

### HLS M2 — first Android on-device downloads

- Tap a detected HLS video to load its playlist; choose video quality for master playlists.
- **Supported now:** completed, unencrypted MPEG-TS HLS VOD playlists with video/audio in the same rendition. Segments download sequentially to a private temporary file.
- **M3.1 — MP4 preferred:** Android's MediaExtractor/MediaMuxer now remuxes supported H.264/H.265 + optional AAC encoded samples into a real `.mp4` without re-encoding. It verifies the result and saves it under `Downloads/StreamCatch/`. Playback quality is unchanged, and no cloud/transcoding service is used.
- **Safety fallback:** If the device's media extractor or codec combination cannot produce a verified MP4, the previously working `.ts` file is saved instead. Failed/pending MP4s and temporary input files are removed, including when cancelled. Both formats are labeled correctly.
- **Storage note:** Conversion briefly needs free space for the complete temporary TS plus the MP4 (and any TS fallback); ensure sufficient internal storage before large downloads.
- The Android foreground service continues when you leave the screen; it shows progress and supports cancellation through its notification (allow notifications when prompted).
- **Not yet supported:** live streams, AES-128 / DRM encryption, separate audio renditions, fragmented MP4 (`#EXT-X-MAP` / `.m4s`), HLS byte ranges or discontinuities, DASH downloads, MP4 remux for codecs not supported by the Android framework. These fail clearly instead of saving corrupt files. `.ts` playback may require a compatible video player, such as VLC.
- Media URLs with expired tokens and some cookies/authenticated players can still fail with HTTP 403 or an expired playlist. DRM bypass is outside scope. Do not download content without permission.

### Build
Requires JDK 17, Android SDK platform/build-tools 35 and Gradle 8.11.1 (or Android Studio). Run `gradle :app:testDebugUnitTest :app:assembleDebug`. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

### Persistent signed APKs: in-place Android updates

**Why v0.3.1 couldn't update:** Previous GitHub-hosted runs generated different ephemeral debug-signing certificates. Android requires the SAME application ID, signing certificate and a non-decreasing `versionCode` to install an update in place.

**One-time local provisioning (Ubuntu / trusted computer):**

1. Install Java keytool and GitHub CLI: `sudo apt install openjdk-21-jdk gh`.
2. Authenticate: `gh auth login`.
3. Run `bash tools/configure-release-signing.sh` from this repository. The script securely creates `~/.local/share/streamcatch/signing/streamcatch-release.p12` (reuses if present), uploads the key, alias and password as **GitHub repository Actions secrets**, then triggers one signed release build.
4. Back up the entire keystore and its password privately (offline and password manager). **Never commit them to this public repository.**
5. Download the newest **signed** APK from [GitHub Releases](https://github.com/kandyman991/Media-downloader/releases). **Uninstall the old debug-signed build one last time:** its lost/ephemeral signing key cannot be used to update it. Future signed APK releases made with this preserved key can update in place without uninstalling.

If `gh auth login` asks for a browser, authenticate as the owner of this GitHub repository.

To publish a new signed test APK after code is ready, use the GitHub Actions UI (**Android CI → Run workflow → publish_signed_apk: true** on the `main` branch), or merge a tested commit whose squash title includes `[apk]`.

**Critical:** Version codes must increase with each genuine update. The first stable-signed version is `0.3.2` (code `4`); future versions must increment the integer in `app/build.gradle.kts`. Back up the keystore indefinitely. The app's `applicationId` must remain `dev.streamcatch.android`.

### CI / zero Actions artifact uploads
- Main/PR builds only compile and test; a normal push never uploads APKs.
- **No** `actions/upload-artifact`, `actions/cache` or Gradle Actions caches (`cache-disabled: true`).
- On an opt-in `[apk]` push, `v*` tag, or explicit `main` manual dispatch with `publish_signed_apk=true`, the workflow uses the same private signing key from GitHub Actions secrets. Missing secrets cause an explicit failure, never a randomly debug-signed release.
- Signed APKs are uploaded **directly to GitHub Releases**, NOT Actions artifacts. `apksigner verify` checks the APK before publication, alongside its SHA-256 checksum.
- PRs never receive the release-signing secrets.
- Normal workflow **logs** are still retained by GitHub. Adjust their retention in repository Actions settings if desired.
- Source builds remain possible without private secrets; only publishing signed APKs requires them.

### Next milestones
M2: validated MPEG-TS HLS VOD downloads. M3.1: MP4 remux with TS fallback. M3.2: fragmented MP4 HLS. M3.3: separate audio/video; DASH follows. M4: progress/resume. M5: optional Firefox for Android companion. M6: stable signing and publication.

See `docs/ARCHITECTURE.md`. Only download content you have permission to save.
