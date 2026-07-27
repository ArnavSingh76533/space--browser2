package com.spacebrowser.ui.library

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebrowser.core.AppContainer
import com.spacebrowser.core.browser.Downloader
import com.spacebrowser.core.db.HistoryEntry
import com.spacebrowser.core.util.UrlUtil
import com.spacebrowser.ui.LibrarySection
import com.spacebrowser.ui.components.LetterAvatar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DownloadRow(
    val id: Long,
    val title: String,
    val status: Int,
    val bytes: Long,
    val soFar: Long,
    val sourceUrl: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    initialSection: LibrarySection,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    var section by remember { mutableStateOf(initialSection) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Library", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        TabRow(selectedTabIndex = section.ordinal) {
            LibrarySection.entries.forEach { s ->
                Tab(
                    selected = section == s,
                    onClick = { section = s },
                    text = {
                        Text(
                            when (s) {
                                LibrarySection.BOOKMARKS -> "Bookmarks"
                                LibrarySection.HISTORY -> "History"
                                LibrarySection.DOWNLOADS -> "Downloads"
                            },
                        )
                    },
                )
            }
        }

        when (section) {
            LibrarySection.BOOKMARKS -> BookmarksList(container, onOpenUrl)
            LibrarySection.HISTORY -> HistoryList(container, onOpenUrl)
            LibrarySection.DOWNLOADS -> DownloadsList(container)
        }
    }
}

@Composable
private fun BookmarksList(container: AppContainer, onOpenUrl: (String) -> Unit) {
    val flow = remember { container.browsingRepository.bookmarks() }
    val bookmarks by flow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    if (bookmarks.isEmpty()) {
        EmptyState(Icons.Filled.Star, "No bookmarks yet",
            "Star a page from the menu to keep it here.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(bookmarks, key = { it.id }) { bm ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUrl(bm.url) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                LetterAvatar(text = UrlUtil.prettyHost(bm.url), size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(bm.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(bm.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { scope.launch { container.browsingRepository.removeBookmark(bm) } }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HistoryList(container: AppContainer, onOpenUrl: (String) -> Unit) {
    val flow = remember { container.browsingRepository.recentHistory() }
    val history by flow.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<HistoryEntry>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        results = if (query.isBlank()) null
        else container.browsingRepository.searchHistory(query, 50)
    }

    val shown = results ?: history
    val dayFmt = remember { SimpleDateFormat("EEEE, d MMM", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search history") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                scope.launch { container.browsingRepository.clearHistory() }
            }) { Text("Clear") }
        }
        if (shown.isEmpty()) {
            EmptyState(Icons.Filled.History, "Nothing here",
                "Pages you visit appear in history (never from private tabs).")
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            var lastDay = ""
            shown.forEach { entry ->
                val day = dayFmt.format(Date(entry.visitedAt))
                if (day != lastDay) {
                    lastDay = day
                    item(key = "h-$day-${entry.id}") {
                        Text(
                            day,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                }
                item(key = entry.id) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUrl(entry.url) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            timeFmt.format(Date(entry.visitedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.title.ifBlank { entry.url }, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(entry.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            scope.launch { container.browsingRepository.deleteHistory(entry.id) }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete entry",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsList(container: AppContainer) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val dm = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager }
    var rows by remember { mutableStateOf<List<DownloadRow>>(emptyList()) }
    var speeds by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) } // id -> bytes/sec
    val lastSample = remember { mutableMapOf<Long, Pair<Long, Long>>() } // id -> (bytes, elapsedMs)

    LaunchedEffect(Unit) {
        while (true) {
            val now = android.os.SystemClock.elapsedRealtime()
            val fresh = queryDownloads(context)
            val newSpeeds = speeds.toMutableMap()
            fresh.forEach { r ->
                val prev = lastSample[r.id]
                if (r.status == DownloadManager.STATUS_RUNNING && prev != null) {
                    val dtMs = now - prev.second
                    if (dtMs > 400) {
                        newSpeeds[r.id] = ((r.soFar - prev.first) * 1000 / dtMs).coerceAtLeast(0)
                    }
                } else {
                    newSpeeds.remove(r.id)
                }
                lastSample[r.id] = r.soFar to now
            }
            speeds = newSpeeds
            rows = fresh
            kotlinx.coroutines.delay(1500)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "Downloads run through Android's download manager, so they " +
                    "continue in the background with progress notifications.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (_: Exception) { }
            }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open all")
            }
        }
        if (rows.isEmpty()) {
            EmptyState(Icons.Filled.Download, "No downloads yet",
                "Files you download will be listed here and in your Downloads folder.")
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(rows, key = { it.id }) { row ->
                val active = row.status == DownloadManager.STATUS_RUNNING ||
                    row.status == DownloadManager.STATUS_PENDING ||
                    row.status == DownloadManager.STATUS_PAUSED
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openDownload(context, row.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Download, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                downloadSubtitle(row, speeds[row.id]),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.status == DownloadManager.STATUS_FAILED) {
                                    MaterialTheme.colorScheme.error
                                } else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (active) {
                        if (row.bytes > 0) {
                            LinearProgressIndicator(
                                progress = { (row.soFar.toFloat() / row.bytes).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        if (active) {
                            TextButton(onClick = { dm?.remove(row.id) }) { Text("Cancel") }
                        }
                        if (row.status == DownloadManager.STATUS_FAILED) {
                            if (row.sourceUrl.startsWith("http")) {
                                TextButton(onClick = {
                                    Downloader.retry(
                                        context, row.sourceUrl, row.title,
                                        container.tabManager.settings, container.browserEvents,
                                    )
                                    dm?.remove(row.id)
                                }) { Text("Retry") }
                            }
                            TextButton(onClick = { dm?.remove(row.id) }) { Text("Remove") }
                        }
                        if (row.status == DownloadManager.STATUS_SUCCESSFUL) {
                            TextButton(onClick = { dm?.remove(row.id) }) { Text("Delete") }
                        }
                        if (row.sourceUrl.startsWith("http")) {
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(row.sourceUrl))
                                container.browserEvents.toast("Link copied")
                            }) { Text("Copy URL") }
                        }
                    }
                }
            }
        }
    }
}

/** "Downloading · 42% of 100 MB · 1.2 MB/s · 48s left" style status line. */
private fun downloadSubtitle(row: DownloadRow, speed: Long?): String = buildString {
    append(statusLabel(row.status))
    val running = row.status == DownloadManager.STATUS_RUNNING
    if (row.bytes > 0) {
        if (running || row.status == DownloadManager.STATUS_PAUSED ||
            row.status == DownloadManager.STATUS_PENDING
        ) {
            val pct = (row.soFar * 100 / row.bytes).toInt().coerceIn(0, 100)
            append(" · $pct% of ${formatBytes(row.bytes)}")
        } else {
            append(" · ${formatBytes(row.bytes)}")
        }
    } else if (running && row.soFar > 0) {
        append(" · ${formatBytes(row.soFar)}")
    }
    if (running && speed != null && speed > 0) {
        append(" · ${formatBytes(speed)}/s")
        if (row.bytes > row.soFar) {
            append(" · ${formatEta((row.bytes - row.soFar) / speed)} left")
        }
    }
}

private fun formatEta(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds}s"
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = 32.dp, end = 32.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp))
        Text(body, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
    }
}

