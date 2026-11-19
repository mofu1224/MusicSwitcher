package dev.musicswitcher

private val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "amr", "mka", "wma", "alac", "m4b", "aiff")

internal fun isAudioFile(name: String, mime: String): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    return extension !in setOf("partial", "part", "tmp") &&
        (mime.startsWith("audio/") || extension in audioExtensions)
}

internal fun uniqueAudioName(original: String, occupiedLowercaseNames: Set<String>): String {
    if (original.lowercase() !in occupiedLowercaseNames) return original
    val stem = original.substringBeforeLast('.')
    val suffix = original.removePrefix(stem)
    return generateSequence(2) { it + 1 }.map { "$stem ($it)$suffix" }
        .first { it.lowercase() !in occupiedLowercaseNames }
}
