package com.example.data.config

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("washpulse_config", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_API_BASE_URL = "https://washer-u2y9.onrender.com"
        const val DEFAULT_WS_URL = "wss://washer-u2y9.onrender.com/ws"
        const val KEY_API_URL = "key_api_url"
        const val KEY_WS_URL = "key_ws_url"
        const val KEY_AUTH_TOKEN = "key_auth_token"
        const val KEY_USER_EMAIL = "key_user_email"
        const val KEY_USER_NAME = "key_user_name"
        const val KEY_NOTIF_ENABLED = "key_notif_enabled"
        const val DEFAULT_AUTH_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjMmMzNmNiYS0zNzhiLTQ2ZDAtYmQwNS05NGFkZDljNTgxZWIiLCJleHAiOjE3ODk3MzI3MzUsInR5cGUiOiJhY2Nlc3MifQ.79N3zGkNrURzCy2v9R2dNYKrqhrGDNxltsFcEQCzcq0"
    }

    var apiBaseUrl: String
        get() = prefs.getString(KEY_API_URL, DEFAULT_API_BASE_URL) ?: DEFAULT_API_BASE_URL
        set(value) {
            prefs.edit().putString(KEY_API_URL, value.trimEnd('/')).apply()
        }

    var webSocketUrl: String
        get() = prefs.getString(KEY_WS_URL, DEFAULT_WS_URL) ?: DEFAULT_WS_URL
        set(value) {
            prefs.edit().putString(KEY_WS_URL, value.trimEnd('/')).apply()
        }

    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, DEFAULT_AUTH_TOKEN)
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_AUTH_TOKEN).apply()
            } else {
                prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()
            }
        }

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, "test@washpulse.io")
        set(value) {
            prefs.edit().putString(KEY_USER_EMAIL, value).apply()
        }

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, "Alex Jordan")
        set(value) {
            prefs.edit().putString(KEY_USER_NAME, value).apply()
        }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIF_ENABLED, value).apply()
        }
}
