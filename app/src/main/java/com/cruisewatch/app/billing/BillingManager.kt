package com.cruisewatch.app.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BillingManager"
private const val PREFS_NAME = "cruisewatch_prefs"
private const val KEY_IS_SUBSCRIBED = "is_ad_free_subscribed"

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID_AD_FREE_MONTHLY = "ad_free_monthly"
        const val BASE_PLAN_ID_MONTHLY = "monthly"

        @Volatile
        private var INSTANCE: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetForTests() {
            INSTANCE = null
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isSubscribed = MutableStateFlow(prefs.getBoolean(KEY_IS_SUBSCRIBED, false))
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _formattedPrice = MutableStateFlow<String?>(null)
    val formattedPrice: StateFlow<String?> = _formattedPrice.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    private var isConnecting = false
    private val pendingConnectionCallbacks = mutableListOf<Pair<() -> Unit, () -> Unit>>()

    init {
        startConnection()
    }

    @Synchronized
    fun startConnection(
        onConnected: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
    ) {
        if (billingClient.isReady) {
            onConnected?.invoke()
            return
        }

        if (onConnected != null || onFailed != null) {
            pendingConnectionCallbacks.add(
                Pair(
                    onConnected ?: {},
                    onFailed ?: {}
                )
            )
        }

        if (isConnecting) {
            Log.d(TAG, "Billing client connection already in progress, queued callback")
            return
        }

        isConnecting = true
        Log.d(TAG, "Starting billing client connection...")

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val callbacks: List<Pair<() -> Unit, () -> Unit>>
                synchronized(this@BillingManager) {
                    isConnecting = false
                    callbacks = ArrayList(pendingConnectionCallbacks)
                    pendingConnectionCallbacks.clear()
                }

                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client setup finished successfully")
                    querySubscriptionDetails()
                    queryPurchases()
                    callbacks.forEach { (success, _) ->
                        try {
                            success()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in connection success callback", e)
                        }
                    }
                } else {
                    Log.w(TAG, "Billing setup failed with response code: ${billingResult.responseCode}")
                    callbacks.forEach { (_, fail) ->
                        try {
                            fail()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in connection failure callback", e)
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                val callbacks: List<Pair<() -> Unit, () -> Unit>>
                synchronized(this@BillingManager) {
                    isConnecting = false
                    callbacks = ArrayList(pendingConnectionCallbacks)
                    pendingConnectionCallbacks.clear()
                }
                Log.w(TAG, "Billing service disconnected.")
                callbacks.forEach { (_, fail) ->
                    try {
                        fail()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in connection disconnect callback", e)
                    }
                }
            }
        })
    }

    fun querySubscriptionDetails(onComplete: ((Boolean) -> Unit)? = null) {
        if (!billingClient.isReady) {
            startConnection(
                onConnected = { querySubscriptionDetails(onComplete) },
                onFailed = { onComplete?.invoke(false) }
            )
            return
        }

        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID_AD_FREE_MONTHLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, result ->
            val productDetailsList = result.productDetailsList
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !productDetailsList.isNullOrEmpty()) {
                val details = productDetailsList.firstOrNull { it.productId == PRODUCT_ID_AD_FREE_MONTHLY }
                _productDetails.value = details
                Log.d(TAG, "Loaded product details for ${details?.productId}")

                // Resolve formatted price from base plan / pricing phases
                val offer = details?.subscriptionOfferDetails?.firstOrNull()
                val pricingPhase = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()
                val price = pricingPhase?.formattedPrice
                if (price != null) {
                    _formattedPrice.value = price
                    Log.d(TAG, "Resolved monthly subscription price: $price")
                }
                onComplete?.invoke(true)
            } else {
                Log.w(
                    TAG,
                    "queryProductDetailsAsync failed: code=${billingResult.responseCode}, debugMessage=${billingResult.debugMessage}"
                )
                onComplete?.invoke(false)
            }
        }
    }

    fun queryPurchases(onComplete: ((Boolean) -> Unit)? = null) {
        if (!billingClient.isReady) {
            startConnection(
                onConnected = { queryPurchases(onComplete) },
                onFailed = { onComplete?.invoke(false) }
            )
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchasesList, onComplete)
            } else {
                Log.w(TAG, "queryPurchasesAsync failed: code=${billingResult.responseCode}")
                onComplete?.invoke(false)
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>, onComplete: ((Boolean) -> Unit)? = null) {
        var hasActiveSub = false

        for (purchase in purchases) {
            if (purchase.products.contains(PRODUCT_ID_AD_FREE_MONTHLY) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            ) {
                hasActiveSub = true
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
            }
        }

        updateSubscriptionState(hasActiveSub)
        onComplete?.invoke(hasActiveSub)
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully: ${purchase.orderId}")
                updateSubscriptionState(true)
            } else {
                Log.w(TAG, "Failed to acknowledge purchase: ${billingResult.responseCode}")
            }
        }
    }

    private fun updateSubscriptionState(active: Boolean) {
        _isSubscribed.value = active
        prefs.edit().putBoolean(KEY_IS_SUBSCRIBED, active).apply()
        Log.d(TAG, "Subscription status updated to: $active")
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        _isLoading.value = false
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled billing flow")
        } else {
            Log.w(TAG, "onPurchasesUpdated error: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}")
        }
    }

    fun launchBillingFlow(activity: Activity): Boolean {
        if (!billingClient.isReady) {
            Log.w(TAG, "launchBillingFlow: billingClient is not ready, reconnecting")
            startConnection(onConnected = { querySubscriptionDetails() })
            return false
        }

        val details = _productDetails.value
        if (details == null) {
            Log.w(TAG, "launchBillingFlow: productDetails not loaded yet")
            querySubscriptionDetails()
            return false
        }

        val offer = details.subscriptionOfferDetails?.firstOrNull()
        val offerToken = offer?.offerToken
        if (offerToken == null) {
            Log.w(TAG, "launchBillingFlow: no offer token available")
            return false
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        _isLoading.value = true
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _isLoading.value = false
            Log.w(TAG, "Failed to launch billing flow: code=${result.responseCode}")
            return false
        }
        return true
    }

    fun restorePurchases(onResult: (Boolean) -> Unit) {
        _isLoading.value = true
        queryPurchases { active ->
            _isLoading.value = false
            onResult(active)
        }
    }

    fun openManageSubscriptions(context: Context) {
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID_AD_FREE_MONTHLY&package=${context.packageName}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open manage subscription page", e)
        }
    }
}
