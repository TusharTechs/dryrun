package com.dryrun.app.billing

/**
 * The RevenueCat public SDK key for this platform.
 *
 * Returning a blank string is a supported state, not a bug: the app then runs
 * as a complete free app with no paywall anywhere. See [Plus].
 */
expect fun revenueCatApiKey(): String

/** Entitlement and offering identifiers as configured in the RevenueCat dashboard. */
object StoreIds {
    const val ENTITLEMENT = "plus"
}
