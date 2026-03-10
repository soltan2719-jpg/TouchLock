package com.example.touchlock

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
                Log.d(TAG, "Billing setup result: ${result.responseCode} - ${result.debugMessage}")

                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    isBillingReady = true
                    Toast.makeText(context, "Billing connected", Toast.LENGTH_SHORT).show()
                    checkPremium()
                } else {
                    isBillingReady = false
                    Toast.makeText(
                        context,
                        "Billing not ready: ${result.debugMessage}",
                        Toast.LENGTH_LONG
                    ).show()
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

        Log.d(TAG, "Launching purchase for product: $productId")

        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(query) { result, list ->
            Log.d(
                TAG,
                "Product query result for $productId: ${result.responseCode} - ${result.debugMessage}, found=${list.size}"
            )

            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Toast.makeText(
                    context,
                    "Product query failed: ${result.debugMessage}",
                    Toast.LENGTH_LONG
                ).show()
                return@queryProductDetailsAsync
            }

            if (list.isEmpty()) {
                Toast.makeText(
                    context,
                    "Product not found: $productId",
                    Toast.LENGTH_LONG
                ).show()
                return@queryProductDetailsAsync
            }

            val product = list[0]

            val params = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .build()

            val flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(params))
                .build()

            val launchResult = billingClient.launchBillingFlow(activity, flow)
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
            purchases?.forEach {
                handlePurchase(it)
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

    fun checkPremium() {
        if (!::billingClient.isInitialized || !isBillingReady) return

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, list ->
            Log.d(TAG, "Check premium result: ${result.responseCode} - ${result.debugMessage}")

            var hasPremium = false
            list.forEach {
                if (it.products.contains(premiumId)) {
                    hasPremium = true
                }
            }

            Prefs.setPremium(context, hasPremium)
        }
    }
}