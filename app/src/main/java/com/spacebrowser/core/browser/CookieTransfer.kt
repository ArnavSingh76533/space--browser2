package com.spacebrowser.core.browser

import org.json.JSONArray
import org.json.JSONObject

data class TransferCookie(
    val name: String,
    val value: String,
    val path: String = "/",
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
) {
    fun setCookieHeader(https: Boolean): String = buildString {
        append(name).append('=').append(value)
        append("; Path=").append(path.takeIf { it.startsWith('/') } ?: "/")
        if (secure || https) append("; Secure")
        if (httpOnly) append("; HttpOnly")
        append("; SameSite=Lax")
    }
}

object CookieTransfer {
    const val FORMAT = "space-site-cookies-v1"

    fun export(origin: String, cookieHeader: String): String {
        val cookies = parseHeader(cookieHeader)
        val array = JSONArray()
        cookies.forEach { cookie ->
            array.put(
                JSONObject()
                    .put("name", cookie.name)
                    .put("value", cookie.value)
                    .put("path", cookie.path)
                    .put("secure", origin.startsWith("https://")),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("origin", origin)
            .put("cookies", array)
            .toString(2)
    }

    /**
     * Accepts SPACE JSON, common Cookie-Editor JSON arrays, and Netscape cookie
     * files. Domain fields are intentionally ignored; imports are scoped to the
     * current top-level site by the caller.
     */
    fun import(text: String): List<TransferCookie> {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "The cookie file is empty" }
        return when {
            trimmed.startsWith("[") -> parseJsonArray(JSONArray(trimmed))
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                parseJsonArray(root.optJSONArray("cookies") ?: JSONArray())
            }
            else -> parseNetscape(trimmed)
        }.distinctBy { it.name to it.path }
    }

    internal fun parseHeader(header: String): List<TransferCookie> =
        header.split(';').mapNotNull { part ->
            val pieces = part.trim().split('=', limit = 2)
            val name = pieces.getOrNull(0).orEmpty().trim()
            val value = pieces.getOrNull(1) ?: return@mapNotNull null
            if (!validName(name)) null else TransferCookie(name, value)
        }

    private fun parseJsonArray(array: JSONArray): List<TransferCookie> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (!validName(name)) continue
            add(
                TransferCookie(
                    name = name,
                    value = item.optString("value"),
                    path = item.optString("path", "/").takeIf { it.startsWith('/') } ?: "/",
                    secure = item.optBoolean("secure", false),
                    httpOnly = item.optBoolean("httpOnly", false),
                ),
            )
        }
    }

    private fun parseNetscape(text: String): List<TransferCookie> =
        text.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isBlank() || (line.startsWith('#') && !line.startsWith("#HttpOnly_"))) {
                return@mapNotNull null
            }
            val fields = line.removePrefix("#HttpOnly_").split('\t')
            if (fields.size < 7) return@mapNotNull null
            val name = fields[5].trim()
            if (!validName(name)) return@mapNotNull null
            TransferCookie(
                name = name,
                value = fields[6],
                path = fields[2].takeIf { it.startsWith('/') } ?: "/",
                secure = fields[3].equals("TRUE", true),
                httpOnly = line.startsWith("#HttpOnly_"),
            )
        }.toList()

    private fun validName(name: String): Boolean =
        name.isNotBlank() &&
            name.length <= 256 &&
            name.none { it <= ' ' || it in "()<>@,;:\\\"/[]?={}" }
}
