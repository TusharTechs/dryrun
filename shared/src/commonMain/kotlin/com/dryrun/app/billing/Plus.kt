package com.dryrun.app.billing

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase
import com.revenuecat.purchases.kmp.ktx.awaitRestore
import com.revenuecat.purchases.kmp.models.Package
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the app knows about paying. The only file that imports a
 * RevenueCat type -- the rest of the app asks [isActive] and nothing else.
 *
 * Fails closed and quiet. A build with no store key is not a broken app with
 * padlocks over an empty sheet; it is a complete free app. Every gate treats
 * "billing unavailable" as "let them through".
 */
object Plus {

    private val _state = MutableStateFlow(PlusState())
    val state: StateFlow<PlusState> = _state.asStateFlow()

    /** True when the user has Plus, or when billing is unavailable at all. */
    val isActive: Boolean
        get() = _state.value.let { it.isSubscribed || !it.isAvailable }

    private var offerings: List<Offer> = emptyList()
    private var packagesById: Map<String, Package> = emptyMap()

    /** Call once at startup. A blank key leaves the app free and is not an error. */
    fun configure() {
        val key = revenueCatApiKey()
        if (key.isBlank()) {
            _state.value = PlusState(isAvailable = false)
            return
        }
        return try {
            Purchases.configure(PurchasesConfiguration(apiKey = key))
            _state.value = PlusState(isAvailable = true)
        } catch (_: Throwable) {
            // A misconfigured store must never take the app down with it.
            _state.value = PlusState(isAvailable = false)
        }
    }

    suspend fun refresh() {
        if (!_state.value.isAvailable) return
        try {
            val info = Purchases.sharedInstance.awaitCustomerInfo()
            _state.value = _state.value.copy(
                isSubscribed = info.entitlements.active.containsKey(StoreIds.ENTITLEMENT)
            )
        } catch (_: Throwable) {
            // Offline is not "not subscribed" -- leave the last known state alone.
        }
    }

    /** The offers to show on the paywall. Empty means: do not show a paywall. */
    suspend fun offers(): List<Offer> {
        if (!_state.value.isAvailable) return emptyList()
        if (offerings.isNotEmpty()) return offerings
        return try {
            val current = Purchases.sharedInstance.awaitOfferings().current
                ?: return emptyList()
            packagesById = current.availablePackages.associateBy { it.identifier }
            offerings = current.availablePackages.map { pkg ->
                Offer(
                    id = pkg.identifier,
                    price = pkg.storeProduct.price.formatted,
                    title = pkg.storeProduct.title,
                    freeTrialDays = FREE_TRIAL_DAYS
                )
            }
            offerings
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun purchase(offerId: String): PurchaseOutcome {
        val pkg = packagesById[offerId] ?: return PurchaseOutcome.Unavailable
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(pkg)
            val active = result.customerInfo.entitlements.active
                .containsKey(StoreIds.ENTITLEMENT)
            _state.value = _state.value.copy(isSubscribed = active)
            if (active) PurchaseOutcome.Success else PurchaseOutcome.Failed
        } catch (e: Throwable) {
            // A cancelled purchase is a normal thing a person does, not an error
            // to shout about.
            if (e.isUserCancellation()) PurchaseOutcome.Cancelled else PurchaseOutcome.Failed
        }
    }

    suspend fun restore(): Boolean {
        if (!_state.value.isAvailable) return false
        return try {
            val info = Purchases.sharedInstance.awaitRestore()
            val active = info.entitlements.active.containsKey(StoreIds.ENTITLEMENT)
            _state.value = _state.value.copy(isSubscribed = active)
            active
        } catch (_: Throwable) {
            false
        }
    }

    private fun Throwable.isUserCancellation(): Boolean =
        message?.contains("cancel", ignoreCase = true) == true

    /**
     * Deliberately long. RevenueCat's own 2026 benchmark puts trials of this
     * length at 42.5% conversion against 25.5% for trials under four days,
     * and almost nobody ships one.
     */
    const val FREE_TRIAL_DAYS = 21
}

data class PlusState(
    /** False when there is no store key, or the store could not be reached. */
    val isAvailable: Boolean = false,
    val isSubscribed: Boolean = false
)

data class Offer(
    val id: String,
    val price: String,
    val title: String,
    val freeTrialDays: Int
)

enum class PurchaseOutcome { Success, Cancelled, Failed, Unavailable }
