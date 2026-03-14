package com.example.touchlock

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Context.APP_OPS_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.touchlock.databinding.ActivityMainBinding
import android.view.View
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import androidx.appcompat.widget.SwitchCompat
import android.widget.Button

class MainActivity : AppCompatActivity() {


    private lateinit var billing: BillingManager

    private lateinit var binding: ActivityMainBinding

    private var adView: AdView? = null

    private var installedApps: List<AppInfo> = emptyList()
    private val selectedPackages = mutableSetOf<String>()
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

        billing = BillingManager(this, this)
        billing.start()

        MobileAds.initialize(this)
        setupBannerAd()
        binding.btnGrantOverlay.setOnClickListener {
            openOverlayPermission()
        }

        binding.btnGrantUsageAccess.setOnClickListener {
            openUsageAccessSettings()
        }

        binding.btnPickApps.setOnClickListener {
            showAppPickerDialog()
        }

        binding.btnOpenSettings.setOnClickListener {
            Toast.makeText(this, "Settings screen coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnOpenSupport.setOnClickListener {
            showSupportDialog()
        }

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        binding.btnStartLockNow.setOnClickListener {
            if (!saveSettings()) return@setOnClickListener
            if (!checkOverlayPermission()) return@setOnClickListener

            val intent = Intent(this, TouchLockService::class.java).apply {
                action = TouchLockService.ACTION_START_LOCK_NOW
            }
            startTouchLockService(intent)
            Toast.makeText(this, "Touch lock started", Toast.LENGTH_SHORT).show()
            binding.btnStartLockNow.text = getString(R.string.unlock)
            binding.txtSubtitle.text = getString(R.string.protection_active)
        }

        binding.btnStartMonitor.setOnClickListener {
            if (!Prefs.isPremium(this)) {
                Toast.makeText(this, "Auto start is premium feature", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!saveSettings()) return@setOnClickListener
            if (!checkOverlayPermission()) return@setOnClickListener
            if (!checkUsageAccessPermission()) {
                Toast.makeText(this, "Please allow Usage Access first", Toast.LENGTH_SHORT).show()
                openUsageAccessSettings()
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
            binding.btnStartLockNow.text = getString(R.string.lock_now)
            binding.txtSubtitle.text = getString(R.string.touchlock_ready)
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

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
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

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)

        val switchShowUnlock = view.findViewById<SwitchCompat>(R.id.dialogSwitchShowUnlock)
        val switchDoubleTap = view.findViewById<SwitchCompat>(R.id.dialogSwitchDoubleTap)
        val switchRequirePin = view.findViewById<SwitchCompat>(R.id.dialogSwitchRequirePin)
        val edtPin = view.findViewById<EditText>(R.id.dialogEdtPin)
        val edtDelay = view.findViewById<EditText>(R.id.dialogEdtDelay)

        switchShowUnlock.isChecked = Prefs.isShowUnlockButton(this)
        switchDoubleTap.isChecked = Prefs.isDoubleTapEnabled(this)
        switchRequirePin.isChecked = Prefs.isPinRequired(this)
        edtPin.setText(Prefs.getPin(this))
        edtDelay.setText(Prefs.getAutoLockDelay(this).toString())

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val pin = edtPin.text.toString().trim()
                val delay = edtDelay.text.toString().trim().toIntOrNull() ?: 0

                Prefs.setShowUnlockButton(this, switchShowUnlock.isChecked)
                Prefs.setDoubleTapEnabled(this, switchDoubleTap.isChecked)
                Prefs.setPinRequired(this, switchRequirePin.isChecked)
                Prefs.setPin(this, pin)
                Prefs.setAutoLockDelay(this, delay)

                loadSettings()
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSupportDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_support, null)

        val btnPremium = view.findViewById<Button>(R.id.dialogBtnPremium)
        val btnTip1 = view.findViewById<Button>(R.id.dialogBtnTip1)
        val btnTip2 = view.findViewById<Button>(R.id.dialogBtnTip2)
        val btnTip5 = view.findViewById<Button>(R.id.dialogBtnTip5)

        btnPremium.setOnClickListener {
            billing.buyPremium()
        }

        btnTip1.setOnClickListener {
            billing.buyTip1()
        }

        btnTip2.setOnClickListener {
            billing.buyTip2()
        }

        btnTip5.setOnClickListener {
            billing.buyTip5()
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton("Close", null)
            .show()
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

    private fun updatePermissionStatus() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val usageGranted = hasUsageAccess()

        binding.txtSystemStatus.text =
            if (Settings.canDrawOverlays(this)) "Ready"
            else "Needs permission"

        binding.txtSystemStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (Settings.canDrawOverlays(this)) R.color.success else R.color.warning
            )
        )

        binding.txtOverlayStatus.text =
            if (overlayGranted) "Overlay permission: Granted"
            else "Overlay permission: Not granted"

        binding.txtUsageStatus.text =
            if (usageGranted) "Usage access: Granted"
            else "Usage access: Not granted"
    }

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow overlay permission first", Toast.LENGTH_SHORT).show()
            openOverlayPermission()
            return false
        }
        return true
    }

    private fun checkUsageAccessPermission(): Boolean {
        return hasUsageAccess()
    }

    private fun openOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
    }

    private fun startTouchLockService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun loadLaunchableApps(): List<AppInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolved = packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.MATCH_ALL
        )

        return resolved
            .map {
                AppInfo(
                    label = it.loadLabel(packageManager).toString(),
                    packageName = it.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
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

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            if (index > 0) {
                params.marginStart = 12
            }

            binding.chipsContainer.addView(chip, params)
        }
    }
}