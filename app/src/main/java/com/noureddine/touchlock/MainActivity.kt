package com.noureddine.touchlock

import android.Manifest
import android.annotation.SuppressLint
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.noureddine.touchlock.databinding.ActivityMainBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var billing: BillingManager
    private var adView: AdView? = null
    private var installedApps: List<AppInfo> = emptyList()
    private val selectedPackages = mutableSetOf<String>()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUIBasedOnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 0. Clean up expired temporary premium on start
        PremiumManager.clearExpiredTemporaryPremium(this)

        requestNotificationPermissionIfNeeded()

        installedApps = loadLaunchableApps()
        selectedPackages.clear()
        selectedPackages.addAll(Prefs.getSelectedPackages(this))

        loadSettings()

        // --- AUTO-SAVE LISTENERS ---
        binding.switchShowUnlock.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setShowUnlockButton(this, isChecked)
            sendUpdateIntent()
        }
        binding.switchDoubleTap.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setDoubleTapEnabled(this, isChecked)
            sendUpdateIntent()
        }
        binding.switchRequirePin.setOnCheckedChangeListener { _, isChecked ->
            if (!PremiumManager.isPremiumUser(this)) {
                binding.switchRequirePin.isChecked = false
                showPremiumFeatureDialog()
            } else {
                Prefs.setPinRequired(this, isChecked)
                sendUpdateIntent()
            }
        }
        // ---------------------------------

        updatePermissionStatus()
        updateSelectedAppsText()
        updatePremiumUI()

        billing = BillingManager(context = this, activity = this)
        billing.start()

        MobileAds.initialize(this)
        setupBannerAd()
        updateAdVisibility()

        binding.btnGrantOverlay.setOnClickListener { openOverlayPermission() }
        binding.btnGrantUsageAccess.setOnClickListener { requestUsageStatsPermission() }
        binding.btnPickApps.setOnClickListener { showAppPickerDialog() }
        binding.btnOpenSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.btnOpenSupport.setOnClickListener { showSupportDialog() }
        binding.btnSaveSettings.setOnClickListener { saveSettings() }

        binding.btnStartLockNow.setOnClickListener {
            if (TouchLockService.isOverlayShowing) {
                val intent = Intent(this, TouchLockService::class.java).apply {
                    action = TouchLockService.ACTION_STOP_ALL
                }
                startTouchLockService(intent)
            } else {
                if (!saveSettings() || !checkOverlayPermission()) return@setOnClickListener
                val intent = Intent(this, TouchLockService::class.java).apply {
                    action = TouchLockService.ACTION_START_LOCK_NOW
                }
                startTouchLockService(intent)
            }
        }

        binding.btnStartMonitor.setOnClickListener {
            if (!PremiumManager.isPremiumUser(this)) {
                showPremiumFeatureDialog()
                return@setOnClickListener
            }
            if (!saveSettings() || !checkOverlayPermission()) return@setOnClickListener
            if (!hasUsageAccess()) {
                requestUsageStatsPermission()
                return@setOnClickListener
            }
            if (selectedPackages.isEmpty()) {
                Toast.makeText(this, "Please choose at least one app", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, TouchLockService::class.java).apply {
                action = TouchLockService.ACTION_START_MONITOR
            }
            startTouchLockService(intent)
            Prefs.setMonitorEnabled(this, true)
            Toast.makeText(this, "Monitor mode started", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopAll.setOnClickListener {
            val intent = Intent(this, TouchLockService::class.java).apply {
                action = TouchLockService.ACTION_STOP_ALL
            }
            startTouchLockService(intent)
            Prefs.setMonitorEnabled(this, false)
        }

        binding.btnPremium.setOnClickListener { billing.buyPremium() }
        binding.btnTip1.setOnClickListener { billing.buyTip1() }
        binding.btnTip2.setOnClickListener { billing.buyTip2() }
        binding.btnTip5.setOnClickListener { billing.buyTip5() }
    }

    private fun showPremiumFeatureDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.premium_feature_title)
            .setMessage(R.string.premium_feature_message)
            .setPositiveButton(R.string.upgrade) { _, _ -> billing.buyPremium() }
            .setNeutralButton(R.string.watch_ad) { _, _ ->
                // Navigate to settings where they can watch the ad
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendUpdateIntent() {
        val intent = Intent(this, TouchLockService::class.java).apply {
            action = "com.noureddine.touchlock.UPDATE_SETTINGS"
        }
        startTouchLockService(intent)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.noureddine.touchlock.STATUS_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        updateUIBasedOnService()
    }

    override fun onResume() {
        super.onResume()
        PremiumManager.clearExpiredTemporaryPremium(this)
        loadSettings()
        selectedPackages.clear()
        selectedPackages.addAll(Prefs.getSelectedPackages(this))
        updateSelectedAppsText()
        updatePermissionStatus()
        updatePremiumUI()
        updateAdVisibility()
        updateUIBasedOnService()
        if (::billing.isInitialized) {
            billing.checkPremium {
                runOnUiThread {
                    updatePremiumUI()
                    updateAdVisibility()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
    }

    private fun updateUIBasedOnService() {
        val isActive = TouchLockService.isOverlayShowing || TouchLockService.isMonitoring
        if (isActive) {
            binding.btnStartLockNow.text = getString(R.string.unlock)
            binding.txtSubtitle.text = getString(R.string.protection_active)
            binding.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.active_blue))
        } else {
            binding.btnStartLockNow.text = getString(R.string.lock_now)
            binding.txtSubtitle.text = getString(R.string.touchlock_ready)
            binding.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_bg))
        }
    }

    private fun requestUsageStatsPermission() {
        if (!hasUsageAccess()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            Toast.makeText(this, "Find TouchLock and allow Usage Access", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupBannerAd() {
        if (PremiumManager.isPremiumUser(this)) {
            binding.adContainer.visibility = View.GONE
            return
        }
        binding.adContainer.visibility = View.VISIBLE
        adView?.destroy()
        adView = AdView(this).apply {
            adUnitId = getString(R.string.admob_banner_id)
            setAdSize(AdSize.BANNER)
        }
        binding.adContainer.removeAllViews()
        binding.adContainer.addView(adView)
        adView?.loadAd(AdRequest.Builder().build())
    }

    private fun updateAdVisibility() {
        if (PremiumManager.isPremiumUser(this)) {
            binding.adContainer.visibility = View.GONE
        } else {
            binding.adContainer.visibility = View.VISIBLE
            if (adView == null) setupBannerAd() else adView?.loadAd(AdRequest.Builder().build())
        }
    }

    private fun updatePremiumUI() {
        val isPremium = PremiumManager.isPremiumUser(this)
        val isPermanent = PremiumManager.isPermanentPremium(this)

        if (isPermanent) {
            binding.btnPremium.text = getString(R.string.premium_enabled)
            binding.btnPremium.isEnabled = false
        } else if (isPremium) {
            binding.btnPremium.text = getString(R.string.upgrade_to_permanent_premium)
            binding.btnPremium.isEnabled = true
        } else {
            binding.btnPremium.text = getString(R.string.upgrade_to_premium)
            binding.btnPremium.isEnabled = true
        }
    }

    private fun loadSettings() {
        binding.switchShowUnlock.isChecked = Prefs.isShowUnlockButton(this)
        binding.switchDoubleTap.isChecked = Prefs.isDoubleTapEnabled(this)
        binding.switchRequirePin.isChecked = Prefs.isPinRequired(this)
        binding.edtPin.setText(Prefs.getPin(this))
        binding.edtDelay.setText(Prefs.getAutoLockDelay(this).toString())
    }

    private fun saveSettings(): Boolean {
        val pin = binding.edtPin.text.toString().trim()
        val delay = binding.edtDelay.text.toString().trim().toIntOrNull() ?: 0
        if (binding.switchRequirePin.isChecked && pin.length < 4) {
            Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
            return false
        }

        // Sync everything to Prefs
        Prefs.setShowUnlockButton(this, binding.switchShowUnlock.isChecked)
        Prefs.setDoubleTapEnabled(this, binding.switchDoubleTap.isChecked)
        
        // Premium check for security settings
        if (!PremiumManager.isPremiumUser(this)) {
            Prefs.setPinRequired(this, false)
        } else {
            Prefs.setPinRequired(this, binding.switchRequirePin.isChecked)
        }

        Prefs.setPin(this, pin)
        Prefs.setAutoLockDelay(this, delay)
        Prefs.setSelectedPackages(this, selectedPackages)
        sendUpdateIntent()
        return true
    }

    @SuppressLint("SetTextI18n")
    private fun updatePermissionStatus() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val usageGranted = hasUsageAccess()
        val batteryIgnored = isBatteryOptimizationDisabled()

        binding.txtSystemStatus.text = if (overlayGranted) getString(R.string.ready) else getString(R.string.needs_permission)
        binding.txtSystemStatus.setTextColor(ContextCompat.getColor(this, if (overlayGranted) R.color.success else R.color.warning))
        
        binding.txtOverlayStatus.text = if (overlayGranted) getString(R.string.overlay_granted) else getString(R.string.overlay_not_granted)
        
        val usageText = if (usageGranted) getString(R.string.usage_granted) else getString(R.string.usage_not_granted)
        val batteryText = if (batteryIgnored) getString(R.string.battery_unrestricted) else getString(R.string.battery_optimized)
        binding.txtUsageStatus.text = "$usageText | $batteryText"
    }

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermission()
            return false
        }
        return true
    }

    private fun openOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
    }

    private fun hasUsageAccess(): Boolean {
        val statsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val stats = statsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, currentTime - 1000 * 10, currentTime)
        return !stats.isNullOrEmpty()
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
            }
        }
    }

    private fun startTouchLockService(intent: Intent) {
        ContextCompat.startForegroundService(this, intent)
    }

    private fun loadLaunchableApps(): List<AppInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolved = packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        return resolved.map {
            AppInfo(label = it.loadLabel(packageManager).toString(), packageName = it.activityInfo.packageName)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    private fun showAppPickerDialog() {
        if (installedApps.isEmpty()) return
        val labels = installedApps.map { "${it.label}\n${it.packageName}" }.toTypedArray()
        val checked = installedApps.map { selectedPackages.contains(it.packageName) }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Choose apps for auto-start lock")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = installedApps[which].packageName
                if (isChecked) selectedPackages.add(pkg) else selectedPackages.remove(pkg)
            }
            .setPositiveButton("Save") { _, _ ->
                Prefs.setSelectedPackages(this, selectedPackages)
                updateSelectedAppsText()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSelectedAppsText() {
        binding.chipsContainer.removeAllViews()
        if (selectedPackages.isEmpty()) {
            binding.txtSelectedApps.text = getString(R.string.selected_apps_none)
            binding.scrollSelectedApps.visibility = View.GONE
            return
        }
        val selectedApps = installedApps.filter { selectedPackages.contains(it.packageName) }
        binding.txtSelectedApps.text = getString(R.string.apps_selected_for_autostart)
        binding.scrollSelectedApps.visibility = View.VISIBLE
        selectedApps.forEachIndexed { index, app ->
            val chip = TextView(this).apply {
                text = app.label
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
                textSize = 14f
                setBackgroundResource(R.drawable.bg_app_chip)
                setPadding(28, 16, 28, 16)
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (index > 0) params.marginStart = 12
            binding.chipsContainer.addView(chip, params)
        }
    }

    private fun showSupportDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_support, null)
        view.findViewById<Button>(R.id.dialogBtnPremium).setOnClickListener { billing.buyPremium() }
        view.findViewById<Button>(R.id.dialogBtnTip1).setOnClickListener { billing.buyTip1() }
        view.findViewById<Button>(R.id.dialogBtnTip2).setOnClickListener { billing.buyTip2() }
        view.findViewById<Button>(R.id.dialogBtnTip5).setOnClickListener { billing.buyTip5() }
        AlertDialog.Builder(this).setView(view).setNegativeButton("Close", null).show()
    }

    override fun onDestroy() {
        adView?.destroy()
        if (::billing.isInitialized) billing.endConnection()
        super.onDestroy()
    }
}
