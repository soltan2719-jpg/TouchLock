package com.example.touchlock

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.touchlock.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences(Prefs.NAME, MODE_PRIVATE) }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Notification permission Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Timer dropdown
        val timerItems = listOf("Off", "30 seconds", "1 minute", "5 minutes", "10 minutes")
        binding.spinnerTimer.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            timerItems
        )

        // Load settings
        binding.seekDim.progress = prefs.getInt(Prefs.DIM_PERCENT, 20)
        binding.txtDimValue.text = "${binding.seekDim.progress}%"

        binding.switchUnlockButton.isChecked = prefs.getBoolean(Prefs.SHOW_UNLOCK_BTN, true)
        binding.switchDoubleTap.isChecked = prefs.getBoolean(Prefs.DOUBLE_TAP_UNLOCK, true)
        binding.switchEdgeHandle.isChecked = prefs.getBoolean(Prefs.EDGE_HANDLE_UNLOCK, true)
        binding.switchShakeUnlock.isChecked = prefs.getBoolean(Prefs.SHAKE_UNLOCK, false)

        val savedTimerIndex = prefs.getInt(Prefs.TIMER_INDEX, 0).coerceIn(0, 4)
        binding.spinnerTimer.setSelection(savedTimerIndex)

        updateOverlayStatusUi()

        // Listeners
        binding.seekDim.setOnSeekBarChangeListener(SimpleSeekBar { value ->
            binding.txtDimValue.text = "$value%"
            prefs.edit { putInt(Prefs.DIM_PERCENT, value) }
        })

        binding.switchUnlockButton.setOnCheckedChangeListener { _, v ->
            prefs.edit { putBoolean(Prefs.SHOW_UNLOCK_BTN, v) }
        }
        binding.switchDoubleTap.setOnCheckedChangeListener { _, v ->
            prefs.edit { putBoolean(Prefs.DOUBLE_TAP_UNLOCK, v) }
        }
        binding.switchEdgeHandle.setOnCheckedChangeListener { _, v ->
            prefs.edit { putBoolean(Prefs.EDGE_HANDLE_UNLOCK, v) }
        }
        binding.switchShakeUnlock.setOnCheckedChangeListener { _, v ->
            prefs.edit { putBoolean(Prefs.SHAKE_UNLOCK, v) }
        }

        binding.spinnerTimer.setOnItemSelectedListener(SimpleItemSelected { index ->
            prefs.edit { putInt(Prefs.TIMER_INDEX, index) }
        })

        binding.btnGrantOverlay.setOnClickListener {
            openOverlayPermissionScreen()
        }

        binding.btnStartLock.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Enable 'Display over other apps' first.", Toast.LENGTH_LONG).show()
                openOverlayPermissionScreen()
                return@setOnClickListener
            }
            TouchLockService.start(this)
            Toast.makeText(this, "Touch Lock ON", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopLock.setOnClickListener {
            TouchLockService.stop(this)
            Toast.makeText(this, "Touch Lock OFF", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateOverlayStatusUi()
    }

    private fun updateOverlayStatusUi() {
        val allowed = Settings.canDrawOverlays(this)
        binding.txtOverlayStatus.text =
            if (allowed) "Overlay permission: ENABLED ✅"
            else "Overlay permission: DISABLED ❌"
    }

    private fun openOverlayPermissionScreen() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}