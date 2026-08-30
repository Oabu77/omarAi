package com.darcloud.omarai.data.billing

import android.app.Activity
import android.content.Context
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
import com.darcloud.omarai.data.api.ApiResult
import com.darcloud.omarai.data.api.BillingVerificationRequest
import com.darcloud.omarai.data.api.OmarApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class PlayProduct(
    val id: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    internal val details: ProductDetails,
    internal val offerToken: String,
)

data class BillingUiState(
    val connecting: Boolean = false,
    val playServiceConnected: Boolean = false,
    val products: List<PlayProduct> = emptyList(),
    val verifiedEntitlement: String? = null,
    val entitlementEvidenceId: String? = null,
    val message: String = "No server-verified entitlement.",
)

/**
 * Play Billing is only a purchase transport. This class never grants a local entitlement.
 * A plan becomes active in UI only after the configured backend verifies the purchase token
 * and supplies a provider evidence ID.
 */
class BillingManager(
    private val context: Context,
    private val apiClient: OmarApiClient,
) : PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = mutableState.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (client.isReady || mutableState.value.connecting) return
        mutableState.value = mutableState.value.copy(connecting = true, message = "Connecting to Google Play…")
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    mutableState.value = mutableState.value.copy(
                        connecting = false,
                        playServiceConnected = true,
                        message = "Google Play connected. Entitlements still require server verification.",
                    )
                    queryProducts()
                    restorePurchases()
                } else {
                    mutableState.value = mutableState.value.copy(
                        connecting = false,
                        playServiceConnected = false,
                        message = "Google Play Billing unavailable: ${result.debugMessage}",
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                mutableState.value = mutableState.value.copy(
                    connecting = false,
                    playServiceConnected = false,
                    message = "Google Play Billing disconnected. No entitlement changed.",
                )
            }
        })
    }

    private fun queryProducts() {
        val products = PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.value = mutableState.value.copy(
                    products = emptyList(),
                    message = "Subscription products were not returned by Google Play.",
                )
                return@queryProductDetailsAsync
            }
            val display = detailsResult.productDetailsList.mapNotNull { details ->
                val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return@mapNotNull null
                val price = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice
                    ?: return@mapNotNull null
                PlayProduct(
                    id = details.productId,
                    title = details.title,
                    description = details.description,
                    formattedPrice = price,
                    details = details,
                    offerToken = offer.offerToken,
                )
            }
            mutableState.value = mutableState.value.copy(
                products = display,
                message = if (display.isEmpty()) {
                    "No active subscription offers were returned by Google Play."
                } else mutableState.value.message,
            )
        }
    }

    fun launchPurchase(activity: Activity, product: PlayProduct): String? {
        if (!apiClient.isConfigured) return "Connect the entitlement-verification service before purchasing."
        if (!apiClient.hasAuthenticatedSession) return "A verified sign-in session is required before purchasing."
        if (!client.isReady) return "Google Play Billing is not connected."
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product.details)
            .setOfferToken(product.offerToken)
            .build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build(),
        )
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) null
        else "Purchase flow did not start: ${result.debugMessage}"
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::verify)
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                mutableState.value = mutableState.value.copy(message = "Purchase cancelled. No entitlement changed.")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restorePurchases()
            else -> mutableState.value = mutableState.value.copy(
                message = "Google Play did not complete the purchase: ${result.debugMessage}",
            )
        }
    }

    fun restorePurchases() {
        if (!client.isReady) {
            start()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.isEmpty()) {
                    mutableState.value = mutableState.value.copy(
                        verifiedEntitlement = null,
                        entitlementEvidenceId = null,
                        message = "No Google Play purchases found. No entitlement is active.",
                    )
                } else purchases.forEach(::verify)
            } else {
                mutableState.value = mutableState.value.copy(
                    message = "Could not restore purchases: ${result.debugMessage}",
                )
            }
        }
    }

    private fun verify(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> {
                mutableState.value = mutableState.value.copy(
                    message = "Purchase pending in Google Play. No entitlement has been granted.",
                )
            }
            Purchase.PurchaseState.PURCHASED -> scope.launch {
                val productId = purchase.products.firstOrNull()
                if (productId == null) {
                    mutableState.value = mutableState.value.copy(message = "Purchase had no product ID; no entitlement granted.")
                    return@launch
                }
                mutableState.value = mutableState.value.copy(message = "Purchase received. Waiting for backend verification…")
                when (val response = apiClient.call {
                    verifyGooglePlayPurchase(
                        UUID.randomUUID().toString(),
                        BillingVerificationRequest(context.packageName, productId, purchase.purchaseToken),
                    )
                }) {
                    is ApiResult.Success -> {
                        val value = response.value
                        val active = value.entitlement.state.equals("active", ignoreCase = true) &&
                            value.entitlement.grantsAccess &&
                            value.providerEvidence.state == "PROVIDER_VERIFIED" &&
                            value.entitlement.key.isNotBlank() &&
                            !value.providerEvidence.referenceId.isNullOrBlank()
                        mutableState.value = if (active) {
                            mutableState.value.copy(
                                verifiedEntitlement = value.entitlement.key,
                                entitlementEvidenceId = value.providerEvidence.referenceId,
                                message = "Entitlement verified by the backend.",
                            )
                        } else {
                            mutableState.value.copy(
                                verifiedEntitlement = null,
                                entitlementEvidenceId = null,
                                message = "Backend did not grant access with an active entitlement and provider evidence.",
                            )
                        }
                    }
                    is ApiResult.Failure -> mutableState.value = mutableState.value.copy(
                        verifiedEntitlement = null,
                        entitlementEvidenceId = null,
                        message = "Verification failed: ${response.userMessage} No entitlement was granted.",
                    )
                }
            }
            else -> mutableState.value = mutableState.value.copy(message = "Purchase is not complete. No entitlement changed.")
        }
    }

    companion object {
        val PRODUCT_IDS = listOf("omar_ai_pro", "omar_ai_business")
    }
}
