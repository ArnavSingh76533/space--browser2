package com.spacebrowser.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED, AUTO }
enum class AiActionMode { ASK, AUTOMATIC }

/** Immutable snapshot of every user preference. */
data class SpaceSettings(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentIndex: Int = 0,
    val dynamicColor: Boolean = false,
    val animatedBackground: Boolean = true,
    val animationLevel: Float = 1f,          // 0.3 .. 1.0 star/nebula intensity
    val webDarkMode: Boolean = true,          // algorithmic darkening of pages
    // Browsing
    val javascriptEnabled: Boolean = true,
    val blockImages: Boolean = false,         // data saver
    val desktopDefault: Boolean = false,      // open new tabs with the desktop user agent
    val searchEngineId: String = "google",
    val customSearchUrl: String = "",
    val searchSuggestions: Boolean = true,
    // Privacy
    val adBlockEnabled: Boolean = true,
    val adBlockCustomRules: Set<String> = emptySet(),
    val adBlockAllowlist: Set<String> = emptySet(),
    val httpsUpgrade: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val safeBrowsing: Boolean = true,
    val uaPrivacyMode: Boolean = false,       // default engine UA maximizes site compatibility
    val askCameraMic: Boolean = false,        // false = auto-deny site hardware access
    val askLocation: Boolean = false,
    val clearHistoryOnExit: Boolean = false,
    val clearCookiesOnExit: Boolean = false,
    val clearCacheOnExit: Boolean = true,
    val historyRetentionDays: Int = 90,       // 0 = forever
    // Security
    val appLock: Boolean = false,
    val developerToolsEnabled: Boolean = false,
    // AI (bring your own OpenAI-compatible endpoint; key lives in SecureStore)
    val aiEndpoint: String = "https://api.openai.com/v1",
    val aiModel: String = "gpt-4o-2024-08-06",
    val aiActionMode: AiActionMode = AiActionMode.ASK,
    val spaceAiButtonEnabled: Boolean = true,
    // Media
    val backgroundPlaybackEnabled: Boolean = true,
    // Downloads
    val downloadWifiOnly: Boolean = false,
    val confirmDownloads: Boolean = false,
    val ytDlpEnabled: Boolean = true,
    // Start page
    val hiddenTopSites: Set<String> = emptySet(),
    // Stats
    val trackersBlockedTotal: Long = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "space_settings")

class SettingsRepository(private val context: Context) {

    private object K {
        val THEME = intPreferencesKey("theme_mode")
        val ACCENT = intPreferencesKey("accent_index")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val ANIM_BG = booleanPreferencesKey("animated_bg")
        val ANIM_LEVEL = floatPreferencesKey("anim_level")
        val WEB_DARK = booleanPreferencesKey("web_dark")
        val JS = booleanPreferencesKey("javascript")
        val BLOCK_IMAGES = booleanPreferencesKey("block_images")
        val ENGINE = stringPreferencesKey("search_engine")
        val CUSTOM_SEARCH = stringPreferencesKey("custom_search_url")
        val SUGGESTIONS = booleanPreferencesKey("search_suggestions")
        val ADBLOCK = booleanPreferencesKey("adblock")
        val ADBLOCK_RULES = stringSetPreferencesKey("adblock_rules")
        val ADBLOCK_ALLOW = stringSetPreferencesKey("adblock_allow")
        val HTTPS = booleanPreferencesKey("https_upgrade")
        val COOKIES_3P = booleanPreferencesKey("block_3p_cookies")
        val SAFE_BROWSING = booleanPreferencesKey("safe_browsing")
        val UA_PRIVACY = booleanPreferencesKey("ua_privacy")
        val ASK_CAM = booleanPreferencesKey("ask_camera_mic")
        val ASK_LOC = booleanPreferencesKey("ask_location")
        val CLEAR_HISTORY = booleanPreferencesKey("clear_history_exit")
        val CLEAR_COOKIES = booleanPreferencesKey("clear_cookies_exit")
        val CLEAR_CACHE = booleanPreferencesKey("clear_cache_exit")
        val RETENTION = intPreferencesKey("history_retention_days")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val DEVELOPER_TOOLS = booleanPreferencesKey("developer_tools")
        val AI_ENDPOINT = stringPreferencesKey("ai_endpoint")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_ACTION_MODE = intPreferencesKey("ai_action_mode")
        val SPACE_AI_BUTTON = booleanPreferencesKey("space_ai_button")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val BLOCKED_TOTAL = longPreferencesKey("trackers_blocked_total")
        val DESKTOP_DEFAULT = booleanPreferencesKey("desktop_default")
        val DL_WIFI_ONLY = booleanPreferencesKey("dl_wifi_only")
        val DL_CONFIRM = booleanPreferencesKey("dl_confirm")
        val YTDLP = booleanPreferencesKey("ytdlp_enabled")
        val HIDDEN_TOP_SITES = stringSetPreferencesKey("hidden_top_sites")
        val SESSION = stringPreferencesKey("session_json")
    }

