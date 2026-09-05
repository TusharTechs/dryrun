package com.dryrun.app.data

import com.dryrun.app.models.Rehearsal
import com.dryrun.app.models.RunRecord
import kotlinx.serialization.json.Json
import kotlin.random.Random

private const val KEY_DEVICE_ID = "device_id"
private const val KEY_TOKEN = "token"
private const val KEY_REHEARSAL = "rehearsal"
private const val KEY_RUNS = "runs"

/** Everything the app remembers, all of it on this device. */
class DryRunStore(private val kv: KeyValueStore = createKeyValueStore()) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Stable per install. Used only to rate-limit; it identifies nobody. */
    fun deviceId(): String {
        kv.getString(KEY_DEVICE_ID)?.let { return it }
        val fresh = randomUuidV4()
        kv.putString(KEY_DEVICE_ID, fresh)
        return fresh
    }

    fun token(): String? = kv.getString(KEY_TOKEN)
    fun saveToken(token: String) = kv.putString(KEY_TOKEN, token)

    fun rehearsal(): Rehearsal? =
        kv.getString(KEY_REHEARSAL)?.let {
            runCatching { json.decodeFromString<Rehearsal>(it) }.getOrNull()
        }

    fun saveRehearsal(rehearsal: Rehearsal) =
        kv.putString(KEY_REHEARSAL, json.encodeToString(Rehearsal.serializer(), rehearsal))

    fun runs(): List<RunRecord> =
        kv.getString(KEY_RUNS)?.let {
            runCatching { json.decodeFromString<List<RunRecord>>(it) }.getOrNull()
        } ?: emptyList()

    fun saveRun(run: RunRecord) {
        val updated = runs() + run
        kv.putString(KEY_RUNS, json.encodeToString(updated))
    }

    /** Wipes the conversation and every run. Offered in the UI, and it means it. */
    fun forgetEverything() {
        kv.remove(KEY_REHEARSAL)
        kv.remove(KEY_RUNS)
    }
}

/** UUID v4 without pulling in a dependency for it. */
internal fun randomUuidV4(): String {
    val hex = "0123456789abcdef"
    val sb = StringBuilder(36)
    for (i in 0 until 36) {
        when (i) {
            8, 13, 18, 23 -> sb.append('-')
            14 -> sb.append('4')
            19 -> sb.append(hex[(Random.nextInt(4) + 8)])
            else -> sb.append(hex[Random.nextInt(16)])
        }
    }
    return sb.toString()
}
