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

        // Ad click-throughs often navigate the current tab instead of opening a
        // popup. Apply document rules to cross-site main-frame navigations too,
        // while still allowing a URL the user typed into an empty tab.
        val pageUrl = tab.url?.takeUnless { tab.showHome }
        if (request.isForMainFrame && pageUrl != null &&
            deps.settings().adBlockEnabled &&
            adBlocker.shouldBlock(uri.toString(), pageUrl, AdResourceType.DOCUMENT)
        ) {
            tab.blockedOnPage++
            deps.events.toast("Shield blocked an ad redirect")
            return true
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
        // Keep the red certificate marker across redirects/repaints on the same
        // host after an explicit one-time continuation. A different host starts
        // with a clean security state.
        if (UrlUtil.hostOf(tab.certificateErrorUrl) != UrlUtil.hostOf(url)) {
            tab.certificateError = null
            tab.certificateErrorUrl = null
        }
        tab.isLoading = true
        tab.showHome = false
        tab.url = url
        tab.isSecure = url.startsWith("https://")
        tab.blockedOnPage = 0
        deps.events.clearDetectedMedia(tab.id)
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
        deps.scriptsFor(url).forEach { script ->
            val quoted = JSONObject.quote(script.code)
            val name = JSONObject.quote("SPACE extension ${script.name}")
            view.evaluateJavascript(
                """
                (function(){
                  try { (0,eval)($quoted); }
                  catch(error) { console.error($name, error); }
                })()
                """.trimIndent(),
                null,
            )
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
        if (tryHttpFallback(view, error.url)) {
            handler.cancel()
            return
        }

        // A broken third-party ad/resource certificate must never interrupt the
        // whole page with a warning dialog.
        val pageHost = UrlUtil.hostOf(tab.url)
        val errorHost = UrlUtil.hostOf(error.url)
        if (pageHost != null && errorHost != null && pageHost != errorHost) {
            handler.cancel()
            return
        }

        val reason = when (error.primaryError) {
            SslError.SSL_EXPIRED -> "The certificate has expired."
            SslError.SSL_IDMISMATCH -> "The certificate does not match this site."
            SslError.SSL_NOTYETVALID -> "The certificate is not valid yet."
            SslError.SSL_UNTRUSTED -> "The certificate authority is not trusted."
            SslError.SSL_DATE_INVALID -> "The certificate date is invalid."
            else -> "The certificate could not be verified."
        }
        tab.isSecure = false
        tab.certificateError = reason
        tab.certificateErrorUrl = error.url
        deps.events.requestSslDecision(
            SslWarningRequest(
                tab = tab,
                handler = handler,
                url = error.url.orEmpty(),
                reason = reason,
            ),
        )
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
        detectMedia(request.url.toString())
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

    private fun detectMedia(url: String) {
        val clean = url.substringBefore('#').substringBefore('?').lowercase()
        val kind = when {
            clean.endsWith(".m3u8") -> "HLS"
            clean.endsWith(".mpd") -> "DASH"
            clean.endsWith(".mp4") -> "MP4"
            clean.endsWith(".webm") -> "WebM"
            clean.endsWith(".mkv") -> "MKV"
            clean.endsWith(".m4v") -> "M4V"
            clean.endsWith(".mov") -> "MOV"
            else -> null
        } ?: return
        main.post { deps.events.detectMedia(tab.id, url, kind) }
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
        val MEDIA_EXTENSIONS = setOf(
            ".mp4", ".webm", ".m3u8", ".mpd", ".mkv", ".m4v", ".mov",
            ".mp3", ".m4a", ".ogg", ".wav",
        )
        val FONT_EXTENSIONS = setOf(".woff", ".woff2", ".ttf", ".otf")
    }
}
