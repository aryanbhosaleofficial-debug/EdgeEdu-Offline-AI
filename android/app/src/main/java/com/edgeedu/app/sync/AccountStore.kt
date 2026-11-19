package com.edgeedu.app.sync

import android.content.Context

/**
 * The signed-in backup account (email + auth token), stored on-device.
 * User data like bookmarks and notes: it survives logout of the local
 * profile; "Sign out" in Settings clears it (history rows are kept).
 */
class AccountStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("edgeedu_account", Context.MODE_PRIVATE)

    val email: String? get() = prefs.getString("email", null)
    val token: String? get() = prefs.getString("token", null)

    fun save(email: String, token: String) {
        prefs.edit().putString("email", email).putString("token", token).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
