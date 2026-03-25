package com.noureddine.touchlock

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*

class BillingManager(
    private val context: Context,
    private val activity: Activity
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
    }

    private lateinit var billingClient: BillingClient
    private var isBillingReady = false

    private val premiumId = "premium_upgrade"
    private val tipIds = listOf("tip_1", "tip_2", "tip_5")

    fun start() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    isBillingReady = true
                    // Check for existing purchases immediately upon connection
                    checkPremium()
                }
            }

            override fun onBillingServiceDisconnected() {
                isBillingReady = false
            }
        })
    }

    fun buyPremium() = launchPurchase(premiumId)
    fun buyTip1() = launchPurchase("tip_1")
    fun buyTip2() = launchPurchase("tip_2")
    fun buyTip5() = launchPurchase("tip_5")

    private fun launchPurchase(productId: String) {
        if (!isBillingReady) {
            Toast.makeText(context, "Billing not ready", Toast.LENGTH_SHORT).show()
            return
        }

        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )).build()

        billingClient.queryProductDetailsAsync(query) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetailsList[0])
                            .build()
                    )).build()
                billingClient.launchBillingFlow(activity, flowParams)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        // 1. Grant the Item
        if (purchase.products.contains(premiumId)) {
            Prefs.setPremium(context, true)
            Toast.makeText(context, "Premium activated!", Toast.LENGTH_SHORT).show()
            acknowledgePurchase(purchase)
        } else {
            // It's a tip - these must be CONSUMED so they can be bought again
            consumePurchase(purchase)
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { Log.d(TAG, "Acknowledged") }
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(params) { result, _ ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Toast.makeText(context, "Thank you for your support! ❤️", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun checkPremium(onResult: ((Boolean) -> Unit)? = null) {
        if (!isBillingReady) {
            onResult?.invoke(Prefs.isPremium(context))
            return
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { _, purchases ->
            val hasPremium = purchases.any { it.products.contains(premiumId) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            Prefs.setPremium(context, hasPremium)
            onResult?.invoke(hasPremium)
        }
    }

    fun restorePurchase(onResult: (Boolean) -> Unit) {
        if (!isBillingReady) {
            Toast.makeText(context, "Billing service not ready", Toast.LENGTH_SHORT).show()
            onResult(Prefs.isPremium(context))
            return
        }
        checkPremium { hasPremium ->
            if (!hasPremium) {
                Toast.makeText(context, "No previous purchases found", Toast.LENGTH_SHORT).show()
            }
            onResult(hasPremium)
        }
    }

    fun endConnection() {
        if (::billingClient.isInitialized) billingClient.endConnection()
    }
}
