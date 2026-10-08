# Media-downloader — StreamCatch Android

Android-first video detector/downloader prototype. **Android 10+**, Kotlin, Android WebView.

### MVP features
- Open HTTPS webpages and detect media using WebView request observation, HTML video elements, and resource timings.
- Recognize MP4, WebM, HLS (`.m3u8`), and DASH (`.mpd`) sources.
- Download direct MP4/WebM videos through Android DownloadManager into `Downloads/StreamCatch/`.
- Accept webpage links from Firefox or other Android browsers via Share → StreamCatch.

**HLS/DASH downloading is not yet implemented.** Video detection can miss MSE/blob streams or protected and authenticated players. DRM bypass is not supported.

### Build
Requires JDK 17, Android SDK platform/build-tools 35 and Gradle 8.11.1 (or Android Studio). Run `gradle :app:testDebugUnitTest :app:assembleDebug`. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

### CI / zero Actions artifact uploads
- Builds/tests on main, PRs and manual dispatch. These runs deliberately do not publish APK files.
- No `actions/upload-artifact`, `actions/cache`, or Gradle Actions caches (`cache-disabled: true`).
- Push a version tag (e.g. `v0.1.0`) to build and upload a debug APK **directly to GitHub Releases**. Releases are separate from Actions artifacts.
- Workflows still create GitHub Actions **logs**; set their retention to the minimum in repository Actions settings if needed.
- Test APKs use ephemeral signing on hosted runners; for new versions, the previous debug build might have to be uninstalled. Stable signing is planned.

### Next milestones
M2: HLS parsing/quality selection/background downloader. M3: DASH. M4: progress/resume. M5: optional Firefox for Android companion. M6: stable signing and publication.

See `docs/ARCHITECTURE.md`. Only download content you have permission to save.
