package com.spacebrowser.core.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.spacebrowser.core.settings.SpaceSettings

/**
 * Creates and (re)configures WebViews. Android's WebView is Chromium-based;
 * SPACE layers privacy defaults on top of it.
 */
class WebViewFactory(
    private val appContext: Context,
    private val events: BrowserEvents,
    private val currentSettings: () -> SpaceSettings,
) {

    /** The engine's default UA, captured once so it can be restored. */
    private val defaultUa: String by lazy { WebSettings.getDefaultUserAgent(appContext) }
    private val privacyUa: String by lazy {
        defaultUa.replace(
            Regex("""\(Linux; Android[^)]*\)"""),
            "(Linux; Android 10; K)",
        )
    }
    private val desktopUa: String by lazy {
        defaultUa
            .replace(Regex("""\(Linux; Android[^)]*\)"""), "(X11; Linux x86_64)")
            .replace(" Mobile Safari/", " Safari/")
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        tab: Tab,
        settings: SpaceSettings,
        webViewClient: SpaceWebViewClient,
        chromeClient: SpaceWebChromeClient,
    ): WebView {
        val wv = WebView(context)
        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        wv.webViewClient = webViewClient
        wv.webChromeClient = chromeClient
        wv.isVerticalScrollBarEnabled = true

        with(wv.settings) {
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            // WebChromeClient captures target=_blank destinations so Shield can
            // reject ad popups and route legitimate links into real tabs.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            cacheMode = if (tab.isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            if (tab.isPrivate) {
                saveFormData = false
            }
        }

        applyDynamic(wv, tab, settings)
        installDownloadListener(wv, tab)
        return wv
    }

    /** Settings that may change at runtime; safe to re-apply to live WebViews. */
    fun applyDynamic(wv: WebView, tab: Tab, s: SpaceSettings) {
        with(wv.settings) {
            javaScriptEnabled = s.javascriptEnabled
            loadsImagesAutomatically = !s.blockImages
            blockNetworkImage = s.blockImages
            userAgentString = userAgentFor(tab, s)
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, !s.blockThirdPartyCookies && !tab.isPrivate)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(wv.settings, s.safeBrowsing)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, s.webDarkMode)
        }
    }

    fun userAgentFor(tab: Tab, s: SpaceSettings): String = when {
        tab.isDesktop -> desktopUa
        s.uaPrivacyMode -> privacyUa
        else -> defaultUa
    }

    private fun installDownloadListener(wv: WebView, tab: Tab) {
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                // Blob/data downloads need a JS bridge; on the roadmap.
                return@setDownloadListener
            }
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val pending = PendingDownload(
                url = url,
                userAgent = userAgent,
                fileName = fileName,
                mimeType = mimeType,
                cookies = try { CookieManager.getInstance().getCookie(url) } catch (_: Exception) { null },
                sizeBytes = contentLength,
            )
            val s = currentSettings()
            if (s.confirmDownloads) {
                events.downloadRequest.value = pending
            } else {
                Downloader.enqueue(appContext, pending, s, events)
            }
        }
    }

    companion object {
        /**
         * Reduced, Chrome-style generic UA (frozen model "K", stable versions):
         * every SPACE user in privacy mode presents the same string, shrinking
         * the UA's fingerprinting value.
         */
        const val PRIVACY_UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/140.0.0.0 Mobile Safari/537.36"

        const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/140.0.0.0 Safari/537.36"
    }
}
