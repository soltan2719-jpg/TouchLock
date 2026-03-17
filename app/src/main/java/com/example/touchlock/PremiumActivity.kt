package com.example.touchlock

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PremiumActivity : AppCompatActivity() {

    private var billing: BillingManager? = null
    private lateinit var txtStatus: TextView
    private lateinit var btnBuy: Button
    private lateinit var btnRestore: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.premium_activity)

        txtStatus = findViewById(R.id.txtPremiumStatus)
        btnBuy = findViewById(R.id.btnBuyPremium)
        btnRestore = findViewById(R.id.btnRestore)

        updatePremiumStatus()

        try {
            billing = BillingManager(
                context = this,
                activity = this
            )
            billing?.start()
        } catch (e: Exception) {
            txtStatus.text = "Premium screen loaded, billing unavailable"
        }

        btnBuy.setOnClickListener {
            if (billing == null) {
                txtStatus.text = "Billing not ready"
                return@setOnClickListener
            }
            billing?.buyPremium()
        }

        btnRestore.setOnClickListener {
            if (billing == null) {
                txtStatus.text = "Billing not ready"
                return@setOnClickListener
            }
            billing?.restorePurchase {
                runOnUiThread {
                    updatePremiumStatus()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        billing?.checkPremium {
            runOnUiThread {
                updatePremiumStatus()
            }
        }
    }

    override fun onDestroy() {
        billing?.endConnection()
        super.onDestroy()
    }

    private fun updatePremiumStatus() {
        if (Prefs.isPremium(this)) {
            txtStatus.text = getString(R.string.premium_already_have)
            btnBuy.isEnabled = false
        } else {
            txtStatus.text = getString(R.string.premium_not_active)
            btnBuy.isEnabled = true
        }
    }
}