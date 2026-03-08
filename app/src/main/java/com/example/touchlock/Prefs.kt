package com.example.touchlock

import android.content.Context

object Prefs {
    private const val PREF_NAME = "touchlock_prefs"

    private const val KEY_SHOW_UNLOCK_BUTTON = "show_unlock_button"
    private const val KEY_ENABLE_DOUBLE_TAP = "enable_double_tap"
    private const val KEY_REQUIRE_PIN = "require_pin"
    private const val KEY_PIN = "pin"
    private const val KEY_AUTO_LOCK_DELAY = "auto_lock_delay"

    fun isShowUnlockButton(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_UNLOCK_BUTTON, true)
    }

    fun setShowUnlockButton(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_UNLOCK_BUTTON, value)
            .apply()
    }

    fun isDoubleTapEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLE_DOUBLE_TAP, true)
    }

    fun setDoubleTapEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLE_DOUBLE_TAP, value)
            .apply()
    }

    fun isPinRequired(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REQUIRE_PIN, false)
    }

    fun setPinRequired(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REQUIRE_PIN, value)
            .apply()
    }

    fun getPin(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PIN, "") ?: ""
    }

    fun setPin(context: Context, value: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PIN, value)
            .apply()
    }

    fun getAutoLockDelay(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_AUTO_LOCK_DELAY, 0)
    }

    fun setAutoLockDelay(context: Context, value: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_AUTO_LOCK_DELAY, value)
            .apply()
    }
}