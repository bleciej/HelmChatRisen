package dev.communicator.helmchatrisen.jitsi

internal const val HTTP_SCHEME = "http://"
internal const val HTTPS_SCHEME = "https://"

/**
 * Canonical form of a rider-entered or persisted Jitsi server URL: surrounding whitespace and
 * trailing slashes removed, scheme defaulted to `https://` when missing. A typed `http://` scheme
 * is preserved (with its case normalized), so `http://` URLs stay insecure here; callers that
 * must not join over plain HTTP upgrade known presets themselves (see
 * [JitsiCallManager.sanitizeServerUrl]).
 *
 * Scheme-only input such as `https://` has no host, so it also returns `null` rather than being
 * turned into `https://https:`.
 *
 * @return the normalized URL, or `null` when [url] is blank or contains only a scheme.
 */
internal fun normalizeServerUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    val schemeLength = when {
        trimmed.startsWith(HTTPS_SCHEME, ignoreCase = true) -> HTTPS_SCHEME.length
        trimmed.startsWith(HTTP_SCHEME, ignoreCase = true) -> HTTP_SCHEME.length
        else -> 0
    }
    val scheme = if (schemeLength == 0) {
        HTTPS_SCHEME
    } else {
        trimmed.substring(0, schemeLength).lowercase()
    }
    val host = trimmed.substring(schemeLength).trimEnd('/')
    if (host.isBlank()) return null
    return scheme + host
}
