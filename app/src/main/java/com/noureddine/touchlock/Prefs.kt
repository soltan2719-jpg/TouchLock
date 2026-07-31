package com.noureddine.touchlock

import android.content.Context

object Prefs {

    private const val PREF_NAME = "touchlock_prefs"

    // Keys
    private const val KEY_PREMIUM = "premium"
    private const val KEY_SHOW_UNLOCK_BUTTON = "show_unlock_button"
    private const val KEY_ENABLE_DOUBLE_TAP = "enable_double_tap"
    private const val KEY_REQUIRE_PIN = "require_pin"
    private const val KEY_PIN = "pin"
    private const val KEY_AUTO_LOCK_DELAY = "auto_lock_delay"
    private const val KEY_SELECTED_PACKAGES = "selected_packages"
    private const val KEY_SHAKE_THRESHOLD = "shake_threshold"
    private const val KEY_MONITOR_ENABLED = "monitor_enabled"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_INVISIBLE_UNLOCK_BUTTON = "invisible_unlock_button"
    private const val KEY_PERSONALIZED_ADS = "personalized_ads"
    private const val KEY_ADS_DISABLED_UNTIL = "ads_disabled_until"
    private const val KEY_FIRST_TIME_PICK_APPS = "first_time_pick_apps"
    private const val KEY_FIRST_TIME_USER = "first_time_user"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ---------- PREMIUM ----------

    fun isPremium(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PREMIUM, false)
    }

    fun setPremium(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREMIUM, value).apply()
    }

    fun getAdsDisabledUntil(context: Context): Long {
        return prefs(context).getLong(KEY_ADS_DISABLED_UNTIL, 0L)
    }

    fun setAdsDisabledUntil(context: Context, timestamp: Long) {
        prefs(context).edit().putLong(KEY_ADS_DISABLED_UNTIL, timestamp).apply()
    }

    // ---------- SETTINGS ----------

    fun isShowUnlockButton(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_UNLOCK_BUTTON, true)

    fun setShowUnlockButton(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_UNLOCK_BUTTON, value).apply()
    }

    fun getShakeThreshold(context: Context): Float {
        return prefs(context).getFloat(KEY_SHAKE_THRESHOLD, 12.0f)
    }

    fun setShakeThreshold(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_SHAKE_THRESHOLD, value).apply()
    }

    fun isDoubleTapEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLE_DOUBLE_TAP, false)

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

    fun setMonitorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONITOR_ENABLED, enabled).apply()
    }

    fun isMonitorEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_MONITOR_ENABLED, false)
    }

    fun isKeepScreenOn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_SCREEN_ON, false)

    fun setKeepScreenOn(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()

    fun isInvisibleUnlockButton(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INVISIBLE_UNLOCK_BUTTON, false)

    fun setInvisibleUnlockButton(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_INVISIBLE_UNLOCK_BUTTON, value).apply()
    }

    fun isPersonalizedAdsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERSONALIZED_ADS, true)

    fun setPersonalizedAdsEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PERSONALIZED_ADS, value).apply()
    }

    fun isFirstTimePickApps(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FIRST_TIME_PICK_APPS, true)

    fun setFirstTimePickApps(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_FIRST_TIME_PICK_APPS, value).apply()
    }

    fun isFirstTimeUser(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FIRST_TIME_USER, true)

    fun setFirstTimeUser(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_FIRST_TIME_USER, value).apply()
    }
}