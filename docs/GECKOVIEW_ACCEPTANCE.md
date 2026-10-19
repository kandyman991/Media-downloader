# GeckoView + uBlock Origin acceptance contract (PR #7)

## CI (mandatory)
- [ ] PR compile and unit tests pass with pinned GeckoView from Mozilla Maven.
- [ ] Release signing smoke test succeeds; no `upload-artifact`, Gradle Actions cache or build cache is used.
- [ ] No APK is published by a regular PR.
- [ ] Android manifest remains HTTPS-oriented; unchanged package ID and versionCode on non-release feature PR.

## Real Android device (mandatory before merge/release)
- [ ] Open HTTPS, navigate back, reload, and re-open after Activity/process recreation.
- [ ] Install official **Mozilla-signed** uBlock Origin with consent; verify persistence after app restart.
- [ ] Verify actual ads/trackers blocked, cosmetic page filtering works, site's block toggle works.
- [ ] Open uBO popup, dashboard/filter lists, update and enable/disable; deny optional permission requests and verify safe behavior.
- [ ] Verify video playback including fullscreen and redirects/popups.
- [ ] Detect MP4, WebM, master HLS and DASH; manual scan and deduplication work, no spurious media from previous tabs.
- [ ] Direct MP4/WebM downloads play, HLS quality picker works, MP4 remux has valid audio/video, TS fallback works and cancellation removes temporary files.
- [ ] Cookie/session-authenticated downloads work OR keep the feature behind an opt-in toggle until Gecko-cookie-aware download transport is implemented. Never silently substitute WebView cookies.
- [ ] Test a site that requires temporarily disabling uBO and re-enabling it.
- [ ] Install signed APK as an update without losing data; verify unchanged signing key and increased versionCode on released builds.
- [ ] Confirm release files go to GitHub Releases only and do not consume Actions artifact storage.

## Out of scope / never claim implemented
- DRM bypass, unsupported HLS variants, DASH downloads, cookie transfer across security domains.
- Any full uBO compatibility claim before a real device tests popup, cosmetic filters and webRequest hooks.

## Regression policy
Maintain the old working WebView release until the new browser passes all applicable gates.
