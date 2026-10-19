package dev.streamcatch.android.core

/** Pure, deterministic library policy so Android, CLI tests and future AI commands share rules. */
object OfflineMediaPolicy {
    private val video = setOf("mp4", "m4v", "webm", "mkv", "ts", "mov")
    private val audio = setOf("mp3", "m4a", "flac", "ogg")

    fun extension(filename: String): String =
        filename.substringAfterLast('.', "").lowercase()

    fun supports(filename: String): Boolean =
        extension(filename) in video || extension(filename) in audio

    fun category(filename: String): String = when (extension(filename)) {
        in video -> "video"
        in audio -> "audio"
        else -> "unsupported"
    }
}
