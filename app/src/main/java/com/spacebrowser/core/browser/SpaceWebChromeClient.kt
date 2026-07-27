package com.spacebrowser.core.browser

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View

class SpaceWebChromeClient(
    private val tab: Tab,
    private val deps: TabManagerDeps,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        tab.progress = newProgress
        tab.isLoading = newProgress < 100
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        tab.title = title.orEmpty()
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        if (icon == null || tab.isPrivate) return
        deps.saveFavicon(com.spacebrowser.core.util.UrlUtil.hostOf(tab.url), icon)
    }

    override fun onShowCustomView(
        view: View,
        callback: WebChromeClient.CustomViewCallback,
    ) {
        deps.events.showFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        deps.events.hideFullscreen(notifyWebView = false)
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        // Background/script-created windows are popups by definition. A real
        // user-initiated target=_blank is captured and opened as a normal tab
        // only after Shield gets the destination URL.
        if (!isUserGesture) {
            if (deps.settings().adBlockEnabled) {
                tab.blockedOnPage++
                deps.events.toast("Shield blocked a popup")
            }
            return false
        }
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popup = WebView(view.context)
        var handled = false
        fun open(url: String?) {
            if (handled || url.isNullOrBlank() || url == "about:blank") return
            handled = true
            deps.openPopup(tab, url)
            popup.stopLoading()
            popup.destroy()
        }
        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                child: WebView,
                request: WebResourceRequest,
            ): Boolean {
                open(request.url.toString())
                return true
            }

            override fun onPageStarted(child: WebView, url: String, favicon: Bitmap?) {
                open(url)
            }
        }
        // A malformed popup can remain at about:blank forever. Do not retain an
        // invisible WebView if it never supplies a destination.
        popup.postDelayed(
            {
                if (!handled) {
                    handled = true
                    popup.stopLoading()
                    popup.destroy()
                }
            },
            POPUP_CAPTURE_TIMEOUT_MS,
        )
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        return try {
            deps.events.requestFileChooser(filePathCallback, fileChooserParams.createIntent())
            true
        } catch (_: Exception) {
            filePathCallback.onReceiveValue(null)
            false
        }
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val wantsHardware = request.resources.any {
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }
        if (!wantsHardware || !deps.settings().askCameraMic) {
            // Privacy default: sites cannot even ask for camera/mic (this also
            // covers WebRTC device capture) unless the user opts in.
            request.deny()
            return
        }
        deps.events.sitePermissionRequest.value = request
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        if (deps.events.sitePermissionRequest.value === request) {
            deps.events.sitePermissionRequest.value = null
        }
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        if (!deps.settings().askLocation) {
            callback.invoke(origin, false, false)
            return
        }
        deps.events.geoRequest.value = GeoRequest(origin, callback)
    }

    override fun onGeolocationPermissionsHidePrompt() {
        deps.events.geoRequest.value = null
    }

    private companion object {
        const val POPUP_CAPTURE_TIMEOUT_MS = 5_000L
    }
}
