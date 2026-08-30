package com.darcloud.omarai.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.darcloud.omarai.BuildConfig
import com.darcloud.omarai.data.api.ApiResult
import com.darcloud.omarai.data.api.BillingVerificationRequest
import com.darcloud.omarai.data.api.OmarApiClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayProduct(
    val id: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
)

data class BillingUiState(
    val connecting: Boolean = false,
    val playServiceConnected: Boolean = false,
    val products: List<PlayProduct> = emptyList(),
    val verifiedEntitlement: String? = null,
    val entitlementEvidenceId: String? = null,
    val message: String = "Connecting to Google Play…",
)

/** Google Play is the payment UI; the Omar API is the sole entitlement authority. */
class BillingManager(
    context: Context,
    private val apiClient: OmarApiClient,
) : PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val productDetails = ConcurrentHashMap<String, ProductDetails>()
    private val mutableState = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = mutableState.asStateFlow()

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady || mutableState.value.connecting) return
        mutableState.value = mutableState.value.copy(connecting = true, message = "Connecting to Google Play…")
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    mutableState.value = mutableState.value.copy(
                        connecting = false,
                        playServiceConnected = true,
                        message = "Google Play connected. Prices and entitlements are verified before access is granted.",
                    )
                    queryProducts()
                    restorePurchases()
                } else {
                    mutableState.value = mutableState.value.copy(
                        connecting = false,
                        playServiceConnected = false,
                        message = userMessage(result, "Google Play Billing could not connect."),
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                mutableState.value = mutableState.value.copy(
                    connecting = false,
                    playServiceConnected = false,
                    message = "Google Play Billing disconnected. It will reconnect automatically.",
                )
            }
        })
    }

    private fun queryProducts() {
        val requested = PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(requested).build(),
        ) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.value = mutableState.value.copy(message = userMessage(result, "Paid plan prices are unavailable."))
                return@queryProductDetailsAsync
            }
            productDetails.clear()
            queryResult.productDetailsList.forEach { productDetails[it.productId] = it }
            val products = PRODUCT_IDS.mapNotNull { id ->
                productDetails[id]?.let { detail ->
                    val price = detail.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.lastOrNull()
                        ?.formattedPrice
                        ?: return@let null
                    PlayProduct(id, detail.title, detail.description, price)
                }
            }
            mutableState.value = mutableState.value.copy(
                products = products,
                message = if (products.isEmpty()) {
                    "Google Play connected, but the Pro and Business subscription products are not published for this tester."
                } else {
                    "Plans loaded from Google Play. Purchases activate only after server verification."
                },
            )
        }
    }

    fun launchPurchase(activity: Activity, product: PlayProduct): String? {
        if (!billingClient.isReady) {
            start()
            return "Google Play is reconnecting. Please retry in a moment."
        }
        val details = productDetails[product.id] ?: return "That plan is not currently available from Google Play."
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return "No eligible Google Play offer is available for this plan."
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build(),
        )
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) null
        else userMessage(result, "The Google Play purchase screen could not open.")
    }

    fun restorePurchases() {
        if (!billingClient.isReady) {
            start()
            return
        }
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.value = mutableState.value.copy(message = userMessage(result, "Purchases could not be restored."))
            } else if (purchases.isEmpty()) {
                mutableState.value = mutableState.value.copy(message = "No active Google Play subscriptions were found for this account.")
            } else {
                purchases.forEach(::processPurchase)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::processPurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                mutableState.value = mutableState.value.copy(message = "Purchase cancelled. No entitlement changed.")
            }
            else -> mutableState.value = mutableState.value.copy(message = userMessage(result, "Google Play did not complete the purchase."))
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            mutableState.value = mutableState.value.copy(message = "Payment is pending in Google Play. Access will not be granted until payment completes.")
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = purchase.products.firstOrNull { it in PRODUCT_IDS } ?: return
        mutableState.value = mutableState.value.copy(message = "Verifying the Google Play purchase with Omar AI…")
        scope.launch {
            when (val verification = apiClient.call {
                verifyGooglePlayPurchase(
                    UUID.randomUUID().toString(),
                    BillingVerificationRequest(BuildConfig.APPLICATION_ID, productId, purchase.purchaseToken),
                )
            }) {
                is ApiResult.Success -> {
                    val entitlement = verification.value.entitlement
                    mutableState.value = mutableState.value.copy(
                        verifiedEntitlement = entitlement.key.takeIf { entitlement.grantsAccess },
                        entitlementEvidenceId = verification.value.providerEvidence.referenceId,
                        message = if (entitlement.grantsAccess) {
                            "${entitlement.key.replaceFirstChar(Char::uppercase)} is active and server verified."
                        } else {
                            "Google Play verified the purchase, but lifecycle activation is still pending."
                        },
                    )
                }
                is ApiResult.Failure -> {
                    mutableState.value = mutableState.value.copy(
                        message = "Purchase verification failed: ${verification.userMessage} No paid access was granted.",
                    )
                }
            }
        }
    }

    private fun userMessage(result: BillingResult, fallback: String): String =
        result.debugMessage.takeIf { BuildConfig.DEBUG && it.isNotBlank() } ?: fallback

    private companion object {
        val PRODUCT_IDS = listOf("omar_ai_pro", "omar_ai_business")
    }
}
