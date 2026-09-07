package com.olivia.companion

import android.content.Context

class WelcomePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSeen(): Boolean = prefs.getBoolean(KEY_SEEN, false)

    fun markSeen() {
        prefs.edit().putBoolean(KEY_SEEN, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "olivia_welcome"
        private const val KEY_SEEN = "welcome_seen"
    }
}
