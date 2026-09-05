package com.dryrun.app.billing

// Google Play public SDK key from the RevenueCat dashboard. Blank until the
// products are live; a blank key leaves the app fully free rather than broken.
private const val ANDROID_API_KEY = ""

actual fun revenueCatApiKey(): String = ANDROID_API_KEY
