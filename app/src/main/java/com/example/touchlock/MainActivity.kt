package com.example.touchlock

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.touchlock.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val REQ_OVERLAY = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        loadSettings()
        updateOverlayStatus()

        binding.btnGrantOverlay.setOnClickListener {
            openOverlayPermission()
        }

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        binding.btnStartLock.setOnClickListener {
            saveSettings()

            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please allow overlay permission first", Toast.LENGTH_SHORT).show()
                openOverlayPermission()
                return@setOnClickListener
            }

            val intent = Intent(this, TouchLockService::class.java).apply {
                action = TouchLockService.ACTION_START
            }
            startService(intent)
            Toast.makeText(this, "Touch lock started", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopLock.setOnClickListener {
            val intent = Intent(this, TouchLockService::class.java).apply {
                action = TouchLockService.ACTION_STOP
            }
            startService(intent)
            Toast.makeText(this, "Touch lock stopped", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateOverlayStatus()
    }

    private fun loadSettings() {
        binding.switchShowUnlock.isChecked = Prefs.isShowUnlockButton(this)
        binding.switchDoubleTap.isChecked = Prefs.isDoubleTapEnabled(this)
        binding.switchRequirePin.isChecked = Prefs.isPinRequired(this)
        binding.edtPin.setText(Prefs.getPin(this))
        binding.edtDelay.setText(Prefs.getAutoLockDelay(this).toString())
    }

    private fun saveSettings() {
        val pin = binding.edtPin.text.toString().trim()
        val delay = binding.edtDelay.text.toString().trim().toIntOrNull() ?: 0

        if (binding.switchRequirePin.isChecked && pin.length < 4) {
            Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
            return
        }

        Prefs.setShowUnlockButton(this, binding.switchShowUnlock.isChecked)
        Prefs.setDoubleTapEnabled(this, binding.switchDoubleTap.isChecked)
        Prefs.setPinRequired(this, binding.switchRequirePin.isChecked)
        Prefs.setPin(this, pin)
        Prefs.setAutoLockDelay(this, delay)

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun updateOverlayStatus() {
        val granted = Settings.canDrawOverlays(this)
        binding.txtOverlayStatus.text =
            if (granted) "Overlay permission: Granted"
            else "Overlay permission: Not granted"
    }

    private fun openOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQ_OVERLAY)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
    }
}