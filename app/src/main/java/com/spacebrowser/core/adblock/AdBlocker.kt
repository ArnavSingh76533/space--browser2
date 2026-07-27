package com.spacebrowser.core.adblock

import android.content.Context
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Fast suffix matcher retained for hosts-style lists. An entry blocks both the
 * exact hostname and its subdomains without substring false positives.
 */
class HostMatcher(rules: Collection<String>) {

    private val exact = HashSet<String>(rules.size * 2)

    init {
        for (raw in rules) {
            val rule = raw.trim().lowercase(Locale.US)
            if (rule.isEmpty() || rule.startsWith("#") || rule.startsWith("!")) continue
            exact += rule.removePrefix("*.").removePrefix(".").removeSuffix(".")
        }
    }

    val size: Int get() = exact.size

    fun matches(host: String?): Boolean {
        var current = normalizeHost(host) ?: return false
        while (true) {
            if (current in exact) return true
            val dot = current.indexOf('.')
            if (dot < 0) return false
            current = current.substring(dot + 1)
        }
    }
}

enum class AdResourceType {
    DOCUMENT,
    SUBDOCUMENT,
    SCRIPT,
    IMAGE,
    STYLESHEET,
    XHR,
    MEDIA,
    FONT,
    OTHER,
}

internal enum class FilterDecision { NONE, BLOCK, ALLOW, IMPORTANT_BLOCK }

private data class RuleOptions(
    val thirdParty: Boolean? = null,
    val includedTypes: Set<AdResourceType> = emptySet(),
    val excludedTypes: Set<AdResourceType> = emptySet(),
    val includedDomains: Set<String> = emptySet(),
    val excludedDomains: Set<String> = emptySet(),
    val important: Boolean = false,
) {
    fun matches(type: AdResourceType, pageHost: String?, thirdPartyRequest: Boolean): Boolean {
        if (thirdParty != null && thirdParty != thirdPartyRequest) return false
        if (includedTypes.isNotEmpty() && type !in includedTypes) return false
        if (type in excludedTypes) return false
        if (includedDomains.isNotEmpty() &&
            includedDomains.none { domainMatches(pageHost, it) }
        ) {
            return false
        }
        if (excludedDomains.any { domainMatches(pageHost, it) }) return false
        return true
    }
}

private data class NetworkRule(
    val regex: Regex,
    val exception: Boolean,
    val options: RuleOptions,
) {
    fun matches(
        requestUrl: String,
        pageHost: String?,
        type: AdResourceType,
        thirdParty: Boolean,
    ): Boolean = options.matches(type, pageHost, thirdParty) && regex.containsMatchIn(requestUrl)
}

private data class CosmeticRule(
    val selector: String,
    val includedDomains: Set<String>,
    val excludedDomains: Set<String>,
    val exception: Boolean,
) {
    fun applies(host: String?): Boolean {
        if (includedDomains.isNotEmpty() &&
            includedDomains.none { domainMatches(host, it) }
        ) {
            return false
        }
        return excludedDomains.none { domainMatches(host, it) }
    }
}

internal data class CosmeticMatch(
    val hide: Set<String>,
    val exceptions: Set<String>,
)

/**
 * A compact Kotlin adaptation of adblock-rust's filter-set/engine split. It
 * supports hosts files plus the high-value ABP/uBlock network syntax used by
 * EasyList-style lists: exceptions, || domain anchors, wildcards, separators,
 * resource types, third/first-party constraints, domain constraints and
 * important rules. Safe CSS cosmetic selectors are also collected.
 */
internal class FilterEngine(lines: Collection<String>) {

    private val hostMatcher: HostMatcher
    private val blockingRules: List<NetworkRule>
    private val exceptionRules: List<NetworkRule>
    private val cosmeticRules: List<CosmeticRule>

