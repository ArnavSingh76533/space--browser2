package com.spacebrowser.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.spacebrowser.core.adblock.AdBlocker
import com.spacebrowser.core.browser.BrowserEvents
import com.spacebrowser.core.browser.TabManager
import com.spacebrowser.core.db.AppDatabase
import com.spacebrowser.core.db.BrowsingRepository
import com.spacebrowser.core.extensions.UserScriptManager
import com.spacebrowser.core.net.AiClient
import com.spacebrowser.core.net.SuggestionClient
import com.spacebrowser.core.security.SecureStore
import com.spacebrowser.core.settings.SettingsRepository
import kotlinx.coroutines.runBlocking

/** App lock state shared between the Activity and Compose. */
class AppLockState {
    var locked by mutableStateOf(false)
}

/**
 * Composition root. SPACE uses explicit constructor injection through this
 * container instead of a DI framework: the object graph is small, fully
 * visible, adds zero APK size, and keeps cold start fast.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository = SettingsRepository(appContext)
    val secureStore = SecureStore(appContext)
    val database = AppDatabase.build(appContext)
    val browsingRepository = BrowsingRepository(database)
    val adBlocker = AdBlocker(appContext)
    val browserEvents = BrowserEvents()
    val userScriptManager = UserScriptManager(appContext)
    val suggestionClient = SuggestionClient(SuggestionClient.defaultHttp())
    val aiClient = AiClient()
    val appLockState = AppLockState()

    val tabManager: TabManager

    init {
        // DataStore's first read is fast; blocking once at process start keeps
        // every later consumer synchronous and simple.
        val initial = runBlocking { settingsRepository.snapshot() }
        adBlocker.updateUserRules(initial.adBlockCustomRules, initial.adBlockAllowlist)
        tabManager = TabManager(
            appContext = appContext,
            settingsRepo = settingsRepository,
            browsingRepo = browsingRepository,
            adBlocker = adBlocker,
            userScriptManager = userScriptManager,
            events = browserEvents,
            initialSettings = initial,
        )
    }
}
