# Media-downloader — StreamCatch Android

Android-first video detector/downloader prototype. **Android 10+**, Kotlin, Android WebView.

### MVP features
- Open HTTPS webpages and detect media using WebView request observation, HTML video elements, and resource timings.
- Recognize MP4, WebM, HLS (`.m3u8`), and DASH (`.mpd`) sources.
- Download direct MP4/WebM videos through Android DownloadManager into `Downloads/StreamCatch/`.
- Accept webpage links from Firefox or other Android browsers via Share → StreamCatch.

### HLS M2 — first Android on-device downloads

- Tap a detected HLS video to load its playlist; choose video quality for master playlists.
- **Supported now:** completed, unencrypted MPEG-TS HLS VOD playlists with video/audio in the same rendition. Segments download sequentially and are concatenated to an MPEG-TS video (`.ts`) under `Downloads/StreamCatch/`.
- The Android foreground service continues when you leave the screen; it shows progress and supports cancellation through its notification (allow notifications when prompted).
- **Not yet supported:** live streams, AES-128 / DRM encryption, separate audio renditions, fragmented MP4 (`#EXT-X-MAP` / `.m4s`), HLS byte ranges or discontinuities, DASH downloads, exporting MPEG-TS to MP4. These fail clearly instead of saving corrupt files. `.ts` playback may require a compatible video player, such as VLC.
- Media URLs with expired tokens and some cookies/authenticated players can still fail with HTTP 403 or an expired playlist. DRM bypass is outside scope. Do not download content without permission.

### Build
Requires JDK 17, Android SDK platform/build-tools 35 and Gradle 8.11.1 (or Android Studio). Run `gradle :app:testDebugUnitTest :app:assembleDebug`. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

### CI / zero Actions artifact uploads
- Builds/tests on main, PRs and manual dispatch. These runs deliberately do not publish APK files.
- No `actions/upload-artifact`, `actions/cache`, or Gradle Actions caches (`cache-disabled: true`).
- To publish a test APK without manually creating a tag, merge a change to `main` using a commit message containing `[apk]`. Only that push runs the APK publishing job. It builds/tests once and creates a `snapshot-<commit>` prerelease with the APK **directly in GitHub Releases**.
- Version tags (e.g. `v0.1.0`) also build and upload a debug APK directly to GitHub Releases. Releases are separate from Actions artifacts.
- Workflows still create GitHub Actions **logs**; set their retention to the minimum in repository Actions settings if needed.
- Test APKs use ephemeral signing on hosted runners; for new versions, the previous debug build might have to be uninstalled. Stable signing is planned.

### Next milestones
M2: first MPEG-TS HLS VOD downloads (in progress, see support limits). M3: fragmented MP4 HLS / DASH. M4: progress/resume. M5: optional Firefox for Android companion. M6: stable signing and publication.

See `docs/ARCHITECTURE.md`. Only download content you have permission to save.
