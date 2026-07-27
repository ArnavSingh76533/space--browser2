package com.spacebrowser.ui.ai

import com.spacebrowser.core.browser.MediaCommand
import com.spacebrowser.core.browser.WebStep
import com.spacebrowser.core.settings.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

/** Allowlisted browser actions SPACE AI may request. Everything else is treated as text. */
sealed class AiAction(val label: String) {
    data class OpenUrl(val url: String) : AiAction("Open $url")
    data class SearchWeb(val query: String) : AiAction("Search the web for “$query”")
    data class PlayMedia(val query: String) : AiAction("Play “$query”")
    data class MediaControl(val command: MediaCommand) : AiAction(command.label)
    data class WebActions(val steps: List<WebStep>) :
        AiAction(steps.joinToString(prefix = "Run: ", separator = " → ") { it.label })
    data object NewTab : AiAction("Open a new tab")
    data object CloseTab : AiAction("Close the current tab")
    data object GoBack : AiAction("Go back")
    data object GoForward : AiAction("Go forward")
    data object Reload : AiAction("Reload this page")
    data class SetTheme(val mode: ThemeMode) : AiAction(
        "Switch theme to " + when (mode) {
            ThemeMode.SYSTEM -> "Follow system"
            ThemeMode.AUTO -> "Auto"
            ThemeMode.LIGHT -> "Daylight"
            ThemeMode.DARK -> "Dim"
            ThemeMode.AMOLED -> "Dark (AMOLED)"
        },
    )
    data class SetDesktopMode(val enabled: Boolean) :
        AiAction(if (enabled) "Turn desktop mode on" else "Turn desktop mode off")
    data class SetShield(val enabled: Boolean) :
        AiAction(if (enabled) "Turn the SPACE shield on" else "Turn the SPACE shield off")
    data object OpenDownloads : AiAction("Open your downloads")
    data object ClosePrivateTabs : AiAction("Close all private tabs")
}

