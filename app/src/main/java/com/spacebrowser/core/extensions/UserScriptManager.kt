package com.spacebrowser.core.extensions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class UserScript(
    val id: String,
    val name: String,
    val matches: List<String>,
    val code: String,
    val enabled: Boolean = true,
)

/**
 * A small local content-extension layer for Android WebView.
 *
 * WebView does not expose Chromium's CRX/Manifest V3 extension APIs. SPACE
 * therefore supports auditable Greasemonkey/Tampermonkey-style user scripts,
 * stored only in the app's private files directory and injected only into
 * matching http(s) pages.
 */
class UserScriptManager(context: Context) {
    private val directory = File(context.filesDir, "userscripts").apply { mkdirs() }
    private val _scripts = MutableStateFlow(loadAll())
    val scripts: StateFlow<List<UserScript>> = _scripts

    fun importText(text: String, sourceName: String = "Local script"): Result<Int> = runCatching {
        require(text.toByteArray().size <= MAX_IMPORT_BYTES) { "Extension file is too large" }
        val root = text.trimStart().takeIf { it.startsWith("{") }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val imported = if (root?.optString("format") == BUNDLE_FORMAT) {
            parseBundle(root)
        } else {
            listOf(parseScript(text, sourceName))
        }
        require(imported.isNotEmpty()) { "No user scripts were found" }
        imported.forEach(::persist)
        refresh()
        imported.size
    }

    fun exportBundle(): String {
        val array = JSONArray()
        _scripts.value.forEach { array.put(it.toJson()) }
        return JSONObject()
            .put("format", BUNDLE_FORMAT)
            .put("scripts", array)
            .toString(2)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val script = _scripts.value.firstOrNull { it.id == id } ?: return
        persist(script.copy(enabled = enabled))
        refresh()
    }

    fun remove(id: String) {
        File(directory, "$id.json").delete()
        refresh()
    }

    fun scriptsFor(url: String): List<UserScript> =
        _scripts.value.filter { it.enabled && it.matches.any { pattern -> matches(pattern, url) } }

    private fun parseBundle(root: JSONObject): List<UserScript> {
        require(root.optString("format") == BUNDLE_FORMAT) {
            "This is not a SPACE extension bundle"
        }
        val array = root.optJSONArray("scripts") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index).toScript(regenerateId = true))
            }
        }
    }

    private fun parseScript(text: String, sourceName: String): UserScript {
        require(text.isNotBlank()) { "The script is empty" }
        val metadata = metadataLines(text)
        val name = metadata.firstOrNull { it.first.equals("name", true) }
            ?.second
            ?.take(100)
            ?.takeIf { it.isNotBlank() }
            ?: sourceName.substringBeforeLast('.').take(100).ifBlank { "Local script" }
        val patterns = metadata
            .filter { it.first.equals("match", true) || it.first.equals("include", true) }
            .map { it.second.trim() }
            .filter(::validPattern)
            .distinct()
            .take(32)
            .ifEmpty { listOf("<all_urls>") }
        return UserScript(
            id = UUID.randomUUID().toString(),
            name = name,
            matches = patterns,
            code = text,
        )
    }

    private fun metadataLines(text: String): List<Pair<String, String>> {
        var inside = false
        return buildList {
            text.lineSequence().take(300).forEach { raw ->
                val line = raw.trim()
                if (line.contains("==UserScript==")) {
                    inside = true
                    return@forEach
                }
                if (line.contains("==/UserScript==")) {
                    inside = false
                    return@forEach
                }
                if (inside) {
                    Regex("""^//\s*@([A-Za-z][\w-]*)\s+(.+)$""")
                        .matchEntire(line)
                        ?.let { add(it.groupValues[1] to it.groupValues[2].trim()) }
                }
            }
        }
    }

    private fun persist(script: UserScript) {
        File(directory, "${script.id}.json").writeText(script.toJson().toString())
    }

    private fun refresh() {
        _scripts.value = loadAll()
    }

    private fun loadAll(): List<UserScript> = directory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.extension.equals("json", true) }
        .mapNotNull { file ->
            runCatching { JSONObject(file.readText()).toScript() }.getOrNull()
        }
        .sortedBy { it.name.lowercase() }
        .toList()

    private fun UserScript.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("enabled", enabled)
        .put("matches", JSONArray(matches))
        .put("code", code)

    private fun JSONObject.toScript(regenerateId: Boolean = false): UserScript {
        val matchArray = optJSONArray("matches") ?: JSONArray()
        val patterns = buildList {
            for (index in 0 until matchArray.length()) {
                matchArray.optString(index).takeIf(::validPattern)?.let(::add)
            }
        }.distinct().take(32).ifEmpty { listOf("<all_urls>") }
        val code = getString("code")
        require(code.toByteArray().size <= MAX_IMPORT_BYTES) { "Extension file is too large" }
        return UserScript(
            id = if (regenerateId) UUID.randomUUID().toString() else {
                optString("id").takeIf { it.matches(ID_PATTERN) } ?: UUID.randomUUID().toString()
            },
            name = optString("name", "Local script").take(100),
            matches = patterns,
            code = code,
            enabled = optBoolean("enabled", true),
        )
    }

    companion object {
        const val BUNDLE_FORMAT = "space-userscripts-v1"
        private const val MAX_IMPORT_BYTES = 1_000_000
        private val ID_PATTERN = Regex("^[A-Za-z0-9-]{1,80}$")
        private val MATCH_PATTERN = Regex(
            """^(\*|https?)://([^/\s]+)(/.*)$""",
            RegexOption.IGNORE_CASE,
        )

        internal fun validPattern(pattern: String): Boolean {
            if (pattern == "<all_urls>") return true
            val match = MATCH_PATTERN.matchEntire(pattern) ?: return false
            val host = match.groupValues[2]
            return host == "*" ||
                host.removePrefix("*.").matches(
                    Regex("""^[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$"""),
                )
        }

        internal fun matches(pattern: String, url: String): Boolean {
            if (!url.startsWith("http://") && !url.startsWith("https://")) return false
            if (pattern == "<all_urls>") return true
            val match = MATCH_PATTERN.matchEntire(pattern) ?: return false
            if (!validPattern(pattern)) return false
            val scheme = if (match.groupValues[1] == "*") "https?" else {
                Regex.escape(match.groupValues[1].lowercase())
            }
            val hostPattern = match.groupValues[2]
            val host = when {
                hostPattern == "*" -> """[^/:]+"""
                hostPattern.startsWith("*.") -> {
                    val base = Regex.escape(hostPattern.removePrefix("*."))
                    """(?:[^./:]+\.)?$base"""
                }
                else -> Regex.escape(hostPattern)
            }
            val path = wildcardRegex(match.groupValues[3])
            val regex = buildString {
                append("^").append(scheme).append("://")
                append(host).append(path).append('$')
            }
            return runCatching { Regex(regex, RegexOption.IGNORE_CASE).matches(url) }
                .getOrDefault(false)
        }

        private fun wildcardRegex(value: String): String = buildString {
            value.split('*').forEachIndexed { index, piece ->
                if (index > 0) append(".*")
                append(Regex.escape(piece))
            }
        }
    }
}
