package com.noureddine.touchlock

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Manages loading and showing rewarded ads to grant temporary premium.
 */
class RewardedAdManager(private val activity: Activity) {

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    // Using the Test Ad ID for development. Replace with your real ID for production.
    private val adUnitId = "ca-app-pub-3940256099942544/5224354917"

    /**
     * Loads a rewarded ad if one isn't already loaded.
     */
    fun loadAd() {
        if (rewardedAd != null || isLoading) return

        isLoading = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(activity, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("RewardedAdManager", "Ad failed to load: ${adError.message}")
                rewardedAd = null
                isLoading = false
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d("RewardedAdManager", "Ad loaded successfully.")
                rewardedAd = ad
                isLoading = false
            }
        })
    }

    /**
     * Shows the rewarded ad and grants premium if the user completes it.
     */
    fun showAd(onRewardEarned: () -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.show(activity) { _ ->
                // User earned the reward!
                // Grant 2 hours of premium (2 * 60 * 60 * 1000 milliseconds)
                val twoHoursInMillis = 2 * 60 * 60 * 1000L
                PremiumManager.grantTemporaryPremium(activity, twoHoursInMillis)
                
                Toast.makeText(activity, activity.getString(R.string.premium_unlocked_success), Toast.LENGTH_LONG).show()
                
                rewardedAd = null
                onRewardEarned()
                loadAd() // Load next ad
            }
        } else {
            Toast.makeText(activity, "Ad is still loading, please try again...", Toast.LENGTH_SHORT).show()
            loadAd()
        }
    }
}