    init {
        val hosts = ArrayList<String>()
        val blocking = ArrayList<NetworkRule>()
        val exceptions = ArrayList<NetworkRule>()
        val cosmetics = ArrayList<CosmeticRule>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[Adblock")) continue
            val cosmetic = parseCosmetic(line)
            if (cosmetic != null) {
                cosmetics += cosmetic
                continue
            }
            val host = parseHost(line)
            if (host != null) {
                hosts += host
                continue
            }
            parseNetwork(line)?.let {
                if (it.exception) exceptions += it else blocking += it
            }
        }

        hostMatcher = HostMatcher(hosts)
        blockingRules = blocking
        exceptionRules = exceptions
        cosmeticRules = cosmetics
    }

    val size: Int
        get() = hostMatcher.size + blockingRules.size + exceptionRules.size + cosmeticRules.size

    fun decide(
        requestUrl: String,
        pageUrl: String?,
        type: AdResourceType,
    ): FilterDecision {
        val requestHost = hostOf(requestUrl)
        val pageHost = hostOf(pageUrl)
        val thirdParty = isThirdParty(requestHost, pageHost)
        val hostBlocked = hostMatcher.matches(requestHost)
        val matchingBlocks = blockingRules.filter {
            it.matches(requestUrl, pageHost, type, thirdParty)
        }

        if (matchingBlocks.any { it.options.important }) {
            return FilterDecision.IMPORTANT_BLOCK
        }
        if (exceptionRules.any { it.matches(requestUrl, pageHost, type, thirdParty) }) {
            return FilterDecision.ALLOW
        }
        return if (hostBlocked || matchingBlocks.isNotEmpty()) {
            FilterDecision.BLOCK
        } else {
            FilterDecision.NONE
        }
    }

    fun cosmeticFor(pageHost: String?): CosmeticMatch {
        val applicable = cosmeticRules.filter { it.applies(pageHost) }
        return CosmeticMatch(
            hide = applicable.filterNot { it.exception }.mapTo(linkedSetOf()) { it.selector },
            exceptions = applicable.filter { it.exception }.mapTo(linkedSetOf()) { it.selector },
        )
    }

    private fun parseHost(line: String): String? {
        val pieces = line.split(Regex("\\s+"))
        val candidate = when {
            pieces.size >= 2 && pieces.first() in HOST_REDIRECTS -> pieces[1]
            pieces.size == 1 -> pieces[0]
            else -> return null
        }.trim().removeSuffix(".")

        if (candidate == "localhost" || candidate.contains('/') || candidate.contains('*') ||
            candidate.contains('^') || candidate.contains('|') || candidate.contains('$') ||
            candidate.contains('#') || !HOST_PATTERN.matches(candidate)
        ) {
            return null
        }
        return candidate
    }

    private fun parseNetwork(line: String): NetworkRule? {
        var body = line
        val exception = body.startsWith("@@")
        if (exception) body = body.removePrefix("@@")
        if (body.isBlank() || body.startsWith("#")) return null

        val optionIndex = body.lastIndexOf('$')
        val pattern = if (optionIndex > 0) body.substring(0, optionIndex) else body
        val optionsText = if (optionIndex > 0) body.substring(optionIndex + 1) else ""
        if (pattern.isBlank() || pattern.contains("##") || pattern.contains("#@#")) return null
        val regex = compilePattern(pattern) ?: return null
        return NetworkRule(regex, exception, parseOptions(optionsText))
    }