/** Strictly parses a model reply into an allowlisted action, or null to show it as text. */
internal fun parseAiAction(text: String): AiAction? {
    var candidate = text.trim()
    if (candidate.startsWith("```")) {
        candidate = candidate.removePrefix("```json").removePrefix("```").trim()
        candidate = candidate.removeSuffix("```").trim()
    }
    if (!candidate.startsWith("{") || !candidate.endsWith("}")) return null
    return try {
        val obj = JSONObject(candidate)
        when (obj.optString("action").trim().uppercase()) {
            "OPEN_URL" -> {
                val url = obj.optString("url").trim()
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    AiAction.OpenUrl(url)
                } else {
                    null
                }
            }
            "SEARCH_WEB" -> obj.optString("query").trim()
                .takeIf { it.isNotEmpty() }?.let { AiAction.SearchWeb(it) }
            "PLAY_MEDIA" -> obj.optString("query").trim().take(200)
                .takeIf { it.isNotEmpty() }?.let { AiAction.PlayMedia(it) }
            "MEDIA_CONTROL" -> parseMediaCommand(obj)?.let { AiAction.MediaControl(it) }
            "WEB_SEQUENCE" -> parseWebSteps(obj.optJSONArray("steps"))
                ?.let { AiAction.WebActions(it) }
            "CLICK_TEXT", "CLICK" -> parseWebStep(
                JSONObject()
                    .put("type", "CLICK_TEXT")
                    .put("text", obj.optString("text")),
            )?.let { AiAction.WebActions(listOf(it)) }
            "FILL_FIELD", "FILL" -> parseWebStep(
                JSONObject()
                    .put("type", "FILL_FIELD")
                    .put("field", obj.optString("field"))
                    .put("value", obj.optString("value")),
            )?.let { AiAction.WebActions(listOf(it)) }
            "FIND_TEXT" -> parseWebStep(
                JSONObject()
                    .put("type", "FIND_TEXT")
                    .put("text", obj.optString("text")),
            )?.let { AiAction.WebActions(listOf(it)) }
            "NEW_TAB" -> AiAction.NewTab
            "CLOSE_TAB" -> AiAction.CloseTab
            "GO_BACK" -> AiAction.GoBack
            "GO_FORWARD" -> AiAction.GoForward
            "RELOAD" -> AiAction.Reload
            "SET_THEME" -> runCatching {
                ThemeMode.valueOf(obj.optString("mode").trim().uppercase())
            }.getOrNull()?.let { AiAction.SetTheme(it) }
            "SET_DESKTOP_MODE" -> AiAction.SetDesktopMode(obj.optBoolean("enabled", true))
            "SET_SHIELD" -> AiAction.SetShield(obj.optBoolean("enabled", true))
            "OPEN_DOWNLOADS" -> AiAction.OpenDownloads
            "CLOSE_PRIVATE_TABS" -> AiAction.ClosePrivateTabs
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun parseMediaCommand(obj: JSONObject): MediaCommand? =
    when (obj.optString("command").trim().uppercase()) {
        "PLAY" -> MediaCommand.Play
        "PAUSE" -> MediaCommand.Pause
        "NEXT" -> MediaCommand.Next
        "PREVIOUS" -> MediaCommand.Previous
        "MUTE" -> MediaCommand.Mute
        "UNMUTE" -> MediaCommand.Unmute
        "SEEK_TO" -> obj.optDouble("seconds", Double.NaN)
            .takeIf { it.isFinite() && it >= 0 }?.let { MediaCommand.SeekTo(it) }
        "SEEK_BY" -> obj.optDouble("seconds", Double.NaN)
            .takeIf { it.isFinite() }?.let { MediaCommand.SeekBy(it) }
        "VOLUME", "SET_VOLUME" -> obj.optInt("value", -1)
            .takeIf { it in 0..100 }?.let { MediaCommand.SetVolume(it) }
        else -> null
    }

private fun parseWebSteps(array: JSONArray?): List<WebStep>? {
    if (array == null || array.length() !in 1..8) return null
    return buildList {
        for (index in 0 until array.length()) {
            val step = parseWebStep(array.optJSONObject(index) ?: return null) ?: return null
            add(step)
        }
    }
}

private fun parseWebStep(obj: JSONObject): WebStep? =
    when (obj.optString("type").trim().uppercase()) {
        "OPEN_URL" -> obj.optString("url").trim().take(2_000)
            .takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.let { WebStep.OpenUrl(it) }
        "CLICK_TEXT", "CLICK" -> obj.optString("text").trim().take(160)
            .takeIf { it.isNotEmpty() }?.let { WebStep.ClickText(it) }
        "FILL_FIELD", "FILL" -> {
            val field = obj.optString("field").trim().take(120)
            val value = obj.optString("value").take(2_000)
            if (field.isBlank()) null else WebStep.FillField(field, value)
        }
        "FIND_TEXT", "FIND" -> obj.optString("text").trim().take(200)
            .takeIf { it.isNotEmpty() }?.let { WebStep.FindText(it) }
        "SCROLL" -> obj.optString("direction", "down").trim()
            .takeIf { it.lowercase() in setOf("up", "down", "top", "bottom") }
            ?.let { WebStep.Scroll(it) }
        "WAIT" -> WebStep.Wait(obj.optLong("millis", 500).coerceIn(0, 5_000))
        else -> null
    }

/** Deterministic handling for common media phrases, independent of model quality. */
internal fun parseLocalBrowserCommand(text: String): AiAction? {
    val input = text.trim()
    val lower = input.lowercase()
    when (lower) {
        "play", "resume", "continue", "play this", "resume this" ->
            return AiAction.MediaControl(MediaCommand.Play)
        "pause", "pause this", "stop playback" ->
            return AiAction.MediaControl(MediaCommand.Pause)
        "next", "next video", "next song" ->
            return AiAction.MediaControl(MediaCommand.Next)
        "previous", "previous video", "previous song" ->
            return AiAction.MediaControl(MediaCommand.Previous)
        "mute" -> return AiAction.MediaControl(MediaCommand.Mute)
        "unmute" -> return AiAction.MediaControl(MediaCommand.Unmute)
        "go back" -> return AiAction.GoBack
        "go forward" -> return AiAction.GoForward
        "reload", "refresh" -> return AiAction.Reload
    }

    Regex("""^(?:play|watch|listen to)\s+(.+)$""", RegexOption.IGNORE_CASE)
        .matchEntire(input)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { return AiAction.PlayMedia(it.take(200)) }

    Regex("""(?:skip|seek|go)(?:\s+to)?\s+(\d+):(\d{1,2})""", RegexOption.IGNORE_CASE)
        .find(input)?.let { match ->
            val minutes = match.groupValues[1].toDoubleOrNull() ?: return@let
            val seconds = match.groupValues[2].toDoubleOrNull() ?: return@let
            return AiAction.MediaControl(MediaCommand.SeekTo(minutes * 60 + seconds))
        }

    Regex(
        """(?:skip|seek|go).*?(\d+)\s*(?:minutes?|mins?)(?:\s*(?:and\s*)?(\d+)\s*(?:seconds?|secs?))?""",
        RegexOption.IGNORE_CASE,
    ).find(input)?.let { match ->
        val minutes = match.groupValues[1].toDoubleOrNull() ?: return@let
        val seconds = match.groupValues.getOrNull(2)?.toDoubleOrNull() ?: 0.0
        return AiAction.MediaControl(MediaCommand.SeekTo(minutes * 60 + seconds))
    }

    Regex("""(?:set\s+)?volume(?:\s+to)?\s+(\d{1,3})""", RegexOption.IGNORE_CASE)
        .find(input)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..100 }
        ?.let { return AiAction.MediaControl(MediaCommand.SetVolume(it)) }

    return null
}
