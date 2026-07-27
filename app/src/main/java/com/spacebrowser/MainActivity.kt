package com.spacebrowser

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.OpenableColumns
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.spacebrowser.core.AppContainer
import com.spacebrowser.core.browser.CookieTransfer
import com.spacebrowser.core.browser.Downloader
import com.spacebrowser.core.browser.Tab
import com.spacebrowser.ui.ActivityActions
import com.spacebrowser.ui.LibrarySection
import com.spacebrowser.ui.Screen
import com.spacebrowser.ui.browser.BrowserScreen
import com.spacebrowser.ui.components.GalaxyBackground
import com.spacebrowser.ui.library.LibraryScreen
import com.spacebrowser.ui.settings.SettingsScreen
import com.spacebrowser.ui.tabs.TabSwitcherScreen
import com.spacebrowser.ui.theme.SpaceTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), ActivityActions {

    private lateinit var container: AppContainer
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var cookieExportLauncher: ActivityResultLauncher<String>
    private lateinit var cookieImportLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var scriptExportLauncher: ActivityResultLauncher<String>
    private lateinit var scriptImportLauncher: ActivityResultLauncher<Array<String>>
    private var pendingCookieExport: String? = null
    private var pendingCookieImport: Pair<String, String>? = null
    private var pendingScriptExport: String? = null
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = (application as SpaceApp).container
        container.tabManager.hostContext = this

        // WebView <input type=file> round-trip.
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            container.browserEvents.resolveFileChooser(uris)
        }

        cookieExportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            writeDocument(uri, pendingCookieExport, "Cookies exported")
            pendingCookieExport = null
        }
        cookieImportLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            val target = pendingCookieImport
            pendingCookieImport = null
            if (uri != null && target != null) importCookies(uri, target.first, target.second)
        }
        scriptExportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            writeDocument(uri, pendingScriptExport, "Extensions exported")
            pendingScriptExport = null
        }
        scriptImportLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) importUserScript(uri)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    container.browserEvents.fileChooserIntent.collect { intent ->
                        if (intent != null) {
                            try {
                                fileChooserLauncher.launch(intent)
                            } catch (_: ActivityNotFoundException) {
                                container.browserEvents.resolveFileChooser(null)
                                container.browserEvents.toast("No app can pick files")
                            }
                        }
                    }
                }
                launch {
                    container.browserEvents.toasts.collect {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        if (savedInstanceState == null) handleIntent(intent)

        setContent { SpaceRoot(container = container, actions = this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let { container.tabManager.newTab(it) }
            Intent.ACTION_WEB_SEARCH ->
                intent.getStringExtra(SearchManager.QUERY)?.takeIf { it.isNotBlank() }?.let { q ->
                    val tab = container.tabManager.newTab()
                    container.tabManager.submitInput(tab, q)
                }
        }
    }

    override fun onStart() {
        super.onStart()
        if (container.tabManager.settings.appLock) container.appLockState.locked = true
        container.tabManager.setAppForeground(true)
    }

    override fun onStop() {
        container.tabManager.activeTab?.let { container.tabManager.captureThumbnail(it) }
        container.tabManager.setAppForeground(false)
        super.onStop()
    }

    override fun onDestroy() {
        if (container.tabManager.hostContext === this) {
            container.browserEvents.resolveSslDecision(false)
            container.browserEvents.hideFullscreen()
            container.tabManager.hostContext = null
        }
        super.onDestroy()
    }

    // ActivityActions -----------------------------------------------------------

    override fun shareText(title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            startActivity(Intent.createChooser(send, "Share"))
        } catch (_: ActivityNotFoundException) {
            container.browserEvents.toast("Nothing can share this")
        }
    }

    override fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        container.browserEvents.toast("$label copied")
    }

    override fun printPage(tab: Tab) {
        val webView = tab.webView ?: run {
            container.browserEvents.toast("Nothing to print")
            return
        }
        try {
            val jobName = "SPACE — ${tab.displayTitle}".take(60)
            val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        } catch (_: Exception) {
            container.browserEvents.toast("Printing isn't available")
        }
    }

    override fun capturePageAndShare(tab: Tab) {
        val webView = tab.webView ?: return
        if (webView.width <= 0 || webView.height <= 0) {
            container.browserEvents.toast("Nothing to capture")
            return
        }
        try {
            val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            webView.draw(Canvas(bitmap))
            val dir = File(cacheDir, "captures").apply { mkdirs() }
            val file = File(dir, "space-page-${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Screenshot", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share screenshot"))
        } catch (_: Exception) {
            container.browserEvents.toast("Couldn't capture this page")
        }
    }

    override fun openSystemDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (_: ActivityNotFoundException) {
            container.browserEvents.toast("No downloads app found")
        }
    }

    override fun exportCurrentSiteCookies() {
        val tab = container.tabManager.activeTab
        val url = tab?.url
        if (tab == null || url.isNullOrBlank()) {
            container.browserEvents.toast("Open a website first")
            return
        }
        val uri = Uri.parse(url)
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host
        if (host.isNullOrBlank() || (scheme != "http" && scheme != "https")) {
            container.browserEvents.toast("Open a website first")
            return
        }
        val header = CookieManager.getInstance().getCookie(url).orEmpty()
        if (header.isBlank()) {
            container.browserEvents.toast("This site has no cookies to export")
            return
        }
        val origin = "$scheme://$host" + if (uri.port > 0) ":${uri.port}" else ""
        pendingCookieExport = CookieTransfer.export(origin, header)
        cookieExportLauncher.launch("space-cookies-$host.json")
    }

    override fun importCurrentSiteCookies() {
        val tab = container.tabManager.activeTab
        val url = tab?.url
        val scheme = url?.let(Uri::parse)?.scheme?.lowercase()
        if (tab == null || url.isNullOrBlank() || (scheme != "http" && scheme != "https")) {
            container.browserEvents.toast("Open the destination website first")
            return
        }
        pendingCookieImport = tab.id to url
        cookieImportLauncher.launch(arrayOf("application/json", "text/plain", "text/*"))
    }

    override fun importUserScript() {
        scriptImportLauncher.launch(
            arrayOf("application/javascript", "text/javascript", "text/plain", "application/json"),
        )
    }

    override fun exportUserScripts() {
        pendingScriptExport = container.userScriptManager.exportBundle()
        scriptExportLauncher.launch("space-local-extensions.json")
    }

    private fun writeDocument(uri: Uri?, content: String?, successMessage: String) {
        if (uri == null || content == null) return
        try {
            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(content) }
                ?: error("Couldn't open the selected file")
            container.browserEvents.toast(successMessage)
        } catch (_: Exception) {
            container.browserEvents.toast("Couldn't write that file")
        }
    }

    private fun readDocument(uri: Uri): String {
        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Couldn't open the selected file")
        require(text.toByteArray().size <= 2_000_000) { "The selected file is too large" }
        return text
    }

    private fun importCookies(uri: Uri, tabId: String, url: String) {
        try {
            val cookies = CookieTransfer.import(readDocument(uri))
            require(cookies.isNotEmpty()) { "No cookies were found" }
            val https = url.startsWith("https://", ignoreCase = true)
            val manager = CookieManager.getInstance()
            cookies.forEach { manager.setCookie(url, it.setCookieHeader(https), null) }
            manager.flush()
            container.tabManager.tabs.firstOrNull { it.id == tabId }?.webView?.reload()
            container.browserEvents.toast("${cookies.size} cookies imported into this site")
        } catch (error: Exception) {
            container.browserEvents.toast(error.message?.take(120) ?: "Couldn't import cookies")
        }
    }

    private fun importUserScript(uri: Uri) {
        try {
            val name = contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Local extension"
            val count = container.userScriptManager.importText(readDocument(uri), name).getOrThrow()
            container.tabManager.activeTab?.webView?.reload()
            container.browserEvents.toast("$count local extension${if (count == 1) "" else "s"} imported")
        } catch (error: Exception) {
            container.browserEvents.toast(error.message?.take(120) ?: "Couldn't import extension")
        }
    }

    override fun requestUnlock() {
        if (!container.appLockState.locked || prompting) return
        prompting = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    container.appLockState.locked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }

                override fun onAuthenticationFailed() {
                    // Keep the sheet open; the user can retry.
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("SPACE is locked")
            .setSubtitle("Verify it's you to continue")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Exception) {
            prompting = false
            container.appLockState.locked = false // fail open rather than brick the app
        }
    }

    override fun exitApp() {
        finishAffinity()
    }
}

