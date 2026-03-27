package com.noureddine.touchlock

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var billing: BillingManager
    private lateinit var rewardedAdManager: RewardedAdManager
    
    // UI Elements
    private lateinit var txtPremiumStatus: TextView
    private lateinit var switchShowUnlock: Switch
    private lateinit var switchInvisibleUnlock: Switch
    private lateinit var switchDoubleTap: Switch
    private lateinit var switchRequirePin: Switch
    private lateinit var switchKeepScreen: Switch
    private lateinit var switchPersonalizedAds: Switch
    private lateinit var seekShake: SeekBar
    private lateinit var edtPin: EditText
    private lateinit var edtDelay: EditText
    private lateinit var btnPremium: Button
    private lateinit var btnWatchAd: Button

    // Timer for refreshing UI (remaining time)
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updatePremiumUI()
            refreshHandler.postDelayed(this, 30000) // Refresh every 30 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_settings)

        // 0. Clean up expired premium before doing anything
        PremiumManager.clearExpiredTemporaryPremium(this)

        // 1. Initialize Managers
        billing = BillingManager(this, this)
        billing.start()
        
        rewardedAdManager = RewardedAdManager(this)
        rewardedAdManager.loadAd()

        // 2. Find all views
        txtPremiumStatus = findViewById(R.id.txtPremiumStatus)
        switchShowUnlock = findViewById(R.id.dialogSwitchShowUnlock)
        switchInvisibleUnlock = findViewById(R.id.dialogSwitchInvisibleUnlock)
        switchDoubleTap = findViewById(R.id.dialogSwitchDoubleTap)
        switchRequirePin = findViewById(R.id.dialogSwitchRequirePin)
        switchKeepScreen = findViewById(R.id.dialogSwitchKeepScreen)
        switchPersonalizedAds = findViewById(R.id.dialogSwitchPersonalizedAds)
        seekShake = findViewById(R.id.dialogSeekShake)
        edtPin = findViewById(R.id.dialogEdtPin)
        edtDelay = findViewById(R.id.dialogEdtDelay)
        btnPremium = findViewById(R.id.btnPremium)
        btnWatchAd = findViewById(R.id.btnWatchAd)
        val btnSave = findViewById<TextView>(R.id.btnSaveSettings)
        val btnTip1 = findViewById<Button>(R.id.btnTip1)
        val btnTip2 = findViewById<Button>(R.id.btnTip2)
        val btnTip5 = findViewById<Button>(R.id.btnTip5)

        // 3. Load current values from Prefs
        loadSettingsToUI()

        // 4. Setup Listeners
        setupListeners()
        
        // 5. Update Premium UI
        updatePremiumUI()
        
        // --- Support Buttons Logic ---
        btnPremium.setOnClickListener { billing.buyPremium() }
        btnTip1.setOnClickListener { billing.buyTip1() }
        btnTip2.setOnClickListener { billing.buyTip2() }
        btnTip5.setOnClickListener { billing.buyTip5() }

        btnWatchAd.setOnClickListener {
            rewardedAdManager.showAd {
                updatePremiumUI()
                loadSettingsToUI() // Refresh switches state
            }
        }

        btnSave?.setOnClickListener {
            if (saveSettings()) {
                Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadSettingsToUI() {
        switchShowUnlock.isChecked = Prefs.isShowUnlockButton(this)
        switchInvisibleUnlock.isChecked = Prefs.isInvisibleUnlockButton(this)
        switchDoubleTap.isChecked = Prefs.isDoubleTapEnabled(this)
        switchRequirePin.isChecked = Prefs.isPinRequired(this)
        switchKeepScreen.isChecked = Prefs.isKeepScreenOn(this)
        switchPersonalizedAds.isChecked = Prefs.isPersonalizedAdsEnabled(this)
        edtPin.setText(Prefs.getPin(this))
        edtDelay.setText(Prefs.getAutoLockDelay(this).toString())

        val currentThreshold = Prefs.getShakeThreshold(this)
        seekShake.progress = (25 - currentThreshold.toInt()).coerceIn(0, 20)
    }

    private fun setupListeners() {
        // Immediate save for simple switches
        switchShowUnlock.setOnCheckedChangeListener { _, isChecked -> Prefs.setShowUnlockButton(this, isChecked); sendUpdateIntent() }
        switchDoubleTap.setOnCheckedChangeListener { _, isChecked -> Prefs.setDoubleTapEnabled(this, isChecked); sendUpdateIntent() }
        switchPersonalizedAds.setOnCheckedChangeListener { _, isChecked -> Prefs.setPersonalizedAdsEnabled(this, isChecked) }

        // Premium-only features protection
        switchInvisibleUnlock.setOnClickListener { handlePremiumFeatureClick(switchInvisibleUnlock) }
        switchRequirePin.setOnClickListener { handlePremiumFeatureClick(switchRequirePin) }
        switchKeepScreen.setOnClickListener { handlePremiumFeatureClick(switchKeepScreen) }
    }

    private fun handlePremiumFeatureClick(view: Switch) {
        if (!PremiumManager.isPremiumUser(this)) {
            // Revert the check
            view.isChecked = false
            showPremiumDialog()
        } else {
            // User is premium, allow the toggle and save
            when(view.id) {
                R.id.dialogSwitchInvisibleUnlock -> Prefs.setInvisibleUnlockButton(this, view.isChecked)
                R.id.dialogSwitchRequirePin -> Prefs.setPinRequired(this, view.isChecked)
                R.id.dialogSwitchKeepScreen -> Prefs.setKeepScreenOn(this, view.isChecked)
            }
            sendUpdateIntent()
        }
    }

    private fun showPremiumDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.premium_feature_title)
            .setMessage(R.string.premium_feature_message)
            .setPositiveButton(R.string.upgrade) { _, _ -> billing.buyPremium() }
            .setNeutralButton(R.string.watch_ad) { _, _ -> 
                rewardedAdManager.showAd { 
                    updatePremiumUI()
                    loadSettingsToUI() 
                } 
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updatePremiumUI() {
        val isPremium = PremiumManager.isPremiumUser(this)
        val isPermanent = PremiumManager.isPermanentPremium(this)
        
        // Update Status Text
        txtPremiumStatus.text = PremiumManager.getRemainingPremiumTimeText(this)
        
        // Update Premium Button Text
        if (isPermanent) {
            btnPremium.text = getString(R.string.premium_enabled)
            btnPremium.isEnabled = false
            btnWatchAd.visibility = android.view.View.GONE
        } else if (isPremium) {
            btnPremium.text = getString(R.string.upgrade_to_permanent_premium)
            btnPremium.isEnabled = true
            btnWatchAd.visibility = android.view.View.VISIBLE
        } else {
            btnPremium.text = getString(R.string.upgrade_to_premium)
            btnPremium.isEnabled = true
            btnWatchAd.visibility = android.view.View.VISIBLE
        }

        // Visually disable premium switches if not premium
        val alpha = if (isPremium) 1.0f else 0.5f
        switchInvisibleUnlock.alpha = alpha
        switchRequirePin.alpha = alpha
        switchKeepScreen.alpha = alpha
    }

    private fun saveSettings(): Boolean {
        val pin = edtPin.text.toString().trim()
        val delay = edtDelay.text.toString().trim().toIntOrNull() ?: 0

        if (switchRequirePin.isChecked && pin.length < 4) {
            Toast.makeText(this, getString(R.string.pin_length_warning), Toast.LENGTH_SHORT).show()
            return false
        }

        Prefs.setShowUnlockButton(this, switchShowUnlock.isChecked)
        Prefs.setDoubleTapEnabled(this, switchDoubleTap.isChecked)
        Prefs.setPersonalizedAdsEnabled(this, switchPersonalizedAds.isChecked)
        Prefs.setPin(this, pin)
        Prefs.setAutoLockDelay(this, delay)

        val newThreshold = (25 - seekShake.progress).toFloat()
        Prefs.setShakeThreshold(this, newThreshold)

        // Premium features safety check
        if (!PremiumManager.isPremiumUser(this)) {
            Prefs.setInvisibleUnlockButton(this, false)
            Prefs.setPinRequired(this, false)
            Prefs.setKeepScreenOn(this, false)
        } else {
            Prefs.setInvisibleUnlockButton(this, switchInvisibleUnlock.isChecked)
            Prefs.setPinRequired(this, switchRequirePin.isChecked)
            Prefs.setKeepScreenOn(this, switchKeepScreen.isChecked)
        }

        sendUpdateIntent()
        return true
    }

    private fun sendUpdateIntent() {
        val intent = Intent(this, TouchLockService::class.java).apply {
            action = TouchLockService.ACTION_UPDATE_SETTINGS
        }
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            // Service might not be running
        }
    }

    override fun onResume() {
        super.onResume()
        PremiumManager.clearExpiredTemporaryPremium(this)
        updatePremiumUI()
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        if (::billing.isInitialized) billing.endConnection()
        super.onDestroy()
    }
}
