package com.spacebrowser.ui.ai

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.spacebrowser.core.AppContainer
import com.spacebrowser.core.browser.Tab
import com.spacebrowser.core.net.AiConfig
import com.spacebrowser.core.settings.AiActionMode
import com.spacebrowser.core.settings.SpaceSettings
import com.spacebrowser.ui.components.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import kotlin.coroutines.resume

private const val EXTRACT_JS =
    "(function(){try{return document.body.innerText.slice(0,12000)}catch(e){return ''}})()"

private val LANGUAGES = listOf(
    "English", "Spanish", "French", "German", "Portuguese", "Hindi", "Japanese", "Chinese",
)

private const val SYSTEM_PROMPT =
    "You are the built-in assistant of SPACE, a privacy-first Android browser. " +
        "Be accurate and concise. Reply in plain text without markdown formatting. " +
        "You receive PAGE TITLE, PAGE URL and visible PAGE TEXT when a page is open; use them to " +
        "answer questions and extract relevant information. SPECIAL RULE: if, and only if, the " +
        "user asks you to perform an action, reply with ONLY one single-line JSON object. " +
        "Supported actions: " +
        "{\"action\":\"OPEN_URL\",\"url\":\"https://...\"} | " +
        "{\"action\":\"SEARCH_WEB\",\"query\":\"...\"} | " +
        "{\"action\":\"PLAY_MEDIA\",\"query\":\"song or video\"} | " +
        "{\"action\":\"MEDIA_CONTROL\",\"command\":\"PLAY|PAUSE|SEEK_TO|SEEK_BY|VOLUME|MUTE|UNMUTE|NEXT|PREVIOUS\",\"seconds\":140,\"value\":50} | " +
        "{\"action\":\"WEB_SEQUENCE\",\"steps\":[{\"type\":\"OPEN_URL\",\"url\":\"https://...\"}," +
        "{\"type\":\"CLICK_TEXT\",\"text\":\"visible button text\"}," +
        "{\"type\":\"FILL_FIELD\",\"field\":\"visible label\",\"value\":\"text\"}," +
        "{\"type\":\"FIND_TEXT\",\"text\":\"phrase\"},{\"type\":\"SCROLL\",\"direction\":\"down\"}," +
        "{\"type\":\"WAIT\",\"millis\":500}]} | " +
        "{\"action\":\"NEW_TAB\"} | {\"action\":\"CLOSE_TAB\"} | " +
        "{\"action\":\"GO_BACK\"} | {\"action\":\"GO_FORWARD\"} | {\"action\":\"RELOAD\"} | " +
        "{\"action\":\"SET_THEME\",\"mode\":\"AUTO|SYSTEM|LIGHT|DARK|AMOLED\"} " +
        "(LIGHT = Daylight, DARK = Dim, AMOLED = pure black) | " +
        "{\"action\":\"SET_DESKTOP_MODE\",\"enabled\":true} | " +
        "{\"action\":\"SET_SHIELD\",\"enabled\":true} | " +
        "{\"action\":\"OPEN_DOWNLOADS\"} | " +
        "{\"action\":\"CLOSE_PRIVATE_TABS\"}. " +
        "Convert spoken times such as 2:20 to seconds (140). Use PLAY_MEDIA instead of opening a " +
        "YouTube results page. WEB_SEQUENCE is limited to safe visible-page operations; never ask " +
        "to fill passwords, payment details, OTPs or other secrets. For anything unsupported, " +
        "answer normally instead of inventing an action."

