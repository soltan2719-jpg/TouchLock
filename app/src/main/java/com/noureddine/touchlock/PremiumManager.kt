package com.noureddine.touchlock

import android.content.Context
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Central manager for Premium status.
 * Handles both permanent (purchased) and temporary (rewarded ad) premium.
 */
object PremiumManager {

    /**
     * Checks if the user has purchased permanent premium.
     */
    fun isPermanentPremium(context: Context): Boolean {
        return Prefs.isPremium(context)
    }

    /**
     * Sets the permanent premium status.
     */
    fun setPermanentPremium(context: Context, value: Boolean) {
        Prefs.setPremium(context, value)
    }

    /**
     * Checks if the temporary premium from a rewarded ad is currently active.
     */
    fun isTemporaryPremiumActive(context: Context): Boolean {
        val endTime = getTemporaryPremiumEndTime(context)
        return System.currentTimeMillis() < endTime
    }

    /**
     * Grants temporary premium for a specific duration.
     */
    fun grantTemporaryPremium(context: Context, durationMillis: Long) {
        val newEndTime = System.currentTimeMillis() + durationMillis
        Prefs.setAdsDisabledUntil(context, newEndTime)
    }

    /**
     * Returns the timestamp when temporary premium expires.
     */
    fun getTemporaryPremiumEndTime(context: Context): Long {
        return Prefs.getAdsDisabledUntil(context)
    }

    /**
     * Checks if temporary premium has expired and resets it if necessary.
     * Also handles resetting premium-only features if they were active.
     */
    fun clearExpiredTemporaryPremium(context: Context) {
        if (!isPermanentPremium(context) && !isTemporaryPremiumActive(context)) {
            // Temporary premium expired or not active, and no permanent premium.
            // Reset premium-only settings to ensure safe fallback.
            if (Prefs.isInvisibleUnlockButton(context) || 
                Prefs.isPinRequired(context) || 
                Prefs.isKeepScreenOn(context)) {
                
                Prefs.setInvisibleUnlockButton(context, false)
                Prefs.setPinRequired(context, false)
                Prefs.setKeepScreenOn(context, false)
            }
        }
    }

    /**
     * Returns true if the user has ANY kind of premium active.
     */
    fun isPremiumUser(context: Context): Boolean {
        return isPermanentPremium(context) || isTemporaryPremiumActive(context)
    }

    /**
     * Formats the remaining temporary premium time into a human-readable string.
     * Examples: "Premium active • 1h 56m left", "Free Version"
     */
    fun getRemainingPremiumTimeText(context: Context): String {
        // If user has permanent premium, show "Premium Active"
        if (isPermanentPremium(context)) return "Premium Active"
        
        // Check temporary premium
        val remainingMillis = getTemporaryPremiumEndTime(context) - System.currentTimeMillis()
        
        // If expired or not active, show "Free Version"
        if (remainingMillis <= 0) return "Free Version"

        // Format the time remaining
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60

        val timeText = if (hours > 0) {
            String.format(Locale.getDefault(), "%dh %02dm", hours, minutes)
        } else {
            String.format(Locale.getDefault(), "%dm", minutes)
        }
        
        return "Premium active • $timeText left"
    }
}
