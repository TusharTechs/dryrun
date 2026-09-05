package com.dryrun.app.data

import android.content.Context
import android.content.SharedPreferences

actual class KeyValueStore(private val prefs: SharedPreferences) : KeyStore {
    actual override fun getString(key: String): String? = prefs.getString(key, null)
    actual override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    actual override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/** Set once by MainActivity so the store only ever needs an application Context. */
object AppContextHolder {
    var applicationContext: Context? = null
}

actual fun createKeyValueStore(): KeyValueStore {
    val context = AppContextHolder.applicationContext
        ?: error("AppContextHolder.applicationContext must be set in MainActivity.onCreate")
    return KeyValueStore(context.getSharedPreferences("dryrun", Context.MODE_PRIVATE))
}
