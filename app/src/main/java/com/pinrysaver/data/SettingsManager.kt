package com.pinrysaver.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SettingsManager(context: Context) {

    private val prefs: EncryptedSharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    fun isConfigured(): Boolean = getPinryUrl().isNotBlank()
    fun hasToken(): Boolean = getApiToken().isNotBlank()

    fun getPinryUrl(): String = prefs.getString(KEY_PINRY_URL, "").orEmpty()

    fun getApiToken(): String = prefs.getString(KEY_API_TOKEN, "").orEmpty()

    fun getBoardId(): String = prefs.getString(KEY_BOARD_ID, "").orEmpty()

    fun saveSettings(pinryUrl: String, apiToken: String, boardId: String) {
        prefs.edit()
            .putString(KEY_PINRY_URL, pinryUrl)
            .putString(KEY_API_TOKEN, apiToken)
            .putString(KEY_BOARD_ID, boardId)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "pinry_saver_prefs"
        private const val KEY_PINRY_URL = "pinry_url"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_BOARD_ID = "board_id"
    }
}