private fun queryDownloads(context: Context): List<DownloadRow> {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        ?: return emptyList()
    val list = mutableListOf<DownloadRow>()
    try {
        dm.query(DownloadManager.Query()).use { c ->
            val idIdx = c.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleIdx = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val soFarIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val uriIdx = c.getColumnIndex(DownloadManager.COLUMN_URI)
            while (c.moveToNext() && list.size < 100) {
                list += DownloadRow(
                    id = c.getLong(idIdx),
                    title = c.getString(titleIdx) ?: "Download",
                    status = c.getInt(statusIdx),
                    bytes = c.getLong(bytesIdx),
                    soFar = if (soFarIdx >= 0) c.getLong(soFarIdx) else 0L,
                    sourceUrl = if (uriIdx >= 0) c.getString(uriIdx).orEmpty() else "",
                )
            }
        }
    } catch (_: Exception) { }
    return list
}

private fun openDownload(context: Context, id: Long) {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
    try {
        val uri = dm.getUriForDownloadedFile(id) ?: return
        val mime = dm.getMimeTypeForDownloadedFile(id) ?: "*/*"
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) { }
}

private fun statusLabel(status: Int): String = when (status) {
    DownloadManager.STATUS_SUCCESSFUL -> "Completed"
    DownloadManager.STATUS_RUNNING -> "Downloading"
    DownloadManager.STATUS_PAUSED -> "Paused"
    DownloadManager.STATUS_PENDING -> "Queued"
    DownloadManager.STATUS_FAILED -> "Failed"
    else -> "Unknown"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
