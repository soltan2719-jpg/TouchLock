package com.example.touchlock

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
import com.example.touchlock.databinding.ActivityMainBinding
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

    // Receiver to update UI when the Service locks or unlocks
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUIBasedOnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()

        installedApps = loadLaunchableApps()
        selectedPackages.clear()
        selectedPackages.addAll(Prefs.getSelectedPackages(this))

        loadSettings()
        updatePermissionStatus()
        updateSelectedAppsText()
        updatePremiumText()

        billing = BillingManager(
            context = this,
            activity = this
        )
        billing.start()

        MobileAds.initialize(this)
        setupBannerAd()
        updateAdVisibility()

        binding.btnGrantOverlay.setOnClickListener {
            openOverlayPermission()
        }

        binding.btnGrantUsageAccess.setOnClickListener {
            requestUsageStatsPermission()
        }

        binding.btnPickApps.setOnClickListener {
            showAppPickerDialog()
        }

        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnOpenSupport.setOnClickListener {
            showSupportDialog()
        }

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        // Updated Toggle Logic: If showing, STOP. If not showing, START.
        binding.btnStartLockNow.setOnClickListener {
            if (TouchLockService.isOverlayShowing) {
                val intent = Intent(this, TouchLockService::class.java).apply {
                    action = TouchLockService.ACTION_STOP_ALL
                }
                startTouchLockService(intent)
                Toast.makeText(this, "Unlocking...", Toast.LENGTH_SHORT).show()
            } else {
                if (!saveSettings()) return@setOnClickListener
                if (!checkOverlayPermission()) return@setOnClickListener

                val intent = Intent(this, TouchLockService::class.java).apply {
                    action = TouchLockService.ACTION_START_LOCK_NOW
                }
                startTouchLockService(intent)
                Toast.makeText(this, "Touch lock started", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnStartMonitor.setOnClickListener {
            if (!Prefs.isPremium(this)) {
                Toast.makeText(this, "Auto start is premium feature", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!saveSettings()) return@setOnClickListener
            if (!checkOverlayPermission()) return@setOnClickListener

            if (!hasUsageAccess()) {
                Toast.makeText(this, "Please allow Usage Access first", Toast.LENGTH_SHORT).show()
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

            Toast.makeText(this, "Monitor mode started", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopAll.setOnClickListener {
            val intent = Intent(this, TouchLockService::class.java).apply {
                action = TouchLockService.ACTION_STOP_ALL
            }
            startTouchLockService(intent)
            Toast.makeText(this, "Stopped", Toast.LENGTH_SHORT).show()
        }

        binding.btnPremium.setOnClickListener {
            billing.buyPremium()
        }

        binding.btnTip1.setOnClickListener {
            billing.buyTip1()
        }

        binding.btnTip2.setOnClickListener {
            billing.buyTip2()
        }

        binding.btnTip5.setOnClickListener {
            billing.buyTip5()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.example.touchlock.STATUS_CHANGED")

        // This fix handles the security requirement for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        updateUIBasedOnService()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updatePremiumText()
        updateAdVisibility()
        updateUIBasedOnService()

        if (::billing.isInitialized) {
            billing.checkPremium {
                runOnUiThread {
                    updatePremiumText()
                    updateAdVisibility()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(statusReceiver)
        } catch (_: Exception) {
            // Already unregistered
        }
    }

    private fun updateUIBasedOnService() {
        if (TouchLockService.isOverlayShowing) {
            binding.btnStartLockNow.text = getString(R.string.unlock)
            binding.txtSubtitle.text = getString(R.string.protection_active)
        } else {
            binding.btnStartLockNow.text = getString(R.string.lock_now)
            binding.txtSubtitle.text = getString(R.string.touchlock_ready)
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
        } else {
            Toast.makeText(this, "Usage Access already granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        adView?.destroy()
        if (::billing.isInitialized) {
            billing.endConnection()
        }
        super.onDestroy()
    }

    private fun setupBannerAd() {
        if (Prefs.isPremium(this)) {
            binding.adContainer.visibility = View.GONE
            return
        }

        binding.adContainer.visibility = View.VISIBLE
        adView?.destroy()

        adView = AdView(this).apply {
            adUnitId = "ca-app-pub-3940256099942544/6300978111"
            setAdSize(AdSize.BANNER)
        }

        binding.adContainer.removeAllViews()
        binding.adContainer.addView(adView)

        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }

    private fun updateAdVisibility() {
        if (Prefs.isPremium(this)) {
            binding.adContainer.visibility = View.GONE
        } else {
            binding.adContainer.visibility = View.VISIBLE
            if (adView == null) {
                setupBannerAd()
            } else {
                adView?.loadAd(AdRequest.Builder().build())
            }
        }
    }

    private fun updatePremiumText() {
        if (Prefs.isPremium(this)) {
            binding.btnPremium.text = getString(R.string.premium_enabled)
            binding.btnPremium.isEnabled = false
            binding.adContainer.visibility = View.GONE
        } else {
            binding.btnPremium.text = getString(R.string.upgrade_to_premium)
            binding.btnPremium.isEnabled = true
            binding.adContainer.visibility = View.VISIBLE
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

        Prefs.setShowUnlockButton(this, binding.switchShowUnlock.isChecked)
        Prefs.setDoubleTapEnabled(this, binding.switchDoubleTap.isChecked)
        Prefs.setPinRequired(this, binding.switchRequirePin.isChecked)
        Prefs.setPin(this, pin)
        Prefs.setAutoLockDelay(this, delay)
        Prefs.setSelectedPackages(this, selectedPackages)

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        return true
    }

    @SuppressLint("SetTextI18n")
    private fun updatePermissionStatus() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val usageGranted = hasUsageAccess()
        val batteryIgnored = isBatteryOptimizationDisabled()

        binding.txtSystemStatus.text = if (overlayGranted) "Ready" else "Needs permission"
        binding.txtSystemStatus.setTextColor(
            ContextCompat.getColor(this, if (overlayGranted) R.color.success else R.color.warning)
        )

        binding.txtOverlayStatus.text = if (overlayGranted) "Overlay permission: Granted" else "Overlay permission: Not granted"

        val usageText = if (usageGranted) "Usage: Granted" else "Usage: Not granted"
        val batteryText = if (batteryIgnored) "Battery: Unrestricted" else "Battery: Optimized"
        binding.txtUsageStatus.text = "$usageText | $batteryText"
    }

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow overlay permission first", Toast.LENGTH_SHORT).show()
            openOverlayPermission()
            return false
        }
        return true
    }

    private fun openOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri())
        startActivity(intent)
    }

    private fun hasUsageAccess(): Boolean {
        val statsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val stats = statsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 1000 * 10,
            currentTime
        )
        return stats != null && stats.isNotEmpty()
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
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
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        return resolved.map {
            AppInfo(label = it.loadLabel(packageManager).toString(), packageName = it.activityInfo.packageName)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    private fun showAppPickerDialog() {
        if (installedApps.isEmpty()) {
            Toast.makeText(this, "No apps found", Toast.LENGTH_SHORT).show()
            return
        }
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
}