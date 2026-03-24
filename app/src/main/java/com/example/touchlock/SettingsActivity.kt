package com.example.touchlock

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_settings)

        // Initialize Billing
        billing = BillingManager(this, this)
        billing.start()

        // 1. Find all views
        val switchShowUnlock = findViewById<Switch>(R.id.dialogSwitchShowUnlock)
        val switchInvisibleUnlock = findViewById<Switch>(R.id.dialogSwitchInvisibleUnlock)
        val switchDoubleTap = findViewById<Switch>(R.id.dialogSwitchDoubleTap)
        val switchRequirePin = findViewById<Switch>(R.id.dialogSwitchRequirePin)
        val switchKeepScreen = findViewById<Switch>(R.id.dialogSwitchKeepScreen)
        val seekShake = findViewById<SeekBar>(R.id.dialogSeekShake)
        val edtPin = findViewById<EditText>(R.id.dialogEdtPin)
        val edtDelay = findViewById<EditText>(R.id.dialogEdtDelay)
        val btnSave = findViewById<TextView>(R.id.btnSaveSettings)
        val btnPremium = findViewById<Button>(R.id.btnPremium)
        val btnTip1 = findViewById<Button>(R.id.btnTip1)
        val btnTip2 = findViewById<Button>(R.id.btnTip2)
        val btnTip5 = findViewById<Button>(R.id.btnTip5)

        // 2. Load current values from Prefs so the UI matches reality
        switchShowUnlock.isChecked = Prefs.isShowUnlockButton(this)
        switchInvisibleUnlock.isChecked = Prefs.isInvisibleUnlockButton(this)
        switchDoubleTap.isChecked = Prefs.isDoubleTapEnabled(this)
        switchRequirePin.isChecked = Prefs.isPinRequired(this)
        switchKeepScreen.isChecked = Prefs.isKeepScreenOn(this)
        edtPin.setText(Prefs.getPin(this))
        edtDelay.setText(Prefs.getAutoLockDelay(this).toString())

        val currentThreshold = Prefs.getShakeThreshold(this)
        seekShake.progress = (25 - currentThreshold.toInt()).coerceIn(0, 20)

        // Add listeners to save immediately when changed
        switchShowUnlock.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setShowUnlockButton(this, isChecked)
            sendUpdateIntent()
        }
        switchInvisibleUnlock.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setInvisibleUnlockButton(this, isChecked)
            sendUpdateIntent()
        }
        switchDoubleTap.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setDoubleTapEnabled(this, isChecked)
            sendUpdateIntent()
        }
        switchRequirePin.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setPinRequired(this, isChecked)
            sendUpdateIntent()
        }
        switchKeepScreen.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setKeepScreenOn(this, isChecked)
            sendUpdateIntent()
        }

        // 3. Handle Save Button
        btnSave?.setOnClickListener {
            val pin = edtPin.text.toString().trim()
            val delay = edtDelay.text.toString().trim().toIntOrNull() ?: 0

            // Simple validation
            if (switchRequirePin.isChecked && pin.length < 4) {
                Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // SAVE TO PREFS
            Prefs.setShowUnlockButton(this, switchShowUnlock.isChecked)
            Prefs.setInvisibleUnlockButton(this, switchInvisibleUnlock.isChecked)
            Prefs.setDoubleTapEnabled(this, switchDoubleTap.isChecked)
            Prefs.setPinRequired(this, switchRequirePin.isChecked)
            Prefs.setKeepScreenOn(this, switchKeepScreen.isChecked)
            Prefs.setPin(this, pin)
            Prefs.setAutoLockDelay(this, delay)

            val newThreshold = (25 - seekShake.progress).toFloat()
            Prefs.setShakeThreshold(this, newThreshold)

            sendUpdateIntent()
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
            finish() // Close settings and go back to Main
        }

        // --- Support Buttons Logic ---
        btnPremium.setOnClickListener {
            billing.buyPremium()
        }
        btnTip1.setOnClickListener { billing.buyTip1() }
        btnTip2.setOnClickListener { billing.buyTip2() }
        btnTip5.setOnClickListener { billing.buyTip5() }
    }

    private fun sendUpdateIntent() {
        val intent = Intent(this, TouchLockService::class.java).apply {
            action = TouchLockService.ACTION_UPDATE_SETTINGS
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onDestroy() {
        if (::billing.isInitialized) billing.endConnection()
        super.onDestroy()
    }
}