    val flow: Flow<SpaceSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun snapshot(): SpaceSettings = context.dataStore.data.first().toSettings()

    suspend fun sessionJson(): String = context.dataStore.data.first()[K.SESSION] ?: ""

    private fun Preferences.toSettings() = SpaceSettings(
        themeMode = ThemeMode.entries.getOrElse(this[K.THEME] ?: 0) { ThemeMode.SYSTEM },
        accentIndex = this[K.ACCENT] ?: 0,
        dynamicColor = this[K.DYNAMIC] ?: false,
        animatedBackground = this[K.ANIM_BG] ?: true,
        animationLevel = this[K.ANIM_LEVEL] ?: 1f,
        webDarkMode = this[K.WEB_DARK] ?: true,
        javascriptEnabled = this[K.JS] ?: true,
        blockImages = this[K.BLOCK_IMAGES] ?: false,
        desktopDefault = this[K.DESKTOP_DEFAULT] ?: false,
        searchEngineId = this[K.ENGINE] ?: "google",
        customSearchUrl = this[K.CUSTOM_SEARCH] ?: "",
        searchSuggestions = this[K.SUGGESTIONS] ?: true,
        adBlockEnabled = this[K.ADBLOCK] ?: true,
        adBlockCustomRules = this[K.ADBLOCK_RULES] ?: emptySet(),
        adBlockAllowlist = this[K.ADBLOCK_ALLOW] ?: emptySet(),
        httpsUpgrade = this[K.HTTPS] ?: true,
        blockThirdPartyCookies = this[K.COOKIES_3P] ?: true,
        safeBrowsing = this[K.SAFE_BROWSING] ?: true,
        uaPrivacyMode = this[K.UA_PRIVACY] ?: false,
        askCameraMic = this[K.ASK_CAM] ?: false,
        askLocation = this[K.ASK_LOC] ?: false,
        clearHistoryOnExit = this[K.CLEAR_HISTORY] ?: false,
        clearCookiesOnExit = this[K.CLEAR_COOKIES] ?: false,
        clearCacheOnExit = this[K.CLEAR_CACHE] ?: true,
        historyRetentionDays = this[K.RETENTION] ?: 90,
        appLock = this[K.APP_LOCK] ?: false,
        developerToolsEnabled = this[K.DEVELOPER_TOOLS] ?: false,
        aiEndpoint = this[K.AI_ENDPOINT] ?: "https://api.openai.com/v1",
        aiModel = this[K.AI_MODEL] ?: "gpt-4o-2024-08-06",
        aiActionMode = AiActionMode.entries.getOrElse(this[K.AI_ACTION_MODE] ?: 0) {
            AiActionMode.ASK
        },
        spaceAiButtonEnabled = this[K.SPACE_AI_BUTTON] ?: true,
        backgroundPlaybackEnabled = this[K.BACKGROUND_PLAYBACK] ?: true,
        downloadWifiOnly = this[K.DL_WIFI_ONLY] ?: false,
        confirmDownloads = this[K.DL_CONFIRM] ?: false,
        ytDlpEnabled = this[K.YTDLP] ?: true,
        hiddenTopSites = this[K.HIDDEN_TOP_SITES] ?: emptySet(),
        trackersBlockedTotal = this[K.BLOCKED_TOTAL] ?: 0L,
    )

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    suspend fun setThemeMode(v: ThemeMode) = edit { it[K.THEME] = v.ordinal }
    suspend fun setAccentIndex(v: Int) = edit { it[K.ACCENT] = v }
    suspend fun setDynamicColor(v: Boolean) = edit { it[K.DYNAMIC] = v }
    suspend fun setAnimatedBackground(v: Boolean) = edit { it[K.ANIM_BG] = v }
    suspend fun setAnimationLevel(v: Float) = edit { it[K.ANIM_LEVEL] = v }
    suspend fun setWebDarkMode(v: Boolean) = edit { it[K.WEB_DARK] = v }
    suspend fun setJavascriptEnabled(v: Boolean) = edit { it[K.JS] = v }
    suspend fun setBlockImages(v: Boolean) = edit { it[K.BLOCK_IMAGES] = v }
    suspend fun setDesktopDefault(v: Boolean) = edit { it[K.DESKTOP_DEFAULT] = v }
    suspend fun setDownloadWifiOnly(v: Boolean) = edit { it[K.DL_WIFI_ONLY] = v }
    suspend fun setConfirmDownloads(v: Boolean) = edit { it[K.DL_CONFIRM] = v }
    suspend fun setYtDlpEnabled(v: Boolean) = edit { it[K.YTDLP] = v }
    suspend fun setHiddenTopSites(v: Set<String>) = edit { it[K.HIDDEN_TOP_SITES] = v }
    suspend fun setSearchEngineId(v: String) = edit { it[K.ENGINE] = v }
    suspend fun setCustomSearchUrl(v: String) = edit { it[K.CUSTOM_SEARCH] = v }
    suspend fun setSearchSuggestions(v: Boolean) = edit { it[K.SUGGESTIONS] = v }
    suspend fun setAdBlockEnabled(v: Boolean) = edit { it[K.ADBLOCK] = v }
    suspend fun setAdBlockCustomRules(v: Set<String>) = edit { it[K.ADBLOCK_RULES] = v }
    suspend fun setAdBlockAllowlist(v: Set<String>) = edit { it[K.ADBLOCK_ALLOW] = v }
    suspend fun setHttpsUpgrade(v: Boolean) = edit { it[K.HTTPS] = v }
    suspend fun setBlockThirdPartyCookies(v: Boolean) = edit { it[K.COOKIES_3P] = v }
    suspend fun setSafeBrowsing(v: Boolean) = edit { it[K.SAFE_BROWSING] = v }
    suspend fun setUaPrivacyMode(v: Boolean) = edit { it[K.UA_PRIVACY] = v }
    suspend fun setAskCameraMic(v: Boolean) = edit { it[K.ASK_CAM] = v }
    suspend fun setAskLocation(v: Boolean) = edit { it[K.ASK_LOC] = v }
    suspend fun setClearHistoryOnExit(v: Boolean) = edit { it[K.CLEAR_HISTORY] = v }
    suspend fun setClearCookiesOnExit(v: Boolean) = edit { it[K.CLEAR_COOKIES] = v }
    suspend fun setClearCacheOnExit(v: Boolean) = edit { it[K.CLEAR_CACHE] = v }
    suspend fun setHistoryRetentionDays(v: Int) = edit { it[K.RETENTION] = v }
    suspend fun setAppLock(v: Boolean) = edit { it[K.APP_LOCK] = v }
    suspend fun setDeveloperToolsEnabled(v: Boolean) = edit { it[K.DEVELOPER_TOOLS] = v }
    suspend fun setAiEndpoint(v: String) = edit { it[K.AI_ENDPOINT] = v.trim() }
    suspend fun setAiModel(v: String) = edit { it[K.AI_MODEL] = v.trim() }
    suspend fun setAiActionMode(v: AiActionMode) = edit { it[K.AI_ACTION_MODE] = v.ordinal }
    suspend fun setSpaceAiButtonEnabled(v: Boolean) = edit { it[K.SPACE_AI_BUTTON] = v }
    suspend fun setBackgroundPlaybackEnabled(v: Boolean) = edit { it[K.BACKGROUND_PLAYBACK] = v }
    suspend fun addTrackersBlocked(delta: Long) {
        if (delta > 0) edit { it[K.BLOCKED_TOTAL] = (it[K.BLOCKED_TOTAL] ?: 0L) + delta }
    }

    suspend fun saveSession(json: String) = edit { it[K.SESSION] = json }
}
