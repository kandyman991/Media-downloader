import dev.streamcatch.android.core.MediaUrlDetector
import dev.streamcatch.android.core.MediaKind

fun main() {
    check(MediaUrlDetector.classify("https://a.example/video.mp4?auth=x") == MediaKind.MP4)
    check(MediaUrlDetector.classify("https://a.example/a.M3U8") == MediaKind.HLS)
    check(MediaUrlDetector.classify("https://a.example/manifest", "application/dash+xml") == MediaKind.DASH)
    check(MediaUrlDetector.classify("https://a.example/a.webm") == MediaKind.WEBM)
    check(MediaUrlDetector.classify("https://a.example/a.ts") == null)
    check(MediaUrlDetector.classify("http://a.example/a.mp4") == null)
    check(MediaUrlDetector.classify("file:///tmp/a.mp4") == null)
    check(MediaUrlDetector.classify("https://a.example/foo.png") == null)
    val safe = MediaUrlDetector.safeFilename("https://a.example/%2e%2e/very_bad.mp4", MediaKind.MP4, 12)
    check(!safe.contains('/'))
    check(safe.endsWith(".mp4"))
    println("Core classifier tests: 10 passed")
}
