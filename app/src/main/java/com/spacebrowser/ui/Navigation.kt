package com.spacebrowser.ui

import com.spacebrowser.core.browser.Tab

enum class Screen { BROWSER, TABS, LIBRARY, SETTINGS }

enum class LibrarySection { BOOKMARKS, HISTORY, DOWNLOADS }

/** Platform actions that need the Activity (intents, printing, biometrics). */
interface ActivityActions {
    fun shareText(title: String, text: String)
    fun copyToClipboard(label: String, text: String)
    fun printPage(tab: Tab)
    fun capturePageAndShare(tab: Tab)
    fun openSystemDownloads()
    fun requestUnlock()
    fun exitApp()
}
