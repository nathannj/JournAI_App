package com.journai.journai.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePrefs(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getJwt(): String? = prefs.getString(KEY_JWT, null)
    fun setJwt(value: String?) {
        prefs.edit().apply {
            if (value == null) remove(KEY_JWT) else putString(KEY_JWT, value)
        }.apply()
    }

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)
    fun setDeviceId(value: String?) {
        prefs.edit().apply {
            if (value == null) remove(KEY_DEVICE_ID) else putString(KEY_DEVICE_ID, value)
        }.apply()
    }
    
    // Generic methods for settings
    fun getBoolean(key: String, defaultValue: Boolean): Boolean = 
        prefs.getBoolean(key, defaultValue)
    
    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
    
    fun getString(key: String, defaultValue: String): String = 
        prefs.getString(key, defaultValue) ?: defaultValue
    
    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_JWT = "jwt_token"
        private const val KEY_DEVICE_ID = "device_id"
    }
}


