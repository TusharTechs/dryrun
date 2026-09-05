package com.dryrun.app.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FreeTierTest {

    @Test
    fun `free users get two full runs`() {
        assertTrue(FreeTier.canStartRun(completedRuns = 0, plusActive = false))
        assertTrue(FreeTier.canStartRun(completedRuns = 1, plusActive = false))
        assertFalse(FreeTier.canStartRun(completedRuns = 2, plusActive = false))
    }

    @Test
    fun `two free runs is enough to reach the before-after card`() {
        // The card needs a first run to compare against and a second to compare.
        assertTrue(FreeTier.canStartRun(completedRuns = 0, plusActive = false))
        assertTrue(FreeTier.canStartRun(completedRuns = 1, plusActive = false))
        assertFalse(
            FreeTier.shouldShowPaywall(completedRuns = 1, plusActive = false),
            "the wall must not appear before the comparison has been seen"
        )
        assertTrue(FreeTier.shouldShowPaywall(completedRuns = 2, plusActive = false))
    }

    @Test
    fun `plus is unlimited`() {
        assertTrue(FreeTier.canStartRun(completedRuns = 99, plusActive = true))
        assertNull(FreeTier.runsLeft(completedRuns = 99, plusActive = true))
        assertFalse(FreeTier.shouldShowPaywall(completedRuns = 99, plusActive = true))
    }

    @Test
    fun `a build with no store key is a complete free app, never a padlocked one`() {
        // Plus.isActive is true when billing is unavailable, so this is the
        // keyless case. Nothing is ever gated.
        val billingUnavailable = true
        assertTrue(FreeTier.canStartRun(completedRuns = 500, plusActive = billingUnavailable))
        assertFalse(FreeTier.shouldShowPaywall(completedRuns = 500, plusActive = billingUnavailable))
    }

    @Test
    fun `runs left counts down and never goes negative`() {
        assertEquals(2, FreeTier.runsLeft(completedRuns = 0, plusActive = false))
        assertEquals(1, FreeTier.runsLeft(completedRuns = 1, plusActive = false))
        assertEquals(0, FreeTier.runsLeft(completedRuns = 2, plusActive = false))
        assertEquals(0, FreeTier.runsLeft(completedRuns = 7, plusActive = false))
    }
}