    private fun parseOptions(text: String): RuleOptions {
        var thirdParty: Boolean? = null
        var important = false
        val includedTypes = linkedSetOf<AdResourceType>()
        val excludedTypes = linkedSetOf<AdResourceType>()
        val includedDomains = linkedSetOf<String>()
        val excludedDomains = linkedSetOf<String>()

        for (raw in text.split(',')) {
            val token = raw.trim().lowercase(Locale.US)
            if (token.isBlank()) continue
            when (token) {
                "third-party", "3p" -> thirdParty = true
                "~third-party", "first-party", "1p" -> thirdParty = false
                "important" -> important = true
                else -> {
                    if (token.startsWith("domain=")) {
                        token.removePrefix("domain=").split('|').forEach { domain ->
                            if (domain.startsWith("~")) {
                                normalizeHost(domain.removePrefix("~"))?.let(excludedDomains::add)
                            } else {
                                normalizeHost(domain)?.let(includedDomains::add)
                            }
                        }
                    } else {
                        val excluded = token.startsWith("~")
                        resourceType(token.removePrefix("~"))?.let {
                            if (excluded) excludedTypes += it else includedTypes += it
                        }
                    }
                }
            }
        }
        return RuleOptions(
            thirdParty = thirdParty,
            includedTypes = includedTypes,
            excludedTypes = excludedTypes,
            includedDomains = includedDomains,
            excludedDomains = excludedDomains,
            important = important,
        )
    }

    private fun parseCosmetic(line: String): CosmeticRule? {
        val exceptionIndex = line.indexOf("#@#")
        val hideIndex = line.indexOf("##")
        val exception = exceptionIndex >= 0
        val index = if (exception) exceptionIndex else hideIndex
        if (index < 0) return null
        val delimiterSize = 3.takeIf { exception } ?: 2
        val selector = line.substring(index + delimiterSize).trim()
        if (selector.isBlank() || selector.length > 500 || selector.contains("+js(") ||
            selector.contains('{') || selector.contains('}')
        ) {
            return null
        }
        val included = linkedSetOf<String>()
        val excluded = linkedSetOf<String>()
        line.substring(0, index).split(',').forEach { rawDomain ->
            val domain = rawDomain.trim()
            if (domain.startsWith("~")) {
                normalizeHost(domain.removePrefix("~"))?.let(excluded::add)
            } else {
                normalizeHost(domain)?.let(included::add)
            }
        }
        return CosmeticRule(selector, included, excluded, exception)
    }

    private fun compilePattern(pattern: String): Regex? {
        if (pattern.length > 2 && pattern.startsWith('/') && pattern.endsWith('/')) {
            return runCatching { Regex(pattern.substring(1, pattern.lastIndex)) }.getOrNull()
        }

        var source = pattern
        val domainAnchor = source.startsWith("||")
        val leftAnchor = !domainAnchor && source.startsWith("|")
        if (domainAnchor) source = source.drop(2) else if (leftAnchor) source = source.drop(1)
        val rightAnchor = source.endsWith("|")
        if (rightAnchor) source = source.dropLast(1)

        val regex = StringBuilder()
        when {
            domainAnchor -> regex.append("^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?")
            leftAnchor -> regex.append('^')
        }
        for (character in source) {
            when (character) {
                '*' -> regex.append(".*")
                '^' -> regex.append("(?:[^A-Za-z0-9_.%-]|$)")
                else -> regex.append(Regex.escape(character.toString()))
            }
        }
        if (rightAnchor) regex.append('$')
        return runCatching { Regex(regex.toString(), RegexOption.IGNORE_CASE) }.getOrNull()
    }

    private companion object {
        val HOST_REDIRECTS = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
        val HOST_PATTERN = Regex("(?i)^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$")

        fun resourceType(value: String): AdResourceType? = when (value) {
            "document" -> AdResourceType.DOCUMENT
            "subdocument", "sub_frame" -> AdResourceType.SUBDOCUMENT
            "script" -> AdResourceType.SCRIPT
            "image" -> AdResourceType.IMAGE
            "stylesheet", "css" -> AdResourceType.STYLESHEET
            "xmlhttprequest", "xhr", "fetch" -> AdResourceType.XHR
            "media", "object", "object-subrequest" -> AdResourceType.MEDIA
            "font" -> AdResourceType.FONT
            "other", "ping", "beacon" -> AdResourceType.OTHER
            else -> null
        }
    }
}