/** Reads the page's visible text on the main thread; empty string on failure. */
private suspend fun extractPageText(webView: WebView?): String {
    if (webView == null) return ""
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            try {
                webView.evaluateJavascript(EXTRACT_JS) { raw ->
                    val decoded = try {
                        JSONTokener(raw).nextValue() as? String
                    } catch (_: Exception) {
                        null
                    }
                    if (cont.isActive) cont.resume(decoded.orEmpty())
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume("")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSheet(
    container: AppContainer,
    settings: SpaceSettings,
    tab: Tab?,
    onDismiss: () -> Unit,
    onAction: (AiAction) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val config = remember(settings.aiEndpoint, settings.aiModel) {
        AiConfig(
            endpoint = settings.aiEndpoint,
            model = settings.aiModel,
            apiKey = container.secureStore.aiApiKey,
        )
    }
    val hasPage = tab != null && !tab.showHome && tab.webView != null

    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var translateMode by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<AiAction?>(null) }

    fun dispatch(action: AiAction) {
        onAction(action)
        when (action) {
            is AiAction.OpenUrl,
            is AiAction.SearchWeb,
            is AiAction.PlayMedia,
            is AiAction.WebActions,
            AiAction.NewTab,
            AiAction.CloseTab,
            AiAction.OpenDownloads,
            -> onDismiss()
            else -> result =
                "Done — ${action.label.replaceFirstChar { it.lowercase() }}."
        }
    }

    fun run(task: String, usePage: Boolean) {
        if (loading) return
        translateMode = false
        parseLocalBrowserCommand(task)?.let { action ->
            result = ""
            error = null
            if (settings.aiActionMode == AiActionMode.AUTOMATIC) {
                dispatch(action)
            } else {
                pendingAction = action
            }
            return
        }
        scope.launch {
            loading = true
            error = null
            result = ""
            val page = if (usePage) extractPageText(tab?.webView) else ""
            if (usePage && page.isBlank()) {
                error = "Couldn't read this page's text."
                loading = false
                return@launch
            }
            val user = buildString {
                append(task)
                if (page.isNotBlank()) {
                    append("\n\nPAGE TITLE: ").append(tab?.title.orEmpty())
                    append("\nPAGE URL: ").append(tab?.url.orEmpty())
                    append("\nPAGE TEXT:\n").append(page)
                }
            }
            container.aiClient.complete(config, SYSTEM_PROMPT, user).fold(
                onSuccess = { reply ->
                    val action = parseAiAction(reply)
                    if (action == null) {
                        result = reply.trim()
                    } else if (settings.aiActionMode == AiActionMode.AUTOMATIC) {
                        dispatch(action)
                    } else {
                        pendingAction = action
                    }
                },
                onFailure = { error = it.message ?: "Request failed" },
            )
            loading = false
        }
    }

    pendingAction?.let { act ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("Allow this action?") },
            text = { Text("SPACE AI wants to: ${act.label}") },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    dispatch(act)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingAction = null
                    result = "Action cancelled."
                }) { Text("Deny") }
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("AI assistant", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (settings.aiActionMode == AiActionMode.ASK) {
                            "Actions ask for permission"
                        } else {
                            "Supported actions run automatically"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!config.isConfigured) {
                Text(
                    "Local commands such as “Play Titanium”, pause, seek and volume already work. " +
                        "For page summaries and questions, add an OpenAI-compatible endpoint and " +
                        "model under Settings → AI assistant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            // Actions -----------------------------------------------------------
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(top = 14.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                AiChip("Summarize", enabled = config.isConfigured && hasPage && !loading) {
                    run("Summarize this web page in a short paragraph.", usePage = true)
                }
                AiChip("Key points", enabled = config.isConfigured && hasPage && !loading) {
                    run("List the key points of this web page, one per line.", usePage = true)
                }
                AiChip("Explain simply", enabled = config.isConfigured && hasPage && !loading) {
                    run("Explain this web page in simple words a beginner understands.", usePage = true)
                }
                AiChip("Translate", enabled = config.isConfigured && hasPage && !loading) {
                    translateMode = !translateMode
                }
            }
            if (translateMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    LANGUAGES.forEach { lang ->
                        AiChip(lang, enabled = config.isConfigured && !loading) {
                            run("Translate the main content of this web page to $lang.", usePage = true)
                        }
                    }
                }
            }

            // Free-form question ------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = {
                        Text(if (hasPage) "Ask about this page…" else "Ask anything — or \u201copen wikipedia\u201d…")
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val q = question.trim()
                        if (q.isNotEmpty()) run(q, usePage = hasPage)
                    },
                    enabled = !loading && question.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send question")
                }
            }

            // Result ------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = 90.dp, max = 320.dp)
                    .glass(
                        RoundedCornerShape(16.dp),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primary,
                        alpha = 0.6f,
                    )
                    .padding(14.dp),
            ) {
                when {
                    loading -> CircularProgressIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.Center),
                    )
                    error != null -> Text(
                        error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    result.isNotBlank() -> SelectionContainer {
                        Text(
                            result,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                    else -> Text(
                        if (hasPage) "Pick an action or ask a question about this page."
                        else "No page is open — you can still ask a general question.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Text(
                    config.model,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (result.isNotBlank()) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(result)) }) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .glass(
                RoundedCornerShape(16.dp),
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.primary,
                alpha = 0.7f,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
