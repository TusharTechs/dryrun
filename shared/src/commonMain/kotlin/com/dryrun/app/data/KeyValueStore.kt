package com.dryrun.app.data

/**
 * Device-local storage. Nothing here ever leaves the phone.
 *
 * The free-text situation the user types describes a real colleague, so it is
 * never persisted server-side -- the Worker sees it only for the lifetime of
 * the request that needs it.
 */
expect class KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

expect fun createKeyValueStore(): KeyValueStore
