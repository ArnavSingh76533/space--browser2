package com.spacebrowser.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.spacebrowser.core.browser.Tab
import com.spacebrowser.core.util.UrlUtil
import java.security.SecureRandom

class MenuCallbacks(
    val onNewTab: () -> Unit,
    val onNewPrivateTab: () -> Unit,
    val onReload: () -> Unit,
    val onForward: () -> Unit,
    val onHome: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onAddQuickLink: () -> Unit,
    val onFindInPage: () -> Unit,
    val onToggleDesktop: () -> Unit,
    val onShare: () -> Unit,
    val onPrintPdf: () -> Unit,
    val onScreenshot: () -> Unit,
    val onDownloadMedia: () -> Unit,
    val onAi: () -> Unit,
    val onLibrary: () -> Unit,
    val onPasswordGenerator: () -> Unit,
    val onSettings: () -> Unit,
    val onExit: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    tab: Tab?,
    isBookmarked: Boolean,
    showMediaDownload: Boolean = false,
    onDismiss: () -> Unit,
    callbacks: MenuCallbacks,
) {
    val hasPage = tab != null && !tab.showHome
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            // Quick action row -------------------------------------------------
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                QuickAction(Icons.AutoMirrored.Filled.ArrowForward, "Forward",
                    enabled = tab?.canGoForward == true) { callbacks.onForward(); onDismiss() }
                QuickAction(Icons.Filled.Refresh, "Reload", enabled = hasPage) {
                    callbacks.onReload(); onDismiss()
                }
                QuickAction(
                    if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                    if (isBookmarked) "Bookmarked" else "Bookmark",
                    enabled = hasPage,
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else null,
                ) { callbacks.onToggleBookmark() }
                QuickAction(Icons.Filled.Share, "Share", enabled = hasPage) {
                    callbacks.onShare(); onDismiss()
                }
                QuickAction(Icons.Filled.Home, "Home") { callbacks.onHome(); onDismiss() }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            MenuRow(Icons.Filled.Add, "New tab") { callbacks.onNewTab(); onDismiss() }
            MenuRow(Icons.Filled.VisibilityOff, "New private tab") { callbacks.onNewPrivateTab(); onDismiss() }
            MenuRow(Icons.Filled.Bookmarks, "Library — bookmarks, history, downloads") {
                callbacks.onLibrary(); onDismiss()
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            MenuRow(Icons.Filled.Search, "Find in page", enabled = hasPage) {
                callbacks.onFindInPage(); onDismiss()
            }
            MenuRowSwitch(
                Icons.Filled.DesktopWindows, "Desktop site",
                checked = tab?.isDesktop == true, enabled = tab != null,
            ) { callbacks.onToggleDesktop() }
            MenuRow(Icons.Filled.PushPin, "Add to start page", enabled = hasPage) {
                callbacks.onAddQuickLink(); onDismiss()
            }
            MenuRow(Icons.Filled.Print, "Print / Save as PDF", enabled = hasPage) {
                callbacks.onPrintPdf(); onDismiss()
            }
            MenuRow(Icons.Filled.PhotoCamera, "Capture page screenshot", enabled = hasPage) {
                callbacks.onScreenshot(); onDismiss()
            }
            if (showMediaDownload) {
                MenuRow(Icons.Filled.Movie, "Download media (yt-dlp)", enabled = hasPage) {
                    callbacks.onDownloadMedia(); onDismiss()
                }
            }
            MenuRow(Icons.Filled.AutoAwesome, "AI assistant") { callbacks.onAi(); onDismiss() }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            MenuRow(Icons.Filled.Key, "Password generator") {
                callbacks.onPasswordGenerator(); onDismiss()
            }
            MenuRow(Icons.Filled.Settings, "Settings") { callbacks.onSettings(); onDismiss() }
            MenuRow(Icons.Filled.ExitToApp, "Exit SPACE") { callbacks.onExit() }
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                icon, contentDescription = label,
                tint = tint ?: if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun MenuRowSwitch(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

// Site privacy panel -----------------------------------------------------------

@Composable
fun SitePanelDialog(
    tab: Tab,
    shieldsUpForSite: Boolean,
    onToggleShields: () -> Unit,
    onCopyUrl: () -> Unit,
    onDismiss: () -> Unit,
) {
    val host = UrlUtil.prettyHost(tab.url).ifBlank { "this site" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(host) },
        text = {
            Column {
                Text(
                    if (tab.isSecure) "Connection is encrypted (HTTPS)."
                    else "Connection is NOT encrypted.",
                    color = if (tab.isSecure) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.padding(4.dp))
                Text("${tab.blockedOnPage} trackers and ads blocked on this page.")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text("Shields for $host", modifier = Modifier.weight(1f))
                    Switch(checked = shieldsUpForSite, onCheckedChange = { onToggleShields() })
                }
                if (!shieldsUpForSite) {
                    Text(
                        "Shields are down: nothing is blocked on this site. Reload to apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = { onCopyUrl(); onDismiss() }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy URL")
            }
        },
    )
}

// Password generator -----------------------------------------------------------

private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val DIGITS = "0123456789"
private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?/"

fun generatePassword(length: Int, digits: Boolean, symbols: Boolean): String {
    val pool = buildString {
        append(LOWER); append(UPPER)
        if (digits) append(DIGITS)
        if (symbols) append(SYMBOLS)
    }
    val rnd = SecureRandom()
    return buildString(length) {
        repeat(length) { append(pool[rnd.nextInt(pool.length)]) }
    }
}

@Composable
fun PasswordGeneratorDialog(onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    var length by remember { mutableFloatStateOf(20f) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var password by remember {
        mutableStateOf(generatePassword(20, digits = true, symbols = true))
    }

    fun regenerate() {
        password = generatePassword(length.toInt(), digits, symbols)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password generator") },
        text = {
            Column {
                Text(
                    password,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("Length: ${length.toInt()}", modifier = Modifier.padding(top = 12.dp))
                Slider(
                    value = length,
                    onValueChange = { length = it },
                    onValueChangeFinished = { regenerate() },
                    valueRange = 8f..40f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = digits, onCheckedChange = { digits = it; regenerate() })
                    Text("Digits")
                    Spacer(Modifier.width(16.dp))
                    Checkbox(checked = symbols, onCheckedChange = { symbols = it; regenerate() })
                    Text("Symbols")
                }
                TextButton(onClick = { regenerate() }) { Text("Regenerate") }
            }
        },
        confirmButton = { TextButton(onClick = { onCopy(password) }) { Text("Copy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
