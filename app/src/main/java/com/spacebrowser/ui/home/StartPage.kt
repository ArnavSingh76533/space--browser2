package com.spacebrowser.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebrowser.core.browser.FaviconStore
import com.spacebrowser.core.db.QuickLink
import com.spacebrowser.core.db.TopSite
import com.spacebrowser.core.util.UrlUtil
import com.spacebrowser.ui.components.LetterAvatar
import com.spacebrowser.ui.components.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One tile on the start page: a pinned shortcut or a most-visited site. */
private data class StartTile(
    val key: String,
    val title: String,
    val url: String,
    val host: String?,
    val quickLink: QuickLink?,   // non-null = pinned
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartPage(
    isPrivate: Boolean,
    trackersBlockedTotal: Long,
    quickLinks: List<QuickLink>,
    topSites: List<TopSite>,
    onSearchSubmit: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onRemoveQuickLink: (QuickLink) -> Unit,
    onHideTopSite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }

    var tileMenu by remember { mutableStateOf<StartTile?>(null) }
    var cosmosQuery by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    // Pinned shortcuts first, then the most-visited sites fill the row.
    val tiles = remember(quickLinks, topSites) {
        val pinned = quickLinks.map { link ->
            StartTile(
                key = "q${link.id}",
                title = link.title.ifBlank { UrlUtil.prettyHost(link.url) },
                url = link.url,
                host = UrlUtil.hostOf(link.url)?.removePrefix("www."),
                quickLink = link,
            )
        }
        val pinnedHosts = pinned.mapNotNull { it.host }.toSet()
        val visited = topSites
            .filter { it.host.isNotBlank() && it.host !in pinnedHosts }
            .map { site ->
                StartTile(
                    key = "t${site.host}",
                    title = site.host,
                    url = "https://${site.host}",
                    host = site.host,
                    quickLink = null,
                )
            }
        (pinned + visited.take(5)).take(8) // your 5 most-visited sites, after any pinned links
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = timeFmt.format(now),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = dateFmt.format(now),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "S P A C E",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        if (isPrivate) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .glass(RoundedCornerShape(16.dp), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.secondary)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Filled.VisibilityOff, contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Private tab — no history, no cache. Session cookies are dropped when the last private tab closes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Cosmos owns its text state and focus; it no longer redirects typing
        // to the toolbar address field.
        OutlinedTextField(
            value = cosmosQuery,
            onValueChange = { cosmosQuery = it },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            placeholder = {
                Text(if (isPrivate) "Search privately" else "Search the cosmos")
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    val query = cosmosQuery.trim()
                    if (query.isNotEmpty()) {
                        keyboard?.hide()
                        onSearchSubmit(query)
                    }
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .glass(
                    RoundedCornerShape(26.dp),
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.primary,
                ),
        )

        Spacer(Modifier.height(28.dp))

        if (tiles.isNotEmpty() && !isPrivate) {
            Text(
                "TOP SITES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 10.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((tiles.size + 3) / 4 * 96).dp),
            ) {
                items(tiles, key = { it.key }) { tile ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.combinedClickable(
                            onClick = { onOpenUrl(tile.url) },
                            onLongClick = { tileMenu = tile },
                        ),
                    ) {
                        SiteIcon(host = tile.host, title = tile.title)
                        Text(
                            text = tile.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Privacy stats --------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(20.dp), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary)
                .padding(16.dp),
        ) {
            Icon(
                Icons.Filled.Shield, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "%,d trackers blocked".format(trackersBlockedTotal),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Since you started flying with SPACE",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    tileMenu?.let { tile ->
        val pinned = tile.quickLink
        AlertDialog(
            onDismissRequest = { tileMenu = null },
            title = { Text(if (pinned != null) "Remove shortcut" else "Hide site") },
            text = {
                Text(
                    if (pinned != null) {
                        "Remove \"${tile.title}\" from your start page?"
                    } else {
                        "Hide ${tile.title} from your top sites? It stays in history."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinned != null) onRemoveQuickLink(pinned) else tile.host?.let(onHideTopSite)
                    tileMenu = null
                }) { Text(if (pinned != null) "Remove" else "Hide") }
            },
            dismissButton = { TextButton(onClick = { tileMenu = null }) { Text("Cancel") } },
        )
    }
}

/** Real captured favicon when we have one, letter avatar otherwise. */
@Composable
private fun SiteIcon(host: String?, title: String) {
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(initialValue = null, host) {
        value = host?.let { h -> withContext(Dispatchers.IO) { FaviconStore.load(context, h) } }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .glass(CircleShape, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary),
        )
    } else {
        LetterAvatar(text = title, size = 56.dp)
    }
}
