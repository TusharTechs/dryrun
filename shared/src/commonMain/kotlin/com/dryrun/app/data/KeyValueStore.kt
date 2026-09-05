package com.dryrun.app.data

/**
 * Device-local storage. Nothing here ever leaves the phone.
 *
 * The free-text situation the user types describes a real colleague, so it is
 * never persisted server-side -- the Worker sees it only for the lifetime of
 * the request that needs it.
 */
/**
 * The storage contract, separate from the platform class so everything that
 * reads and writes -- including the tests -- can work against an ordinary
 * interface rather than an expect class.
 */
interface KeyStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

expect class KeyValueStore : KeyStore {
    override fun getString(key: String): String?
    override fun putString(key: String, value: String)
    override fun remove(key: String)
}

expect fun createKeyValueStore(): KeyValueStore
