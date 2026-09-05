package com.dryrun.app.data

import com.dryrun.app.models.Rehearsal
import com.dryrun.app.models.RunRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

private const val KEY_DEVICE_ID = "device_id"
private const val KEY_TOKEN = "token"
private const val KEY_REHEARSALS = "rehearsals"
private const val KEY_ACTIVE_REHEARSAL = "active_rehearsal"
private const val KEY_RUNS = "runs"
private const val KEY_DRILL_LAST_DAY = "drill_last_day"

/** The pre-multi-conversation keys, read once so nobody loses their history. */
private const val LEGACY_KEY_REHEARSAL = "rehearsal"

/** Everything the app remembers, all of it on this device. */
class DryRunStore(private val kv: KeyStore = createKeyValueStore()) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        migrateSingleConversationIfNeeded()
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

    // ---- Conversations -----------------------------------------------------

    /** Every conversation being kept, soonest first. */
    fun rehearsals(): List<Rehearsal> =
        kv.getString(KEY_REHEARSALS)?.let {
            runCatching {
                json.decodeFromString(ListSerializer(Rehearsal.serializer()), it)
            }.getOrNull()
        }.orEmpty().sortedWith(
            compareBy<Rehearsal> { it.scheduledEpochMillis }.thenBy { it.createdAtMillis }
        )

    fun rehearsal(id: String): Rehearsal? = rehearsals().firstOrNull { it.id == id }

    /** The conversation the app opens on. Null before the first one exists. */
    fun activeRehearsal(): Rehearsal? {
        val all = rehearsals()
        if (all.isEmpty()) return null
        val id = kv.getString(KEY_ACTIVE_REHEARSAL)
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    fun setActiveRehearsal(id: String) = kv.putString(KEY_ACTIVE_REHEARSAL, id)

    /** Adds or replaces one conversation, and makes it the active one. */
    fun saveRehearsal(rehearsal: Rehearsal) {
        val updated = rehearsals().filterNot { it.id == rehearsal.id } + rehearsal
        writeRehearsals(updated)
        setActiveRehearsal(rehearsal.id)
    }

    /** Removes one conversation and every run belonging to it. */
    fun deleteRehearsal(id: String) {
        writeRehearsals(rehearsals().filterNot { it.id == id })
        writeRuns(allRuns().filterNot { it.rehearsalId == id })
        if (kv.getString(KEY_ACTIVE_REHEARSAL) == id) {
            rehearsals().firstOrNull()?.let { setActiveRehearsal(it.id) }
                ?: kv.remove(KEY_ACTIVE_REHEARSAL)
        }
    }

    // ---- Runs --------------------------------------------------------------

    /** Every run on the device, across all conversations. Drives the free tier. */
    fun allRuns(): List<RunRecord> =
        kv.getString(KEY_RUNS)?.let {
            runCatching {
                json.decodeFromString(ListSerializer(RunRecord.serializer()), it)
            }.getOrNull()
        }.orEmpty()

    /**
     * The runs for one conversation, oldest first. Everything the user is
     * shown -- numbering, history, the before/after card -- reads through
     * here, so two conversations can never be compared against each other.
     */
    fun runs(rehearsalId: String): List<RunRecord> =
        allRuns().filter { it.rehearsalId == rehearsalId }

    fun saveRun(run: RunRecord) = writeRuns(allRuns() + run)

    /** Wipes every conversation and every run. Offered in the UI, and it means it. */
    fun forgetEverything() {
        kv.remove(KEY_REHEARSALS)
        kv.remove(KEY_ACTIVE_REHEARSAL)
        kv.remove(KEY_RUNS)
        kv.remove(LEGACY_KEY_REHEARSAL)
    }

    // ---- Today's line ------------------------------------------------------

    /** The last local day the drill was finished, or null if never. */
    fun drillLastCompletedDay(): Long? = kv.getString(KEY_DRILL_LAST_DAY)?.toLongOrNull()

    fun markDrillCompleted(dayIndex: Long) =
        kv.putString(KEY_DRILL_LAST_DAY, dayIndex.toString())

    // ---- Internals ---------------------------------------------------------

    private fun writeRehearsals(list: List<Rehearsal>) =
        kv.putString(KEY_REHEARSALS, json.encodeToString(ListSerializer(Rehearsal.serializer()), list))

    private fun writeRuns(list: List<RunRecord>) =
        kv.putString(KEY_RUNS, json.encodeToString(ListSerializer(RunRecord.serializer()), list))

    /**
     * Earlier builds kept one conversation under its own key and a flat run
     * list with no owner. Fold that into the new shape once: the conversation
     * becomes the first of the list, and its orphaned runs are adopted by it
     * rather than being stranded or double-counted.
     */
    private fun migrateSingleConversationIfNeeded() {
        val legacy = kv.getString(LEGACY_KEY_REHEARSAL) ?: return
        val old = runCatching { json.decodeFromString(Rehearsal.serializer(), legacy) }.getOrNull()
        if (old != null && rehearsals().none { it.id == old.id }) {
            writeRehearsals(rehearsals() + old)
            setActiveRehearsal(old.id)
            val adopted = allRuns().map {
                if (it.rehearsalId.isBlank()) it.copy(rehearsalId = old.id) else it
            }
            writeRuns(adopted)
        }
        kv.remove(LEGACY_KEY_REHEARSAL)
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
