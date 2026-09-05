package com.dryrun.app.billing

// App Store public SDK key from the RevenueCat dashboard. Blank until the
// products are live; a blank key leaves the app fully free rather than broken.
private const val IOS_API_KEY = ""

actual fun revenueCatApiKey(): String = IOS_API_KEY