class AdBlocker(context: Context) {

    @Volatile private var bundled = FilterEngine(loadBundled(context))
    @Volatile private var custom = FilterEngine(emptyList())
    @Volatile private var allowedSites: Set<String> = emptySet()

    val sessionBlocked = AtomicLong(0)

    val ruleCount: Int get() = bundled.size + custom.size

    fun updateUserRules(rules: Set<String>, allowlist: Set<String>) {
        custom = FilterEngine(rules)
        allowedSites = allowlist.mapNotNull(::normalizeHost).toSet()
    }

    fun isSiteAllowlisted(pageHost: String?): Boolean {
        val host = normalizeHost(pageHost) ?: return false
        return allowedSites.any { domainMatches(host, it) }
    }

    fun shouldBlock(
        requestUrl: String,
        pageUrl: String?,
        type: AdResourceType = AdResourceType.OTHER,
    ): Boolean {
        if (isSiteAllowlisted(hostOf(pageUrl))) return false
        val decisions = listOf(
            bundled.decide(requestUrl, pageUrl, type),
            custom.decide(requestUrl, pageUrl, type),
        )
        val blocked = when {
            FilterDecision.IMPORTANT_BLOCK in decisions -> true
            FilterDecision.ALLOW in decisions -> false
            FilterDecision.BLOCK in decisions -> true
            else -> false
        }
        if (blocked) sessionBlocked.incrementAndGet()
        return blocked
    }

    fun cosmeticCss(pageUrl: String?): String {
        val host = hostOf(pageUrl)
        if (isSiteAllowlisted(host)) return ""
        val bundledMatch = bundled.cosmeticFor(host)
        val customMatch = custom.cosmeticFor(host)
        val exceptions = bundledMatch.exceptions + customMatch.exceptions
        return (bundledMatch.hide + customMatch.hide)
            .asSequence()
            .filterNot { it in exceptions }
            .take(500)
            .joinToString("\n") { "$it{display:none!important;}" }
    }

    private fun loadBundled(context: Context): List<String> = buildList {
        for (asset in listOf(
            "adblock/hosts.txt",
            "adblock/filters.txt",
            "adblock/cosmetic.txt",
        )) {
            runCatching {
                context.assets.open(asset).bufferedReader().useLines { addAll(it.toList()) }
            }
        }
    }

    companion object {
        fun emptyStream() = ByteArrayInputStream(ByteArray(0))
    }
}

internal fun hostOf(url: String?): String? {
    if (url == null) return null
    return try {
        normalizeHost(URI(url).host)
    } catch (_: Exception) {
        null
    }
}

private fun normalizeHost(host: String?): String? = host
    ?.trim()
    ?.lowercase(Locale.US)
    ?.removePrefix("www.")
    ?.removePrefix(".")
    ?.removeSuffix(".")
    ?.takeIf { it.isNotBlank() }

private fun domainMatches(host: String?, domain: String): Boolean {
    val normalized = normalizeHost(host) ?: return false
    val rule = normalizeHost(domain) ?: return false
    return normalized == rule || normalized.endsWith(".$rule")
}

private fun isThirdParty(requestHost: String?, pageHost: String?): Boolean {
    val requestSite = registrableDomain(requestHost) ?: return true
    val pageSite = registrableDomain(pageHost) ?: return true
    return requestSite != pageSite
}

private fun registrableDomain(host: String?): String? {
    val normalized = normalizeHost(host) ?: return null
    val parts = normalized.split('.')
    if (parts.size <= 2) return normalized
    val lastTwo = parts.takeLast(2).joinToString(".")
    val ccSecondLevel = parts[parts.lastIndex - 1] in setOf(
        "co", "com", "net", "org", "gov", "edu", "ac",
    ) && parts.last().length == 2
    return if (ccSecondLevel && parts.size >= 3) {
        parts.takeLast(3).joinToString(".")
    } else {
        lastTwo
    }
}
