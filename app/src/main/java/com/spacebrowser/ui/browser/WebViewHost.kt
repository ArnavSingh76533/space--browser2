package com.spacebrowser.ui.browser

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.spacebrowser.core.browser.Tab
import com.spacebrowser.core.browser.TabManager
import com.spacebrowser.core.util.UrlUtil

@Composable
fun WebViewHost(tab: Tab, tabManager: TabManager, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { frame ->
            val wv = tabManager.ensureWebView(tab)
            if (wv.parent !== frame) {
                (wv.parent as? ViewGroup)?.removeView(wv)
                frame.removeAllViews()
                frame.addView(wv)
            }
            // A session-restored tab carries its URL here until a real host
            // (with an Activity context) exists to load it.
            tab.pendingUrl?.let { pending ->
                tab.pendingUrl = null
                wv.loadUrl(pending)
            }
        },
    )
}

@Composable
fun ErrorView(
    tab: Tab,
    onRetry: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(32.dp),
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Text(
            "Lost in space",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = buildString {
                append(tab.errorMessage ?: "This page couldn't be reached.")
                UrlUtil.prettyHost(tab.url).takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onRetry) { Text("Try again") }
        TextButton(onClick = onGoHome) { Text("Return to base") }
    }
}
