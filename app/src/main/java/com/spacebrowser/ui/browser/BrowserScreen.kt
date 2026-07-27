package com.spacebrowser.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebrowser.core.AppContainer
import com.spacebrowser.core.settings.SearchEngines
import com.spacebrowser.core.settings.SpaceSettings
import com.spacebrowser.core.util.UrlUtil
import com.spacebrowser.ui.ActivityActions
import com.spacebrowser.ui.LibrarySection
import com.spacebrowser.ui.ai.AiAction
import com.spacebrowser.ui.ai.AiSheet
import com.spacebrowser.ui.components.AddressBar
import com.spacebrowser.ui.components.glass
import com.spacebrowser.ui.home.StartPage
import com.spacebrowser.ui.media.MediaDownloadSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class Suggestion(val text: String, val url: String?, val fromHistory: Boolean)

@Composable
fun BrowserScreen(
    container: AppContainer,
    settings: SpaceSettings,
    actions: ActivityActions,
    onOpenTabs: () -> Unit,
    onOpenLibrary: (LibrarySection) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tabManager = container.tabManager
    val tab = tabManager.activeTab
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Address field state ------------------------------------------------------
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val addressFocus = remember { FocusRequester() }

    // Data flows ---------------------------------------------------------------
    val quickLinksFlow = remember { container.browsingRepository.quickLinks() }
    val quickLinks by quickLinksFlow.collectAsState(initial = emptyList())
    val topSitesFlow = remember { container.browsingRepository.topSites(12) }
    val topSitesAll by topSitesFlow.collectAsState(initial = emptyList())
    val topSites = remember(topSitesAll, settings.hiddenTopSites) {
        topSitesAll.filter { it.host !in settings.hiddenTopSites }
    }

    // Keep the field mirroring the page URL whenever the user isn't typing.
    LaunchedEffect(tab?.id, tab?.url, tab?.showHome, editing) {
        if (!editing) {
            field = TextFieldValue(if (tab == null || tab.showHome) "" else tab.url.orEmpty())
        }
    }

    fun submit(text: String) {
        val t = tab ?: tabManager.newTab()
        if (text.isBlank()) return
        editing = false
        focusManager.clearFocus()
        tabManager.submitInput(t, text)
    }

    // Suggestions --------------------------------------------------------------
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    LaunchedEffect(field.text, editing) {
        if (!editing || field.text.length < 2 || tab?.isPrivate == true) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(200) // debounce
        val q = field.text
        val history = container.browsingRepository.searchHistory(q, 3)
            .map { Suggestion(it.title.ifBlank { it.url }, it.url, fromHistory = true) }
        val engine = SearchEngines.resolveSuggestUrl(settings)?.let { template ->
            container.suggestionClient.fetch(template, q, 5)
                .map { Suggestion(it, null, fromHistory = false) }
        } ?: emptyList()
        suggestions = (history + engine).distinctBy { it.text }.take(7)
    }

    // Find in page -------------------------------------------------------------
    var findVisible by rememberSaveable { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var findMatches by remember { mutableStateOf(0 to 0) }

    fun closeFind() {
        tab?.webView?.clearMatches()
        findVisible = false
        findQuery = ""
        findMatches = 0 to 0
    }

    // Sheets & dialogs ---------------------------------------------------------
    var menuVisible by remember { mutableStateOf(false) }
    var aiVisible by remember { mutableStateOf(false) }
    var sitePanelVisible by remember { mutableStateOf(false) }
    var passwordGenVisible by remember { mutableStateOf(false) }
    var globalShieldVisible by remember { mutableStateOf(false) }
    var mediaVisible by remember { mutableStateOf(false) }

    val currentUrl = tab?.url
    val bookmarkFlow = remember(currentUrl) {
        currentUrl?.let { container.browsingRepository.isBookmarked(it) } ?: flowOf(0)
    }
    val bookmarkCount by bookmarkFlow.collectAsState(initial = 0)

    // Back handling ------------------------------------------------------------
    val webCanGoBack = tab != null && tab.canGoBack
    val handlesBack = editing || findVisible || webCanGoBack ||
        (tab != null && !tab.showHome) || tabManager.tabs.size > 1
    BackHandler(enabled = handlesBack) {
        val t = tab
        when {
            editing -> { editing = false; focusManager.clearFocus() }
            findVisible -> closeFind()
            t == null -> Unit
            t.webView?.canGoBack() == true -> t.webView?.goBack()
            !t.showHome -> tabManager.goHome(t)
            tabManager.tabs.size > 1 -> tabManager.close(t)
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        AddressBar(
            value = field,
            onValueChange = { field = it },
            onSubmit = ::submit,
            onFocusChanged = { focused ->
                editing = focused
                if (focused) {
                    val full = if (tab?.showHome == false) tab.url.orEmpty() else field.text
                    field = TextFieldValue(full, selection = TextRange(0, full.length))
                }
            },
            focusRequester = addressFocus,
            isEditing = editing,
            isSecure = tab?.isSecure != false,
            hasCertificateError = tab?.certificateError != null,
            isPrivateTab = tab?.isPrivate == true,
            isLoading = tab != null && !tab.showHome && tab.isLoading,
            progress = tab?.progress ?: 0,
            blockedCount = tab?.blockedOnPage ?: 0,
            shieldActive = settings.adBlockEnabled &&
                !container.adBlocker.isSiteAllowlisted(UrlUtil.hostOf(tab?.url)),
            tabCount = tabManager.tabs.size,
            onShieldClick = {
                if (tab != null && !tab.showHome) sitePanelVisible = true
                else globalShieldVisible = true
            },
            onTabsClick = {
                tab?.let { tabManager.captureThumbnail(it) }
                onOpenTabs()
            },
            onMenuClick = { menuVisible = true },
            onClearClick = { field = TextFieldValue("") },
            onReloadClick = { tab?.let { tabManager.reload(it) } },
            onStopClick = {
                tab?.webView?.stopLoading()
                tab?.isLoading = false
            },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                tab == null -> NoTabsView(onNewTab = { tabManager.newTab() })
                tab.showHome -> {
                    StartPage(
                        isPrivate = tab.isPrivate,
                        trackersBlockedTotal = settings.trackersBlockedTotal,
                        quickLinks = quickLinks,
                        topSites = topSites,
                        onSearchSubmit = ::submit,
                        onOpenUrl = { tabManager.load(tab, it) },
                        onRemoveQuickLink = {
                            scope.launch { container.browsingRepository.removeQuickLink(it.id) }
                        },
                        onHideTopSite = { host ->
                            scope.launch {
                                container.settingsRepository.setHiddenTopSites(settings.hiddenTopSites + host)
                            }
                        },
                    )
                }
                tab.errorMessage != null -> ErrorView(
                    tab = tab,
                    onRetry = { tabManager.reload(tab) },
                    onGoHome = { tabManager.goHome(tab) },
                )
                else -> Box(Modifier.fillMaxSize()) {
                    WebViewHost(tab = tab, tabManager = tabManager, modifier = Modifier.fillMaxSize())
                    if (tab.awaitingPaint) {
                        // Opaque cover until the NEW page paints, so the previous
                        // page's frame can never flash through.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        ) { CircularProgressIndicator() }
                    }
                }
            }

            // Suggestion dropdown ---------------------------------------------
            if (editing && suggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .glass(RoundedCornerShape(18.dp), MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primary, alpha = 0.95f),
                ) {
                    items(suggestions) { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { submit(s.url ?: s.text) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Icon(
                                if (s.fromHistory) Icons.Filled.History else Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    s.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                s.url?.let {
                                    Text(
                                        it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Find-in-page bar ------------------------------------------------
            if (findVisible && tab?.webView != null) {
                val wv = tab.webView!!
                LaunchedEffect(findVisible) {
                    wv.setFindListener { active, total, _ ->
                        findMatches = (if (total == 0) 0 else active + 1) to total
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .imePadding()
                        .fillMaxWidth()
                        .padding(12.dp)
                        .glass(RoundedCornerShape(18.dp), MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primary, alpha = 0.95f)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = {
                            findQuery = it
                            wv.findAllAsync(it)
                        },
                        placeholder = { Text("Find in page") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${findMatches.first}/${findMatches.second}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = { wv.findNext(false) }) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
                    }
                    IconButton(onClick = { wv.findNext(true) }) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                    }
                    IconButton(onClick = { closeFind() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close find bar")
                    }
                }
            }

            if (settings.spaceAiButtonEnabled && !editing && !findVisible) {
                SmallFloatingActionButton(
                    onClick = { aiVisible = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "Open SPACE AI",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    // Menu sheet ---------------------------------------------------------------
    if (menuVisible) {
        BrowserMenuSheet(
            tab = tab,
            isBookmarked = bookmarkCount > 0,
            showMediaDownload = settings.ytDlpEnabled,
            onDismiss = { menuVisible = false },
            callbacks = MenuCallbacks(
                onNewTab = { tabManager.newTab() },
                onNewPrivateTab = { tabManager.newTab(isPrivate = true) },
                onReload = { tab?.let { tabManager.reload(it) } },
                onForward = { tab?.webView?.goForward() },
                onHome = { tab?.let { tabManager.goHome(it) } },
                onToggleBookmark = {
                    val t = tab ?: return@MenuCallbacks
                    val url = t.url ?: return@MenuCallbacks
                    scope.launch {
                        if (bookmarkCount > 0) container.browsingRepository.removeBookmark(url)
                        else container.browsingRepository.addBookmark(url, t.title)
                    }
                },
                onAddQuickLink = {
                    val t = tab ?: return@MenuCallbacks
                    val url = t.url ?: return@MenuCallbacks
                    scope.launch {
                        container.browsingRepository.addQuickLink(url, t.title.ifBlank { UrlUtil.prettyHost(url) })
                        container.browserEvents.toast("Added to start page")
                    }
                },
                onFindInPage = { findVisible = true },
                onToggleDesktop = { tab?.let { tabManager.toggleDesktopMode(it) } },
                onShare = {
                    val t = tab ?: return@MenuCallbacks
                    actions.shareText(t.displayTitle, t.url ?: return@MenuCallbacks)
                },
                onPrintPdf = { tab?.let { actions.printPage(it) } },
                onScreenshot = { tab?.let { actions.capturePageAndShare(it) } },
                onDownloadMedia = { mediaVisible = true },
                onAi = { aiVisible = true },
                onLibrary = { onOpenLibrary(LibrarySection.BOOKMARKS) },
                onPasswordGenerator = { passwordGenVisible = true },
                onSettings = onOpenSettings,
                onExit = {
                    tabManager.runExitCleanup()
                    actions.exitApp()
                },
            ),
        )
    }

    if (aiVisible) {
        AiSheet(
            container = container,
            settings = settings,
            tab = tab,
            onDismiss = { aiVisible = false },
            onAction = { act ->
                when (act) {
                    is AiAction.OpenUrl -> {
                        val t = tabManager.activeTab ?: tabManager.newTab()
                        tabManager.load(t, act.url)
                    }
                    is AiAction.SearchWeb -> {
                        val t = tabManager.activeTab ?: tabManager.newTab()
                        tabManager.submitInput(t, act.query)
                    }
                    is AiAction.PlayMedia -> {
                        tabManager.playMediaSearch(act.query) { played ->
                            if (!played) container.browserEvents.toast("Couldn't start that video")
                        }
                    }
                    is AiAction.MediaControl -> {
                        tabManager.runMediaCommand(act.command) { worked ->
                            if (worked) container.browserEvents.toast(act.command.label)
                        }
                    }
                    is AiAction.WebActions -> {
                        tabManager.runWebSequence(act.steps) { result ->
                            result.fold(
                                onSuccess = { container.browserEvents.toast(it) },
                                onFailure = {
                                    container.browserEvents.toast(
                                        it.message ?: "Page action failed",
                                    )
                                },
                            )
                        }
                    }
                    AiAction.NewTab -> tabManager.newTab()
                    AiAction.CloseTab -> tabManager.activeTab?.let(tabManager::close)
                    AiAction.GoBack -> tabManager.activeTab?.webView?.goBack()
                    AiAction.GoForward -> tabManager.activeTab?.webView?.goForward()
                    AiAction.Reload -> tabManager.activeTab?.let(tabManager::reload)
                    is AiAction.SetTheme -> scope.launch {
                        container.settingsRepository.setThemeMode(act.mode)
                    }
                    is AiAction.SetDesktopMode -> {
                        val t = tabManager.activeTab
                        if (t != null) {
                            if (t.isDesktop != act.enabled) tabManager.toggleDesktopMode(t)
                        } else {
                            scope.launch { container.settingsRepository.setDesktopDefault(act.enabled) }
                        }
                    }
                    is AiAction.SetShield -> scope.launch {
                        container.settingsRepository.setAdBlockEnabled(act.enabled)
                    }
                    AiAction.OpenDownloads -> onOpenLibrary(LibrarySection.DOWNLOADS)
                    AiAction.ClosePrivateTabs -> tabManager.closeAll(privateOnly = true)
                }
            },
        )
    }

    if (mediaVisible) {
        MediaDownloadSheet(container = container, tab = tab, onDismiss = { mediaVisible = false })
    }

    if (globalShieldVisible) {
        AlertDialog(
            onDismissRequest = { globalShieldVisible = false },
            title = { Text("SPACE Shield") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (settings.adBlockEnabled) "Shields are ON" else "Shields are OFF",
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = settings.adBlockEnabled,
                            onCheckedChange = { on ->
                                scope.launch { container.settingsRepository.setAdBlockEnabled(on) }
                            },
                        )
                    }
                    Text(
                        "Blocks known ad & tracker hosts (${container.adBlocker.ruleCount} rules) on every site. " +
                            "Applies to the next pages you open; per-site overrides live behind the shield while a page is loaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { globalShieldVisible = false }) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = { globalShieldVisible = false; onOpenSettings() }) { Text("All settings") }
            },
        )
    }

    if (sitePanelVisible && tab != null) {
        val host = UrlUtil.hostOf(tab.url)?.removePrefix("www.")
        val shieldsUp = host != null && host !in settings.adBlockAllowlist
        SitePanelDialog(
            tab = tab,
            shieldsUpForSite = shieldsUp,
            onToggleShields = {
                if (host == null) return@SitePanelDialog
                scope.launch {
                    val next = if (shieldsUp) settings.adBlockAllowlist + host
                    else settings.adBlockAllowlist - host
                    container.settingsRepository.setAdBlockAllowlist(next)
                }
            },
            onCopyUrl = { actions.copyToClipboard("URL", tab.url.orEmpty()) },
            onDismiss = { sitePanelVisible = false },
        )
    }

    if (passwordGenVisible) {
        PasswordGeneratorDialog(
            onCopy = { actions.copyToClipboard("Password", it) },
            onDismiss = { passwordGenVisible = false },
        )
    }
}

@Composable
private fun NoTabsView(onNewTab: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            "No tabs open",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Open a tab when you’re ready to browse.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        Button(onClick = onNewTab) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New tab")
        }
        Spacer(Modifier.weight(1f))
    }
}
