package com.spacebrowser.ui.media

import android.webkit.CookieManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebrowser.core.AppContainer
import com.spacebrowser.core.browser.Tab
import com.spacebrowser.core.media.MediaDownloader

/**
 * Optional media-download sheet (yt-dlp). Only reachable when the user has
 * enabled the feature in Settings. Downloads are the user's responsibility:
 * the sheet states that site terms may restrict saving media.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDownloadSheet(
    container: AppContainer,
    tab: Tab?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pageUrl = tab?.url
    var selectedUrl by remember(pageUrl) { mutableStateOf(pageUrl) }
    val detectedByTab by container.browserEvents.detectedMedia.collectAsState()
    val detected = tab?.id?.let { detectedByTab[it] }.orEmpty()
    val requestContext = remember(pageUrl, tab?.webView) {
        MediaDownloader.RequestContext(
            userAgent = tab?.webView?.settings?.userAgentString,
            cookies = pageUrl?.let {
                runCatching { CookieManager.getInstance().getCookie(it) }.getOrNull()
            },
            referer = pageUrl,
        )
    }

    var preparing by remember { mutableStateOf(true) }
    var mediaTitle by remember { mutableStateOf<String?>(null) }
    var infoError by remember { mutableStateOf<String?>(null) }
    val jobs by MediaDownloader.jobs.collectAsState()
    var confirmChoice by remember { mutableStateOf<MediaDownloader.Choice?>(null) }

    LaunchedEffect(selectedUrl) {
        preparing = true
        infoError = null
        mediaTitle = null
        val target = selectedUrl
        if (target == null) {
            preparing = false
            infoError = "Open a page with media first."
            return@LaunchedEffect
        }
        val result = MediaDownloader.fetchInfo(context, target, requestContext)
        preparing = false
        result.fold(
            onSuccess = { mediaTitle = it.title ?: "Media" },
            onFailure = { infoError = it.message?.take(240) ?: "No downloadable media found here." },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Media downloader", style = MaterialTheme.typography.titleLarge)
            Text(
                "Uses yt-dlp plus media detected while the page loads. Supports direct video " +
                    "files and HLS/DASH manifests when the site makes them available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )

            if (detected.isNotEmpty()) {
                Text("Detected on this page", style = MaterialTheme.typography.labelLarge)
                detected.forEachIndexed { index, media ->
                    OutlinedButton(
                        onClick = { selectedUrl = media.url },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            "${media.kind} source ${index + 1}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            when {
                preparing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Checking this page… (first use prepares the engine)")
                }

                infoError != null -> Text(
                    infoError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> {
                    Text(
                        mediaTitle.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    MediaDownloader.choices.forEach { choice ->
                        OutlinedButton(
                            onClick = { confirmChoice = choice },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) { Text(choice.label) }
                    }
                    Text(
                        "Saved to your Downloads/SPACE folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            val active = jobs.filter { it.status == MediaDownloader.Status.DOWNLOADING }
            val finished = jobs.filter { it.status != MediaDownloader.Status.DOWNLOADING }
            if (jobs.isNotEmpty()) Spacer(Modifier.height(16.dp))
            active.forEach { job ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(job.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (job.progress in 0f..100f) {
                        LinearProgressIndicator(
                            progress = { job.progress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                        val eta = if (job.etaSeconds > 0) " · ~${job.etaSeconds}s left" else ""
                        Text(
                            "${job.progress.toInt()}%$eta",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    }
                    TextButton(onClick = { MediaDownloader.cancel(job.id) }) { Text("Cancel") }
                }
            }
            finished.forEach { job ->
                Text(
                    "${job.title}: ${job.message ?: job.status.name.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (job.status == MediaDownloader.Status.DONE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
            if (finished.isNotEmpty()) {
                TextButton(onClick = { MediaDownloader.clearFinished() }) { Text("Clear finished") }
            }
        }
    }

    val pendingChoice = confirmChoice
    val downloadUrl = selectedUrl
    if (pendingChoice != null && downloadUrl != null) {
        AlertDialog(
            onDismissRequest = { confirmChoice = null },
            title = { Text("Download this media?") },
            text = { Text("${mediaTitle ?: "This media"} — ${pendingChoice.label}. You confirm you're allowed to save it.") },
            confirmButton = {
                Button(onClick = {
                    confirmChoice = null
                    MediaDownloader.download(
                        context = context,
                        url = downloadUrl,
                        title = mediaTitle.orEmpty(),
                        choice = pendingChoice,
                        requestContext = requestContext,
                        onRefused = { container.browserEvents.toast("One media download at a time") },
                    )
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { confirmChoice = null }) { Text("Cancel") } },
        )
    }
}
