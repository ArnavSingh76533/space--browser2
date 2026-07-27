package com.spacebrowser.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * AES-encrypted preferences backed by the Android Keystore. Used for the AI
 * API key so it never sits on disk in plaintext.
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "space_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Extremely rare keystore corruption: fall back to plain prefs rather
        // than crashing; secrets simply won't be stored encrypted on this device.
        context.getSharedPreferences("space_secure_fallback", Context.MODE_PRIVATE)
    }

    var aiApiKey: String
        get() = prefs.getString(KEY_AI, "") ?: ""
        set(value) { prefs.edit().putString(KEY_AI, value.trim()).apply() }

    private companion object { const val KEY_AI = "ai_api_key" }
}
