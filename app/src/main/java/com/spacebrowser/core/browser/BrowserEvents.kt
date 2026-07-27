package com.spacebrowser.core.browser

import android.content.Intent
import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.view.View
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class GeoRequest(val origin: String, val callback: GeolocationPermissions.Callback)

/** A download the site triggered, waiting for user confirmation. */
class PendingDownload(
    val url: String,
    val userAgent: String,
    val fileName: String,
    val mimeType: String?,
    val cookies: String?,
    val sizeBytes: Long,
)

class FullscreenRequest(
    val view: View,
    val callback: WebChromeClient.CustomViewCallback,
)

/**
 * Single-consumer event bridge between WebView clients (which run inside the
 * engine) and the Activity/Compose layer (which owns launchers and dialogs).
 */
class BrowserEvents {

    // File chooser -------------------------------------------------------------
    var pendingFileChooser: ValueCallback<Array<Uri>>? = null
    val fileChooserIntent = MutableStateFlow<Intent?>(null)

    fun requestFileChooser(callback: ValueCallback<Array<Uri>>, intent: Intent) {
        pendingFileChooser?.onReceiveValue(null) // cancel a stale one
        pendingFileChooser = callback
        fileChooserIntent.value = intent
    }

    fun resolveFileChooser(uris: Array<Uri>?) {
        pendingFileChooser?.onReceiveValue(uris)
        pendingFileChooser = null
        fileChooserIntent.value = null
    }

    // Site hardware permission (camera / mic) ----------------------------------
    val sitePermissionRequest = MutableStateFlow<PermissionRequest?>(null)

    // Site geolocation ----------------------------------------------------------
    val geoRequest = MutableStateFlow<GeoRequest?>(null)

    // Download confirmation ------------------------------------------------------
    val downloadRequest = MutableStateFlow<PendingDownload?>(null)

    // HTML video fullscreen ------------------------------------------------------
    val fullscreenRequest = MutableStateFlow<FullscreenRequest?>(null)

    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        fullscreenRequest.value?.let { current ->
            if (current.view !== view) {
                runCatching { current.callback.onCustomViewHidden() }
            }
        }
        fullscreenRequest.value = FullscreenRequest(view, callback)
    }

    fun hideFullscreen(notifyWebView: Boolean = true) {
        val current = fullscreenRequest.value ?: return
        fullscreenRequest.value = null
        (current.view.parent as? android.view.ViewGroup)?.removeView(current.view)
        if (notifyWebView) runCatching { current.callback.onCustomViewHidden() }
    }

    // One-shot user messages ----------------------------------------------------
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    fun toast(message: String) { toasts.tryEmit(message) }
}
