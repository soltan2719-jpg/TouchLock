package com.example.touchlock

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_settings)

        // 1. Find all views
        val switchShowUnlock = findViewById<Switch>(R.id.dialogSwitchShowUnlock)
        val switchDoubleTap = findViewById<Switch>(R.id.dialogSwitchDoubleTap)
        val switchRequirePin = findViewById<Switch>(R.id.dialogSwitchRequirePin)
        val seekShake = findViewById<SeekBar>(R.id.dialogSeekShake)
        val edtPin = findViewById<EditText>(R.id.dialogEdtPin)
        val edtDelay = findViewById<EditText>(R.id.dialogEdtDelay)
        val btnSave = findViewById<Button>(R.id.btnSaveSettings)
        val btnPremium = findViewById<Button>(R.id.btnPremium)

        // 2. Load current values from Prefs so the UI matches reality
        switchShowUnlock.isChecked = Prefs.isShowUnlockButton(this)
        switchDoubleTap.isChecked = Prefs.isDoubleTapEnabled(this)
        switchRequirePin.isChecked = Prefs.isPinRequired(this)
        edtPin.setText(Prefs.getPin(this))
        edtDelay.setText(Prefs.getAutoLockDelay(this).toString())

        val currentThreshold = Prefs.getShakeThreshold(this)
        seekShake.progress = (25 - currentThreshold.toInt()).coerceIn(0, 20)

        // Add listeners to save immediately when changed
        switchShowUnlock.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setShowUnlockButton(this, isChecked)
        }
        switchDoubleTap.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setDoubleTapEnabled(this, isChecked)
        }
        switchRequirePin.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setPinRequired(this, isChecked)
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
            Prefs.setDoubleTapEnabled(this, switchDoubleTap.isChecked)
            Prefs.setPinRequired(this, switchRequirePin.isChecked)
            Prefs.setPin(this, pin)
            Prefs.setAutoLockDelay(this, delay)

            val newThreshold = (25 - seekShake.progress).toFloat()
            Prefs.setShakeThreshold(this, newThreshold)

            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
            finish() // Close settings and go back to Main
        }

        btnPremium.setOnClickListener {
            startActivity(Intent(this, PremiumActivity::class.java))
        }
    }
}