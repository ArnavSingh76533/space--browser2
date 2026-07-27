package com.spacebrowser.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import com.spacebrowser.BuildConfig
import com.spacebrowser.core.AppContainer
import com.spacebrowser.core.settings.AiActionMode
import com.spacebrowser.core.settings.SearchEngines
import com.spacebrowser.core.settings.ThemeMode
import com.spacebrowser.ui.ActivityActions
import com.spacebrowser.ui.theme.Accents
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    actions: ActivityActions,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = container.settingsRepository
    val s by repo.flow.collectAsState(initial = container.tabManager.settings)
    val userScripts by container.userScriptManager.scripts.collectAsState()

    var query by remember { mutableStateOf("") }
    fun visible(vararg labels: String): Boolean =
        query.isBlank() || labels.any { it.contains(query, ignoreCase = true) }

    // Dialog state --------------------------------------------------------------
    var themeDialog by remember { mutableStateOf(false) }
    var accentDialog by remember { mutableStateOf(false) }
    var engineDialog by remember { mutableStateOf(false) }
    var customSearchDialog by remember { mutableStateOf(false) }
    var rulesDialog by remember { mutableStateOf(false) }
    var allowlistDialog by remember { mutableStateOf(false) }
    var retentionDialog by remember { mutableStateOf(false) }
    var clearNowDialog by remember { mutableStateOf(false) }
    var aiEndpointDialog by remember { mutableStateOf(false) }
    var aiModelDialog by remember { mutableStateOf(false) }
    var aiKeyDialog by remember { mutableStateOf(false) }
    var aiModeDialog by remember { mutableStateOf(false) }
    var ytDlpDialog by remember { mutableStateOf(false) }
    var exportCookiesDialog by remember { mutableStateOf(false) }
    var importCookiesDialog by remember { mutableStateOf(false) }
    var importExtensionDialog by remember { mutableStateOf(false) }
    var developerToolsDialog by remember { mutableStateOf(false) }
    var developerToolsInfoDialog by remember { mutableStateOf(false) }
    var removeExtensionId by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search settings") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // Appearance --------------------------------------------------------
            SectionHeader("Appearance", query)
            if (visible("Theme", "dark", "light", "amoled")) {
                ActionRow(
                    "Theme",
                    when (s.themeMode) {
                        ThemeMode.SYSTEM -> "Follow system"
                        ThemeMode.AUTO -> "Auto (day / night)"
                        ThemeMode.LIGHT -> "Daylight"
                        ThemeMode.DARK -> "Dim"
                        ThemeMode.AMOLED -> "Dark (AMOLED)"
                    },
                ) { themeDialog = true }
            }
            if (visible("Accent color")) {
                ActionRow("Accent color", Accents[s.accentIndex.coerceIn(0, Accents.lastIndex)].name) {
                    accentDialog = true
                }
            }
            if (visible("Dynamic color", "material you")) {
                SwitchRow(
                    "Dynamic color",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Use wallpaper colors (Material You)"
                    else "Requires Android 12+",
                    checked = s.dynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                ) { v -> scope.launch { repo.setDynamicColor(v) } }
            }
            if (visible("Animated galaxy background", "stars")) {
                SwitchRow(
                    "Animated galaxy background",
                    "Twinkling starfield and nebulas",
                    checked = s.animatedBackground,
                ) { v -> scope.launch { repo.setAnimatedBackground(v) } }
            }
            if (s.animatedBackground && visible("Animation intensity", "stars")) {
                SliderRow(
                    "Animation intensity",
                    value = s.animationLevel,
                    range = 0.3f..1f,
                ) { v -> scope.launch { repo.setAnimationLevel(v) } }
            }
            if (visible("Dark mode for websites", "web dark")) {
                SwitchRow(
                    "Dark mode for websites",
                    "Ask pages to render dark (algorithmic darkening)",
                    checked = s.webDarkMode,
                ) { v -> scope.launch { repo.setWebDarkMode(v) } }
            }

            // Privacy & shields -------------------------------------------------
            SectionHeader("Privacy & shields", query)
            if (visible("Block ads and trackers", "adblock", "shields")) {
                SwitchRow(
                    "Block ads & trackers",
                    "${container.adBlocker.ruleCount} rules loaded",
                    checked = s.adBlockEnabled,
                ) { v -> scope.launch { repo.setAdBlockEnabled(v) } }
            }
            if (visible("Custom block rules", "adblock")) {
                ActionRow("Custom block rules", "${s.adBlockCustomRules.size} rules") { rulesDialog = true }
            }
            if (visible("Allowlisted sites", "shields down")) {
                ActionRow("Allowlisted sites", "${s.adBlockAllowlist.size} sites") { allowlistDialog = true }
            }
            if (visible("Upgrade connections to HTTPS", "https")) {
                SwitchRow(
                    "Upgrade connections to HTTPS",
                    "Try the encrypted version of http:// links first",
                    checked = s.httpsUpgrade,
                ) { v -> scope.launch { repo.setHttpsUpgrade(v) } }
            }
            if (visible("Block third-party cookies", "cookies")) {
                SwitchRow(
                    "Block third-party cookies",
                    "Cuts cross-site tracking cookies",
                    checked = s.blockThirdPartyCookies,
                ) { v -> scope.launch { repo.setBlockThirdPartyCookies(v) } }
            }
            if (visible("Safe Browsing", "malware", "phishing")) {
                SwitchRow(
                    "Safe Browsing",
                    "Warn about known dangerous sites",
                    checked = s.safeBrowsing,
                ) { v -> scope.launch { repo.setSafeBrowsing(v) } }
            }
            if (visible("Generic user agent", "fingerprint")) {
                SwitchRow(
                    "Generic user agent",
                    "Present a common device identity to reduce fingerprinting",
                    checked = s.uaPrivacyMode,
                ) { v -> scope.launch { repo.setUaPrivacyMode(v) } }
            }
            if (visible("Sites may ask for camera and microphone", "permissions")) {
                SwitchRow(
                    "Sites may ask for camera & mic",
                    "Off: requests are denied silently",
                    checked = s.askCameraMic,
                ) { v -> scope.launch { repo.setAskCameraMic(v) } }
            }
            if (visible("Sites may ask for location", "gps")) {
                SwitchRow(
                    "Sites may ask for location",
                    "Off: requests are denied silently",
                    checked = s.askLocation,
                ) { v -> scope.launch { repo.setAskLocation(v) } }
            }
            if (visible("Clear browsing data now", "delete")) {
                ActionRow("Clear browsing data now", "History, cookies, cache") { clearNowDialog = true }
            }

            // Search & browsing -------------------------------------------------
            SectionHeader("Search & browsing", query)
            if (visible("Search engine")) {
                ActionRow("Search engine", SearchEngines.byId(s.searchEngineId).name) { engineDialog = true }
            }
            if (s.searchEngineId == SearchEngines.CUSTOM_ID && visible("Custom search URL")) {
                ActionRow(
                    "Custom search URL",
                    s.customSearchUrl.ifBlank { "Not set — %s is replaced by the query" },
                ) { customSearchDialog = true }
            }
            if (visible("Search suggestions")) {
                SwitchRow(
                    "Search suggestions",
                    "Fetch suggestions from your engine while typing",
                    checked = s.searchSuggestions,
                ) { v -> scope.launch { repo.setSearchSuggestions(v) } }
            }
            if (visible("Desktop sites by default", "desktop mode")) {
                SwitchRow(
                    "Desktop sites by default",
                    "New tabs request the desktop version of pages",
                    checked = s.desktopDefault,
                ) { v -> scope.launch { repo.setDesktopDefault(v) } }
            }
            if (visible("JavaScript")) {
                SwitchRow(
                    "JavaScript",
                    "Most sites need this; disable for maximum privacy",
                    checked = s.javascriptEnabled,
                ) { v -> scope.launch { repo.setJavascriptEnabled(v) } }
            }
            if (visible("Block images", "data saver")) {
                SwitchRow(
                    "Block images",
                    "Data saver: pages load text only",
                    checked = s.blockImages,
                ) { v -> scope.launch { repo.setBlockImages(v) } }
            }
            if (visible("Background media playback", "audio", "video")) {
                SwitchRow(
                    "Background media playback",
                    "Keep playing when SPACE is minimized, with notification controls",
                    checked = s.backgroundPlaybackEnabled,
                ) { v -> scope.launch { repo.setBackgroundPlaybackEnabled(v) } }
            }

            // Data --------------------------------------------------------------
            SectionHeader("Data", query)
            if (visible("Clear history on exit")) {
                SwitchRow("Clear history on exit", null, checked = s.clearHistoryOnExit) { v ->
                    scope.launch { repo.setClearHistoryOnExit(v) }
                }
            }
            if (visible("Clear cookies on exit")) {
                SwitchRow("Clear cookies on exit", "Signs you out of sites", checked = s.clearCookiesOnExit) { v ->
                    scope.launch { repo.setClearCookiesOnExit(v) }
                }
            }
            if (visible("Clear cache on exit")) {
                SwitchRow("Clear cache on exit", null, checked = s.clearCacheOnExit) { v ->
                    scope.launch { repo.setClearCacheOnExit(v) }
                }
            }
            if (visible("Keep history for", "retention")) {
                ActionRow(
                    "Keep history for",
                    if (s.historyRetentionDays == 0) "Forever" else "${s.historyRetentionDays} days",
                ) { retentionDialog = true }
            }
            if (visible("Export current-site cookies", "cookie backup")) {
                ActionRow(
                    "Export current-site cookies",
                    "Save the active site's cookie state as JSON",
                ) { exportCookiesDialog = true }
            }
            if (visible("Import cookies into current site", "cookie restore")) {
                ActionRow(
                    "Import cookies into current site",
                    "SPACE JSON, Cookie-Editor JSON, or Netscape format",
                ) { importCookiesDialog = true }
            }

            // Security ----------------------------------------------------------
            SectionHeader("Security", query)
            if (visible("App lock", "biometric", "fingerprint")) {
                SwitchRow(
                    "App lock",
                    "Require fingerprint / screen lock to open SPACE",
                    checked = s.appLock,
                ) { v ->
                    if (v) {
                        val can = BiometricManager.from(context).canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                        ) == BiometricManager.BIOMETRIC_SUCCESS
                        if (can) {
                            scope.launch { repo.setAppLock(true) }
                        } else {
                            container.browserEvents.toast("Set up a screen lock first")
                        }
                    } else {
                        scope.launch { repo.setAppLock(false) }
                    }
                }
            }

            // AI assistant ------------------------------------------------------
            SectionHeader("AI assistant", query)
            if (visible("Quick SPACE AI button", "assistant shortcut")) {
                SwitchRow(
                    "Quick SPACE AI button",
                    "Show the small AI button directly in the browser",
                    checked = s.spaceAiButtonEnabled,
                ) { v -> scope.launch { repo.setSpaceAiButtonEnabled(v) } }
            }
            if (visible("AI action permissions", "automatic", "ask permission")) {
                ActionRow(
                    "Action permissions",
                    when (s.aiActionMode) {
                        AiActionMode.ASK -> "Ask before every browser action"
                        AiActionMode.AUTOMATIC -> "Run supported actions automatically"
                    },
                ) { aiModeDialog = true }
            }
            if (visible("AI endpoint", "openai", "ollama")) {
                ActionRow(
                    "Endpoint",
                    s.aiEndpoint.ifBlank { "Not set — any OpenAI-compatible base URL" },
                ) { aiEndpointDialog = true }
            }
            if (visible("AI model")) {
                ActionRow("Model", s.aiModel.ifBlank { "Not set" }) { aiModelDialog = true }
            }
            if (visible("AI API key")) {
                ActionRow(
                    "API key",
                    if (container.secureStore.aiApiKey.isBlank()) "Not set" else "Stored encrypted",
                ) { aiKeyDialog = true }
            }

            // Downloads ---------------------------------------------------------
            SectionHeader("Downloads", query)
            if (visible("Download over Wi-Fi only", "wifi")) {
                SwitchRow(
                    "Download over Wi-Fi only",
                    "Queue downloads until an unmetered network is available",
                    checked = s.downloadWifiOnly,
                ) { v -> scope.launch { repo.setDownloadWifiOnly(v) } }
            }
            if (visible("Ask before downloading", "confirm downloads")) {
                SwitchRow(
                    "Ask before downloading",
                    "Confirm the file name and size first",
                    checked = s.confirmDownloads,
                ) { v -> scope.launch { repo.setConfirmDownloads(v) } }
            }
            if (visible("Media downloader", "yt-dlp", "video download")) {
                SwitchRow(
                    "Media downloader (yt-dlp)",
                    if (s.ytDlpEnabled) "On — use \"Download media\" in the menu on video pages"
                    else "Save video / audio from pages that allow it",
                    checked = s.ytDlpEnabled,
                ) { v ->
                    if (v) ytDlpDialog = true
                    else scope.launch { repo.setYtDlpEnabled(false) }
                }
            }

            // Extensions & developer tools ------------------------------------
            SectionHeader("Extensions & developer tools", query)
            if (visible("Import local extension", "userscript", "tampermonkey")) {
                ActionRow(
                    "Import local extension",
                    "Install a .user.js script or SPACE extension bundle",
                ) { importExtensionDialog = true }
            }
            if (visible("Export local extensions", "userscript backup")) {
                ActionRow(
                    "Export local extensions",
                    "${userScripts.size} installed scripts",
                ) { actions.exportUserScripts() }
            }
            userScripts.forEach { script ->
                if (visible(script.name, "local extension", "userscript")) {
                    SwitchRow(
                        "Extension: ${script.name}",
                        script.matches.take(2).joinToString().ifBlank { "All websites" },
                        checked = script.enabled,
                    ) { enabled ->
                        container.userScriptManager.setEnabled(script.id, enabled)
                        container.tabManager.activeTab?.webView?.reload()
                    }
                    ActionRow(
                        "Remove ${script.name}",
                        "Delete this local extension",
                    ) { removeExtensionId = script.id }
                }
            }
            if (visible("Chrome extensions", "CRX", "web store")) {
                ActionRow(
                    "Chrome extension compatibility",
                    "WebView cannot run CRX/Manifest V3; local userscripts are supported",
                ) {
                    container.browserEvents.toast(
                        "Full Chrome extensions require a Chromium-engine build",
                    )
                }
            }
            if (visible("Remote WebView DevTools", "chrome inspect", "developer tools")) {
                SwitchRow(
                    "Remote WebView DevTools",
                    "Inspect pages from desktop Chrome at chrome://inspect",
                    checked = s.developerToolsEnabled,
                ) { enabled ->
                    if (enabled) developerToolsDialog = true
                    else scope.launch { repo.setDeveloperToolsEnabled(false) }
                }
            }
            if (s.developerToolsEnabled &&
                visible("How to connect DevTools", "chrome inspect", "USB debugging")
            ) {
                ActionRow(
                    "How to connect DevTools",
                    "USB debugging → chrome://inspect",
                ) { developerToolsInfoDialog = true }
            }

            // About -------------------------------------------------------------
            SectionHeader("More", query)
            if (visible("Downloads")) {
                ActionRow("System downloads", "Open Android's download manager") {
                    actions.openSystemDownloads()
                }
            }
            if (visible("About", "version")) {
                ActionRow(
                    "About SPACE",
                    "Version ${BuildConfig.VERSION_NAME} — your galaxy-grade private browser",
                ) { }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // Dialogs -------------------------------------------------------------------
    if (themeDialog) {
        RadioDialog(
            title = "Theme",
            options = listOf(
                "Auto — Daylight 7 AM–6 PM, Dim at night" to ThemeMode.AUTO,
                "Follow system" to ThemeMode.SYSTEM,
                "Daylight" to ThemeMode.LIGHT,
                "Dim — soft charcoal" to ThemeMode.DARK,
                "Dark — AMOLED black" to ThemeMode.AMOLED,
            ),
            selected = s.themeMode,
            onSelect = { scope.launch { repo.setThemeMode(it) }; themeDialog = false },
            onDismiss = { themeDialog = false },
        )
    }

    if (ytDlpDialog) {
        AlertDialog(
            onDismissRequest = { ytDlpDialog = false },
            title = { Text("Enable media downloader?") },
            text = {
                Text(
                    "SPACE bundles the open-source yt-dlp engine and prepares it on first use.\n\n" +
                        "\u2022 Only save media you have the right to download. Many sites' terms forbid it \u2014 you are responsible for how you use this.\n" +
                        "\u2022 Detects supported page media, direct video files, HLS, and DASH sources.\n" +
                        "\u2022 The bundled engine makes the app noticeably larger.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.setYtDlpEnabled(true) }
                    ytDlpDialog = false
                }) { Text("Enable") }
            },
            dismissButton = { TextButton(onClick = { ytDlpDialog = false }) { Text("Cancel") } },
        )
    }

    if (exportCookiesDialog) {
        AlertDialog(
            onDismissRequest = { exportCookiesDialog = false },
            title = { Text("Export this site's cookies?") },
            text = {
                Text(
                    "Cookie files can contain signed-in session tokens. Anyone with the exported " +
                        "file may be able to use that site's session. Keep it private.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    exportCookiesDialog = false
                    actions.exportCurrentSiteCookies()
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { exportCookiesDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (importCookiesDialog) {
        AlertDialog(
            onDismissRequest = { importCookiesDialog = false },
            title = { Text("Import cookies into this site?") },
            text = {
                Text(
                    "SPACE scopes imported cookies to the currently open website and ignores " +
                        "domains from the file. Import only files you trust.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    importCookiesDialog = false
                    actions.importCurrentSiteCookies()
                }) { Text("Choose file") }
            },
            dismissButton = {
                TextButton(onClick = { importCookiesDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (importExtensionDialog) {
        AlertDialog(
            onDismissRequest = { importExtensionDialog = false },
            title = { Text("Install a local extension?") },
            text = {
                Text(
                    "A userscript can read and change every matching webpage. Install scripts " +
                        "only from authors you trust. SPACE stores them locally and shows an " +
                        "on/off switch for each script.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    importExtensionDialog = false
                    actions.importUserScript()
                }) { Text("Choose script") }
            },
            dismissButton = {
                TextButton(onClick = { importExtensionDialog = false }) { Text("Cancel") }
            },
        )
    }

    removeExtensionId?.let { id ->
        val script = userScripts.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { removeExtensionId = null },
            title = { Text("Remove ${script?.name ?: "extension"}?") },
            text = { Text("The local script will stop running and be deleted from SPACE.") },
            confirmButton = {
                TextButton(onClick = {
                    container.userScriptManager.remove(id)
                    container.tabManager.activeTab?.webView?.reload()
                    removeExtensionId = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removeExtensionId = null }) { Text("Cancel") }
            },
        )
    }

    if (developerToolsDialog) {
        AlertDialog(
            onDismissRequest = { developerToolsDialog = false },
            title = { Text("Enable remote DevTools?") },
            text = {
                Text(
                    "When USB debugging is authorized, desktop debugging tools can inspect and " +
                        "modify open WebView pages. Turn this off after development.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.setDeveloperToolsEnabled(true) }
                    developerToolsDialog = false
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { developerToolsDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (developerToolsInfoDialog) {
        AlertDialog(
            onDismissRequest = { developerToolsInfoDialog = false },
            title = { Text("Connect DevTools") },
            text = {
                Text(
                    "1. Enable Android Developer options and USB debugging.\n" +
                        "2. Connect the device to your computer and authorize it.\n" +
                        "3. Open chrome://inspect in desktop Chrome.\n" +
                        "4. Find com.spacebrowser and choose Inspect.",
                )
            },
            confirmButton = {
                TextButton(onClick = { developerToolsInfoDialog = false }) { Text("Done") }
            },
        )
    }

    if (aiModeDialog) {
        RadioDialog(
            title = "AI action permissions",
            options = listOf(
                "Ask for permission — confirm every command" to AiActionMode.ASK,
                "Automatic — run allowlisted commands immediately" to AiActionMode.AUTOMATIC,
            ),
            selected = s.aiActionMode,
            onSelect = {
                scope.launch { repo.setAiActionMode(it) }
                aiModeDialog = false
            },
            onDismiss = { aiModeDialog = false },
        )
    }

    if (accentDialog) {
        AlertDialog(
            onDismissRequest = { accentDialog = false },
            title = { Text("Accent color") },
            text = {
                Column {
                    Accents.forEachIndexed { i, accent ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { repo.setAccentIndex(i) }
                                    accentDialog = false
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(accent.color),
                            ) {
                                if (i == s.accentIndex) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.align(Alignment.Center).size(16.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(accent.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { accentDialog = false }) { Text("Close") } },
        )
    }

    if (engineDialog) {
        RadioDialog(
            title = "Search engine",
            options = SearchEngines.all.map { it.name to it.id },
            selected = s.searchEngineId,
            onSelect = { scope.launch { repo.setSearchEngineId(it) }; engineDialog = false },
            onDismiss = { engineDialog = false },
        )
    }

    if (customSearchDialog) {
        TextDialog(
            title = "Custom search URL",
            initial = s.customSearchUrl,
            placeholder = "https://example.com/search?q=%s",
            onDone = { scope.launch { repo.setCustomSearchUrl(it) }; customSearchDialog = false },
            onDismiss = { customSearchDialog = false },
        )
    }

    if (rulesDialog) {
        TextDialog(
            title = "Custom block rules",
            initial = s.adBlockCustomRules.sorted().joinToString("\n"),
            placeholder = "One host per line, e.g.\nads.example.com",
            singleLine = false,
            onDone = { text ->
                val rules = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                scope.launch { repo.setAdBlockCustomRules(rules) }
                rulesDialog = false
            },
            onDismiss = { rulesDialog = false },
        )
    }

    if (allowlistDialog) {
        AlertDialog(
            onDismissRequest = { allowlistDialog = false },
            title = { Text("Allowlisted sites") },
            text = {
                if (s.adBlockAllowlist.isEmpty()) {
                    Text("No sites — shields are up everywhere. Lower them per-site from the shield icon.")
                } else {
                    Column {
                        s.adBlockAllowlist.sorted().forEach { host ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Text(host, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    scope.launch { repo.setAdBlockAllowlist(s.adBlockAllowlist - host) }
                                }) { Text("Remove") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { allowlistDialog = false }) { Text("Done") } },
        )
    }

    if (retentionDialog) {
        RadioDialog(
            title = "Keep history for",
            options = listOf("Forever" to 0, "7 days" to 7, "30 days" to 30, "90 days" to 90),
            selected = s.historyRetentionDays,
            onSelect = { scope.launch { repo.setHistoryRetentionDays(it) }; retentionDialog = false },
            onDismiss = { retentionDialog = false },
        )
    }

    if (clearNowDialog) {
        var history by remember { mutableStateOf(true) }
        var cookies by remember { mutableStateOf(false) }
        var cache by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { clearNowDialog = false },
            title = { Text("Clear browsing data") },
            text = {
                Column {
                    CheckRow("History", history) { history = it }
                    CheckRow("Cookies (signs you out of sites)", cookies) { cookies = it }
                    CheckRow("Cache & site storage", cache) { cache = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val tm = container.tabManager
                    if (history) tm.clearHistoryData()
                    if (cookies) tm.clearCookies()
                    if (cache) tm.clearCache()
                    container.browserEvents.toast("Browsing data cleared")
                    clearNowDialog = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { clearNowDialog = false }) { Text("Cancel") } },
        )
    }

    if (aiEndpointDialog) {
        TextDialog(
            title = "AI endpoint",
            initial = s.aiEndpoint,
            placeholder = "https://api.openai.com/v1",
            onDone = { scope.launch { repo.setAiEndpoint(it) }; aiEndpointDialog = false },
            onDismiss = { aiEndpointDialog = false },
        )
    }
    if (aiModelDialog) {
        TextDialog(
            title = "AI model",
            initial = s.aiModel,
            placeholder = "gpt-4o-mini, llama3, …",
            onDone = { scope.launch { repo.setAiModel(it) }; aiModelDialog = false },
            onDismiss = { aiModelDialog = false },
        )
    }
    if (aiKeyDialog) {
        TextDialog(
            title = "AI API key",
            initial = container.secureStore.aiApiKey,
            placeholder = "sk-…  (leave empty for keyless servers)",
            password = true,
            onDone = { container.secureStore.aiApiKey = it.trim(); aiKeyDialog = false },
            onDismiss = { aiKeyDialog = false },
        )
    }
}

// Row primitives ----------------------------------------------------------------

@Composable
private fun SectionHeader(text: String, query: String) {
    if (query.isNotBlank()) return
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 4.dp),
    )
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChangeFinished: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChangeFinished(local) },
            valueRange = range,
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
private fun <T> RadioDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    placeholder: String,
    password: Boolean = false,
    singleLine: Boolean = true,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = singleLine,
                visualTransformation = if (password) PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
