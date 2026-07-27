package com.spacebrowser.core.browser

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/** One browser tab. All mutable fields are Compose state, mutated on main. */
class Tab(
    val id: String = UUID.randomUUID().toString(),
    val isPrivate: Boolean = false,
) {
    /** Lazily created; null while the tab shows the start page. */
    var webView: WebView? = null

    /** URL to load once a WebView exists (used when restoring sessions). */
    var pendingUrl: String? = null

    var showHome by mutableStateOf(true)
    var url by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var blockedOnPage by mutableIntStateOf(0)
    var isSecure by mutableStateOf(true)
    var isDesktop by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    /**
     * True from "navigate away from the start page" until the new document's
     * first paint. While set, the UI covers the WebView with an opaque loading
     * surface so the previous page's stale frame never flashes on screen.
     */
    var awaitingPaint by mutableStateOf(false)
    var thumbnail by mutableStateOf<Bitmap?>(null)

    /** Original http URL kept while we try its https upgrade, for fallback. */
    var httpFallbackUrl: String? = null

    val displayTitle: String
        get() = when {
            showHome -> if (isPrivate) "Private" else "New tab"
            title.isNotBlank() -> title
            else -> url ?: ""
        }
}

data class ClosedTab(val url: String, val title: String, val isPrivate: Boolean)
