package com.spacebrowser.core.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.spacebrowser.core.settings.SpaceSettings

/** Sends a confirmed/allowed download to Android's DownloadManager. */
object Downloader {

    fun enqueue(context: Context, pending: PendingDownload, s: SpaceSettings, events: BrowserEvents) {
        try {
            val request = DownloadManager.Request(Uri.parse(pending.url)).apply {
                pending.mimeType?.let { setMimeType(it) }
                setTitle(pending.fileName)
                setDescription(Uri.parse(pending.url).host ?: "SPACE download")
                addRequestHeader("User-Agent", pending.userAgent)
                pending.cookies?.let { addRequestHeader("Cookie", it) }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, pending.fileName)
                if (s.downloadWifiOnly) {
                    setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                    setAllowedOverMetered(false)
                    setAllowedOverRoaming(false)
                } else {
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            events.toast("Downloading ${pending.fileName}")
        } catch (_: Exception) {
            events.toast("Couldn't start that download")
        }
    }

    /** Re-enqueue a finished/failed entry by its original URL. */
    fun retry(context: Context, url: String, fileName: String, s: SpaceSettings, events: BrowserEvents) {
        enqueue(
            context,
            PendingDownload(
                url = url,
                userAgent = WebViewFactory.PRIVACY_UA,
                fileName = fileName,
                mimeType = null,
                cookies = try {
                    android.webkit.CookieManager.getInstance().getCookie(url)
                } catch (_: Exception) {
                    null
                },
                sizeBytes = -1,
            ),
            s,
            events,
        )
    }
}
