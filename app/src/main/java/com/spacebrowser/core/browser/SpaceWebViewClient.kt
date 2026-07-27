package com.spacebrowser.core.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.spacebrowser.core.adblock.AdBlocker
import com.spacebrowser.core.adblock.AdResourceType
import com.spacebrowser.core.util.UrlUtil
import org.json.JSONObject

class SpaceWebViewClient(
    private val tab: Tab,
    private val adBlocker: AdBlocker,
    private val deps: TabManagerDeps,
) : WebViewClient() {

    private val main = Handler(Looper.getMainLooper())

    // Navigation ----------------------------------------------------------------

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        val scheme = uri.scheme?.lowercase()

        // Hand non-web schemes (mailto:, tel:, intent:, market:, ...) to the OS.
        if (scheme != "http" && scheme != "https") {
            if (!request.isForMainFrame) return true
            return try {
                val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                view.context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                deps.events.toast("No app can open ${scheme ?: "this"} links")
                true
            } catch (_: Exception) {
                true
            }
        }

        // HTTPS upgrade: try the https version first, remember the original for
        // a one-shot fallback if the secure host doesn't answer.
        if (scheme == "http" && request.isForMainFrame &&
            deps.settings().httpsUpgrade &&
            uri.host !in deps.httpAllowedHosts
        ) {
            tab.httpFallbackUrl = uri.toString()
            view.loadUrl(uri.buildUpon().scheme("https").build().toString())
            return true
        }
        return false
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        tab.errorMessage = null
        tab.isLoading = true
        tab.showHome = false
        tab.url = url
        tab.isSecure = url.startsWith("https://")
        tab.blockedOnPage = 0
        deps.onNavigated(tab)
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        // First paint of the new document: safe to reveal the engine.
        tab.awaitingPaint = false
    }

    override fun onPageFinished(view: WebView, url: String) {
        tab.awaitingPaint = false
        tab.isLoading = false
        tab.progress = 100
        tab.url = url
        tab.title = view.title.orEmpty()
        tab.canGoBack = view.canGoBack()
        tab.canGoForward = view.canGoForward()
        tab.httpFallbackUrl = null
        if (!tab.isPrivate) deps.recordVisit(url, view.title.orEmpty())
        if (deps.settings().adBlockEnabled) {
            val css = adBlocker.cosmeticCss(url)
            if (css.isNotBlank()) {
                val quoted = JSONObject.quote(css)
                view.evaluateJavascript(
                    """
                    (function(){
                      let style=document.getElementById('space-shield-cosmetic');
                      if(!style){style=document.createElement('style');style.id='space-shield-cosmetic';
                        (document.head||document.documentElement).appendChild(style);}
                      style.textContent=$quoted;
                    })()
                    """.trimIndent(),
                    null,
                )
            }
        }
        deps.onNavigated(tab)
    }

    // Errors --------------------------------------------------------------------

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        if (tryHttpFallback(view, request.url.toString())) return
        tab.isLoading = false
        tab.awaitingPaint = false
        tab.errorMessage = error.description?.toString().takeUnless { it.isNullOrBlank() }
            ?: "Couldn't reach this site"
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // Secure by default: never proceed past a broken certificate.
        handler.cancel()
        if (tryHttpFallback(view, error.url)) return
        tab.isLoading = false
        tab.awaitingPaint = false
        tab.errorMessage = "Connection blocked: this site's security certificate is not trusted."
    }

    /** If [failingUrl] is the https upgrade we attempted, retry plain http once. */
    private fun tryHttpFallback(view: WebView, failingUrl: String?): Boolean {
        val original = tab.httpFallbackUrl ?: return false
        val upgraded = Uri.parse(original).buildUpon().scheme("https").build().toString()
        if (failingUrl == null || failingUrl != upgraded) return false
        tab.httpFallbackUrl = null
        UrlUtil.hostOf(original)?.let { deps.httpAllowedHosts.add(it) }
        main.post { view.loadUrl(original) }
        return true
    }

    // Content blocking ----------------------------------------------------------

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val s = deps.settings()
        if (!s.adBlockEnabled) return null
        if (request.isForMainFrame) return null
        if (adBlocker.shouldBlock(
                requestUrl = request.url.toString(),
                pageUrl = tab.url,
                type = inferResourceType(request),
            )
        ) {
            main.post { tab.blockedOnPage++ }
            return WebResourceResponse("text/plain", "utf-8", AdBlocker.emptyStream())
        }
        return null
    }

    private fun inferResourceType(request: WebResourceRequest): AdResourceType {
        if (request.isForMainFrame) return AdResourceType.DOCUMENT
        val url = request.url.toString().lowercase()
        val accept = request.requestHeaders.entries
            .firstOrNull { it.key.equals("accept", ignoreCase = true) }
            ?.value
            ?.lowercase()
            .orEmpty()
        return when {
            accept.contains("text/css") || url.substringBefore('?').endsWith(".css") ->
                AdResourceType.STYLESHEET
            accept.contains("javascript") ||
                url.substringBefore('?').endsWith(".js") ||
                url.substringBefore('?').endsWith(".mjs") -> AdResourceType.SCRIPT
            accept.startsWith("image/") ||
                IMAGE_EXTENSIONS.any { url.substringBefore('?').endsWith(it) } ->
                AdResourceType.IMAGE
            accept.startsWith("video/") || accept.startsWith("audio/") ||
                MEDIA_EXTENSIONS.any { url.substringBefore('?').endsWith(it) } ->
                AdResourceType.MEDIA
            accept.contains("font/") ||
                FONT_EXTENSIONS.any { url.substringBefore('?').endsWith(it) } ->
                AdResourceType.FONT
            accept.contains("application/json") ||
                request.requestHeaders.keys.any { it.equals("x-requested-with", true) } ->
                AdResourceType.XHR
            else -> AdResourceType.OTHER
        }
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".avif")
        val MEDIA_EXTENSIONS = setOf(".mp4", ".webm", ".m3u8", ".mp3", ".m4a", ".ogg", ".wav")
        val FONT_EXTENSIONS = setOf(".woff", ".woff2", ".ttf", ".otf")
    }
}
