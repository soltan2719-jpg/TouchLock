package com.example.touchlock

import android.content.Intent   // ← You forgot this import
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_settings)

        // PREMIUM BUTTON (Step 4)
        val btnPremium = findViewById<Button>(R.id.btnPremium)
        btnPremium.setOnClickListener {
            startActivity(Intent(this, PremiumActivity::class.java))
        }

        val switchShowUnlock = findViewById<Switch>(R.id.dialogSwitchShowUnlock)
        val switchDoubleTap = findViewById<Switch>(R.id.dialogSwitchDoubleTap)
        val switchRequirePin = findViewById<Switch>(R.id.dialogSwitchRequirePin)
        val edtPin = findViewById<EditText>(R.id.dialogEdtPin)
        val edtDelay = findViewById<EditText>(R.id.dialogEdtDelay)

        // Load saved values
        switchShowUnlock.isChecked = Prefs.isShowUnlockButton(this)
        switchDoubleTap.isChecked = Prefs.isDoubleTapEnabled(this)
        switchRequirePin.isChecked = Prefs.isPinRequired(this)
        edtPin.setText(Prefs.getPin(this))
        edtDelay.setText(Prefs.getAutoLockDelay(this).toString())

        // Save on exit
        findViewById<Button>(R.id.btnSaveSettings)?.setOnClickListener {
            Prefs.setShowUnlockButton(this, switchShowUnlock.isChecked)
            Prefs.setDoubleTapEnabled(this, switchDoubleTap.isChecked)
            Prefs.setPinRequired(this, switchRequirePin.isChecked)
            Prefs.setPin(this, edtPin.text.toString())
            Prefs.setAutoLockDelay(this, edtDelay.text.toString().toIntOrNull() ?: 0)

            finish()
        }
    }
}