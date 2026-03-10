package com.example.touchlock

import android.content.Context

object Prefs {

    private const val PREF_NAME = "touchlock_prefs"

    private const val KEY_PREMIUM = "premium"

    private const val KEY_SHOW_UNLOCK_BUTTON = "show_unlock_button"
    private const val KEY_ENABLE_DOUBLE_TAP = "enable_double_tap"
    private const val KEY_REQUIRE_PIN = "require_pin"
    private const val KEY_PIN = "pin"
    private const val KEY_AUTO_LOCK_DELAY = "auto_lock_delay"
    private const val KEY_SELECTED_PACKAGES = "selected_packages"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ---------- PREMIUM ----------

    fun isPremium(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PREMIUM, false)
    }

    fun setPremium(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREMIUM, value).apply()
    }

    // ---------- NORMAL SETTINGS ----------

    fun isShowUnlockButton(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_UNLOCK_BUTTON, true)

    fun setShowUnlockButton(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_UNLOCK_BUTTON, value).apply()
    }

    fun isDoubleTapEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLE_DOUBLE_TAP, true)

    fun setDoubleTapEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLE_DOUBLE_TAP, value).apply()
    }

    fun isPinRequired(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_PIN, false)

    fun setPinRequired(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_PIN, value).apply()
    }

    fun getPin(context: Context): String =
        prefs(context).getString(KEY_PIN, "") ?: ""

    fun setPin(context: Context, value: String) {
        prefs(context).edit().putString(KEY_PIN, value).apply()
    }

    fun getAutoLockDelay(context: Context): Int =
        prefs(context).getInt(KEY_AUTO_LOCK_DELAY, 0)

    fun setAutoLockDelay(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_AUTO_LOCK_DELAY, value).apply()
    }

    fun getSelectedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SELECTED_PACKAGES, emptySet()) ?: emptySet()

    fun setSelectedPackages(context: Context, values: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SELECTED_PACKAGES, values).apply()
    }
}