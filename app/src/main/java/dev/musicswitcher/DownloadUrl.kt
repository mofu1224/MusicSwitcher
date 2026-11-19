package dev.musicswitcher

import java.net.URI

internal fun parseDownloadUrl(input: String): String? {
    val value = input.trim()
    if (value.isEmpty() || value.length > 4_096) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null || uri.port !in listOf(-1, 443)) return null
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
    if ('.' !in host || host.substringAfterLast('.').none { it in 'a'..'z' }) return null
    if (host.startsWith('[') || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost")) return null
    return value
}
