package com.example.touchlock

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

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
    private val tip1 = "tip_1"
    private val tip2 = "tip_2"
    private val tip5 = "tip_5"

    fun start() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    isBillingReady = true
                    Log.d(TAG, "Billing connected")
                } else {
                    isBillingReady = false
                    Log.d(TAG, "Billing not ready: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                isBillingReady = false
                Log.d(TAG, "Billing disconnected")
            }
        })
    }

    fun buyPremium() {
        launchPurchase(premiumId)
    }

    fun buyTip1() {
        launchPurchase(tip1)
    }

    fun buyTip2() {
        launchPurchase(tip2)
    }

    fun buyTip5() {
        launchPurchase(tip5)
    }

    private fun launchPurchase(productId: String) {
        if (!::billingClient.isInitialized || !isBillingReady) {
            Toast.makeText(context, "Billing is not ready yet", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Billing not ready for product: $productId")
            return
        }

        // FIX: Changed BillingClient.ProductType.INAPP to "inapp" to fix K2 compiler crash
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType("inapp")
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(query) { result, productDetailsList ->
            Log.d(
                TAG,
                "Product query result for $productId: ${result.responseCode} - ${result.debugMessage}, found=${productDetailsList.size}"
            )

            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Toast.makeText(
                    context,
                    "Product query failed: ${result.debugMessage}",
                    Toast.LENGTH_LONG
                ).show()
                return@queryProductDetailsAsync
            }

            if (productDetailsList.isEmpty()) {
                Toast.makeText(
                    context,
                    "Product not found: $productId",
                    Toast.LENGTH_LONG
                ).show()
                return@queryProductDetailsAsync
            }

            val productDetails = productDetailsList.first()

            val productDetailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()

            val launchResult = billingClient.launchBillingFlow(activity, billingFlowParams)
            Log.d(
                TAG,
                "Launch billing flow result: ${launchResult.responseCode} - ${launchResult.debugMessage}"
            )
        }
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        Log.d(TAG, "Purchases updated: ${result.responseCode} - ${result.debugMessage}")

        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach { purchase ->
                handlePurchase(purchase)
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(context, "Purchase canceled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "Purchase failed: ${result.debugMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        Log.d(TAG, "Handling purchase: ${purchase.products}")

        if (purchase.products.contains(premiumId)) {
            Prefs.setPremium(context, true)
            Toast.makeText(context, "Premium activated", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Thanks for your support ❤️", Toast.LENGTH_LONG).show()
        }

        if (!purchase.isAcknowledged) {
            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(acknowledgeParams) { result ->
                Log.d(
                    TAG,
                    "Acknowledge result: ${result.responseCode} - ${result.debugMessage}"
                )
            }
        }
    }

    fun checkPremium(onResult: ((Boolean) -> Unit)? = null) {
        if (!::billingClient.isInitialized || !isBillingReady) {
            onResult?.invoke(Prefs.isPremium(context))
            return
        }

        // FIX: Changed BillingClient.ProductType.INAPP to "inapp" here as well
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType("inapp")
                .build()
        ) { result, purchasesList ->
            Log.d(TAG, "Check premium result: ${result.responseCode} - ${result.debugMessage}")

            val hasPremium = purchasesList.any { purchase ->
                purchase.products.contains(premiumId)
            }

            Prefs.setPremium(context, hasPremium)
            onResult?.invoke(hasPremium)
        }
    }

    fun restorePurchase(onRestored: (() -> Unit)? = null) {
        checkPremium { hasPremium ->
            if (hasPremium) {
                Toast.makeText(context, "Purchase restored", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No premium purchase found", Toast.LENGTH_SHORT).show()
            }
            onRestored?.invoke()
        }
    }

    fun endConnection() {
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
}
