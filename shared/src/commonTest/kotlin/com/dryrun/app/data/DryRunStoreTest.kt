package com.dryrun.app.data

import com.dryrun.app.models.CriterionScore
import com.dryrun.app.models.FeedbackReport
import com.dryrun.app.models.Rehearsal
import com.dryrun.app.models.RunRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeKeyStore(
    private val map: MutableMap<String, String> = mutableMapOf()
) : KeyStore {
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    fun seed(key: String, value: String) { map[key] = value }
    fun has(key: String) = map.containsKey(key)
}

private fun rehearsal(id: String, at: Long = 1_000L) = Rehearsal(
    id = id,
    counterpartRole = "Role $id",
    situation = "Situation $id",
    scheduledEpochMillis = at
)

private fun run(rehearsalId: String, number: Int) = RunRecord(
    rehearsalId = rehearsalId,
    runNumber = number,
    transcript = emptyList(),
    feedback = FeedbackReport(
        criteria = listOf(CriterionScore("specific_behaviour", 2, "a line", "note")),
        overall = "fine",
        strongestLine = "a line"
    ),
    hedgeCount = 0,
    hedgeTopPhrase = "",
    silenceOffered = 0,
    silenceFilled = 0,
    completedAtMillis = 0L
)

class DryRunStoreTest {

    @Test
    fun runs_are_scoped_to_their_own_conversation() {
        val store = DryRunStore(FakeKeyStore())
        store.saveRehearsal(rehearsal("a"))
        store.saveRehearsal(rehearsal("b"))
        store.saveRun(run("a", 1))
        store.saveRun(run("a", 2))
        store.saveRun(run("b", 1))

        assertEquals(2, store.runs("a").size)
        assertEquals(1, store.runs("b").size)
        // The free tier counts the device, not the conversation.
        assertEquals(3, store.allRuns().size)
    }

    @Test
    fun saving_a_conversation_makes_it_the_active_one() {
        val store = DryRunStore(FakeKeyStore())
        store.saveRehearsal(rehearsal("a"))
        store.saveRehearsal(rehearsal("b"))
        assertEquals("b", store.activeRehearsal()?.id)

        store.setActiveRehearsal("a")
        assertEquals("a", store.activeRehearsal()?.id)
    }

    @Test
    fun deleting_a_conversation_takes_only_its_own_runs() {
        val store = DryRunStore(FakeKeyStore())
        store.saveRehearsal(rehearsal("a"))
        store.saveRehearsal(rehearsal("b"))
        store.saveRun(run("a", 1))
        store.saveRun(run("b", 1))

        store.deleteRehearsal("a")

        assertNull(store.rehearsal("a"))
        assertEquals(0, store.runs("a").size)
        assertEquals(1, store.runs("b").size)
        assertEquals("b", store.activeRehearsal()?.id)
    }

    @Test
    fun active_falls_back_when_the_stored_id_is_gone() {
        val kv = FakeKeyStore()
        val store = DryRunStore(kv)
        store.saveRehearsal(rehearsal("a"))
        kv.seed("active_rehearsal", "vanished")
        assertEquals("a", store.activeRehearsal()?.id)
    }

    @Test
    fun conversations_come_back_soonest_first() {
        val store = DryRunStore(FakeKeyStore())
        store.saveRehearsal(rehearsal("later", at = 9_000L))
        store.saveRehearsal(rehearsal("sooner", at = 2_000L))
        assertEquals(listOf("sooner", "later"), store.rehearsals().map { it.id })
    }

    @Test
    fun an_older_install_keeps_its_conversation_and_its_runs() {
        val kv = FakeKeyStore()
        // Exactly what the previous build wrote: one conversation under its own
        // key, and a flat run list with no owner recorded.
        kv.seed(
            "rehearsal",
            """{"id":"rehearsal_1","counterpartRole":"A designer","counterpartPersonality":"",""" +
                """"situation":"Work has slipped","scheduledEpochMillis":5000,"isCompleted":false}"""
        )
        kv.seed(
            "runs",
            """[{"runNumber":1,"transcript":[],"feedback":{"criteria":[],"overall":"ok",""" +
                """"strongestLine":""},"hedgeCount":7,"hedgeTopPhrase":"just",""" +
                """"silenceOffered":1,"silenceFilled":0,"completedAtMillis":1}]"""
        )

        val store = DryRunStore(kv)

        assertEquals("rehearsal_1", store.activeRehearsal()?.id)
        assertEquals("A designer", store.activeRehearsal()?.counterpartRole)
        // The orphaned run is adopted rather than stranded.
        assertEquals(1, store.runs("rehearsal_1").size)
        assertEquals(7, store.runs("rehearsal_1").first().hedgeCount)
        // And the old key is gone, so the fold happens exactly once.
        assertTrue(!kv.has("rehearsal"))
    }

    @Test
    fun forgetting_everything_leaves_nothing_behind() {
        val store = DryRunStore(FakeKeyStore())
        store.saveRehearsal(rehearsal("a"))
        store.saveRun(run("a", 1))

        store.forgetEverything()

        assertEquals(emptyList(), store.rehearsals())
        assertEquals(emptyList(), store.allRuns())
        assertNull(store.activeRehearsal())
    }

    @Test
    fun the_daily_drill_remembers_only_the_day_it_was_done() {
        val store = DryRunStore(FakeKeyStore())
        assertNull(store.drillLastCompletedDay())
        store.markDrillCompleted(20_000L)
        assertEquals(20_000L, store.drillLastCompletedDay())
    }
}
