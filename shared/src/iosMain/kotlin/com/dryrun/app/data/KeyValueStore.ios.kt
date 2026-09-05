package com.dryrun.app.data

import platform.Foundation.NSUserDefaults

actual class KeyValueStore(private val defaults: NSUserDefaults) : KeyStore {
    actual override fun getString(key: String): String? = defaults.stringForKey(key)
    actual override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
    actual override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}

actual fun createKeyValueStore(): KeyValueStore = KeyValueStore(NSUserDefaults.standardUserDefaults)
