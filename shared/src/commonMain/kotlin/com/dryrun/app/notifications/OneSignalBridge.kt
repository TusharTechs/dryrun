package com.dryrun.app.notifications

interface OneSignalBridge {
    fun requestPermission(onResult: (Boolean) -> Unit)
    fun setTag(key: String, value: String)
}

object OneSignalBridgeProvider {
    var instance: OneSignalBridge? = null
}