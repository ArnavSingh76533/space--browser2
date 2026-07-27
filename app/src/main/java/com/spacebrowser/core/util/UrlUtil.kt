package com.spacebrowser.core.util

import java.net.URLEncoder

/**
 * Pure-Kotlin URL heuristics (no Android imports so the logic is unit-testable).
 */
object UrlUtil {

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$")
    private val HOSTLIKE = Regex(
        "^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+(:\\d+)?(/.*)?$"
    )

    /** True if the input should be loaded as a URL rather than searched. */
    fun isLikelyUrl(raw: String): Boolean {
        val input = raw.trim()
        if (input.isEmpty() || input.any { it.isWhitespace() }) return false
        if (SCHEME.containsMatchIn(input)) return true
        if (input.startsWith("localhost")) return true
        if (IPV4.matches(input)) return true
        return HOSTLIKE.matches(input)
    }

    /**
     * Turns address-bar input into something loadable: a normalized URL, or a
     * search URL built from [searchTemplate] (which contains `%s`).
     */
    fun toLoadable(raw: String, searchTemplate: String): String {
        val input = raw.trim()
        return if (isLikelyUrl(input)) {
            if (SCHEME.containsMatchIn(input)) input else "https://$input"
        } else {
            searchTemplate.replace("%s", URLEncoder.encode(input, "UTF-8"))
        }
    }

    /** Host without a leading `www.`, for compact display. */
    fun prettyHost(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val afterScheme = url.substringAfter("://", url)
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.removePrefix("www.")
    }

    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return null
        val hostPort = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = hostPort.substringBefore(':')
        return host.lowercase().ifBlank { null }
    }
}