// Root composition ---------------------------------------------------------------

@Composable
private fun SpaceRoot(container: AppContainer, actions: ActivityActions) {
    val settings by container.settingsRepository.flow
        .collectAsState(initial = container.tabManager.settings)

    // Push every settings change into live tabs and the ad blocker.
    LaunchedEffect(settings) { container.tabManager.applySettings(settings) }

    SpaceTheme(settings) {
        GalaxyBackground(settings) {
            var screen by rememberSaveable { mutableStateOf(Screen.BROWSER) }
            var librarySection by rememberSaveable { mutableStateOf(LibrarySection.BOOKMARKS) }

            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.BROWSER -> BrowserScreen(
                        container = container,
                        settings = settings,
                        actions = actions,
                        onOpenTabs = { screen = Screen.TABS },
                        onOpenLibrary = { section ->
                            librarySection = section
                            screen = Screen.LIBRARY
                        },
                        onOpenSettings = { screen = Screen.SETTINGS },
                    )

                    Screen.TABS -> TabSwitcherScreen(
                        tabManager = container.tabManager,
                        onDone = { screen = Screen.BROWSER },
                    )

                    Screen.LIBRARY -> LibraryScreen(
                        container = container,
                        initialSection = librarySection,
                        onOpenUrl = { url ->
                            val tab = container.tabManager.activeTab ?: container.tabManager.newTab()
                            container.tabManager.load(tab, url)
                            screen = Screen.BROWSER
                        },
                        onBack = { screen = Screen.BROWSER },
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        container = container,
                        actions = actions,
                        onBack = { screen = Screen.BROWSER },
                    )
                }

                SitePermissionHandler(container)
                GeoPermissionHandler(container)
                DownloadConfirmHandler(container)
                SslWarningHandler(container)
                FullscreenVideoHost(container)

                if (container.appLockState.locked) {
                    LockOverlay(actions)
                }
            }
        }
    }
}

/** Keeps certificate failures visible and requires an explicit one-time override. */
@Composable
private fun SslWarningHandler(container: AppContainer) {
    val request by container.browserEvents.sslWarningRequest.collectAsState()
    val warning = request ?: return
    val host = runCatching { Uri.parse(warning.url).host }.getOrNull() ?: "this site"
    AlertDialog(
        onDismissRequest = { container.browserEvents.resolveSslDecision(false) },
        title = {
            Text(
                "Certificate warning",
                color = MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Text(
                "$host cannot prove that this connection is private.\n\n" +
                    "${warning.reason}\n\n" +
                    "Continuing can expose passwords, messages, and browsing data. " +
                    "SPACE will mark this tab red and will not remember the exception.",
            )
        },
        confirmButton = {
            TextButton(onClick = { container.browserEvents.resolveSslDecision(false) }) {
                Text("Go back")
            }
        },
        dismissButton = {
            TextButton(onClick = { container.browserEvents.resolveSslDecision(true) }) {
                Text("Continue once", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/** Displays the custom HTML media view supplied by WebChromeClient. */
@Composable
private fun FullscreenVideoHost(container: AppContainer) {
    val request by container.browserEvents.fullscreenRequest.collectAsState()
    val current = request ?: return
    val activity = LocalContext.current as? Activity ?: return

    BackHandler { container.browserEvents.hideFullscreen() }

    DisposableEffect(activity, current) {
        val window = activity.window
        val previousOrientation = activity.requestedOrientation
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity.requestedOrientation = previousOrientation
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            (current.view.parent as? ViewGroup)?.removeView(current.view)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { host ->
            if (current.view.parent !== host) {
                (current.view.parent as? ViewGroup)?.removeView(current.view)
                host.removeAllViews()
                host.addView(
                    current.view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
    )
}

/** Confirms a site-triggered download before handing it to Android. */
@Composable
private fun DownloadConfirmHandler(container: AppContainer) {
    val context = LocalContext.current
    val pending by container.browserEvents.downloadRequest.collectAsState()
    val dl = pending ?: return
    AlertDialog(
        onDismissRequest = { container.browserEvents.downloadRequest.value = null },
        title = { Text("Download file?") },
        text = {
            Text(
                buildString {
                    append(dl.fileName)
                    if (dl.sizeBytes > 0) {
                        append("\n")
                        append(android.text.format.Formatter.formatFileSize(context, dl.sizeBytes))
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                container.browserEvents.downloadRequest.value = null
                Downloader.enqueue(
                    context,
                    dl,
                    container.tabManager.settings,
                    container.browserEvents,
                )
            }) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = { container.browserEvents.downloadRequest.value = null }) {
                Text("Cancel")
            }
        },
    )
}

/** Site asked for camera/mic (already gated by the user's global setting). */
@Composable
private fun SitePermissionHandler(container: AppContainer) {
    val context = LocalContext.current
    val request by container.browserEvents.sitePermissionRequest.collectAsState()
    var awaiting by remember { mutableStateOf<PermissionRequest?>(null) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val target = awaiting
        awaiting = null
        if (target != null) {
            if (grants.values.all { it }) {
                try {
                    target.grant(target.resources)
                } catch (_: Exception) {
                }
            } else {
                try {
                    target.deny()
                } catch (_: Exception) {
                }
                container.browserEvents.toast("Permission denied")
            }
        }
        container.browserEvents.sitePermissionRequest.value = null
    }

    val current = request ?: return
    val wantsCamera = current.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
    val wantsMic = current.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
    val what = listOfNotNull(
        "camera".takeIf { wantsCamera },
        "microphone".takeIf { wantsMic },
    ).joinToString(" and ")
    val host = current.origin?.host ?: current.origin?.toString().orEmpty()

    AlertDialog(
        onDismissRequest = {
            try {
                current.deny()
            } catch (_: Exception) {
            }
            container.browserEvents.sitePermissionRequest.value = null
        },
        title = { Text(host.ifBlank { "This site" }) },
        text = { Text("Wants to use your $what.") },
        confirmButton = {
            TextButton(onClick = {
                val needed = buildList {
                    if (wantsCamera) add(Manifest.permission.CAMERA)
                    if (wantsMic) add(Manifest.permission.RECORD_AUDIO)
                }.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (needed.isEmpty()) {
                    try {
                        current.grant(current.resources)
                    } catch (_: Exception) {
                    }
                    container.browserEvents.sitePermissionRequest.value = null
                } else {
                    awaiting = current
                    launcher.launch(needed.toTypedArray())
                }
            }) { Text("Allow") }
        },
        dismissButton = {
            TextButton(onClick = {
                try {
                    current.deny()
                } catch (_: Exception) {
                }
                container.browserEvents.sitePermissionRequest.value = null
            }) { Text("Block") }
        },
    )
}

/** Site asked for geolocation (already gated by the user's global setting). */
@Composable
private fun GeoPermissionHandler(container: AppContainer) {
    val context = LocalContext.current
    val request by container.browserEvents.geoRequest.collectAsState()
    var awaitingOrigin by remember { mutableStateOf<String?>(null) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val origin = awaitingOrigin
        awaitingOrigin = null
        val pending = container.browserEvents.geoRequest.value
        if (origin != null && pending != null) {
            val granted = grants.values.any { it }
            try {
                pending.callback.invoke(origin, granted, false)
            } catch (_: Exception) {
            }
            if (!granted) container.browserEvents.toast("Location denied")
        }
        container.browserEvents.geoRequest.value = null
    }

    val current = request ?: return
    val host = Uri.parse(current.origin).host ?: current.origin

    AlertDialog(
        onDismissRequest = {
            try {
                current.callback.invoke(current.origin, false, false)
            } catch (_: Exception) {
            }
            container.browserEvents.geoRequest.value = null
        },
        title = { Text(host) },
        text = { Text("Wants to know your location.") },
        confirmButton = {
            TextButton(onClick = {
                val fine = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) {
                    try {
                        current.callback.invoke(current.origin, true, false)
                    } catch (_: Exception) {
                    }
                    container.browserEvents.geoRequest.value = null
                } else {
                    awaitingOrigin = current.origin
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
            }) { Text("Allow") }
        },
        dismissButton = {
            TextButton(onClick = {
                try {
                    current.callback.invoke(current.origin, false, false)
                } catch (_: Exception) {
                }
                container.browserEvents.geoRequest.value = null
            }) { Text("Block") }
        },
    )
}

@Composable
private fun LockOverlay(actions: ActivityActions) {
    // Fire the system sheet as soon as the overlay appears.
    LaunchedEffect(Unit) { actions.requestUnlock() }
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = interaction, indication = null) { /* eat touches */ },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text(
                "SPACE is locked",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
            )
            Button(onClick = { actions.requestUnlock() }) { Text("Unlock") }
        }
    }
}
