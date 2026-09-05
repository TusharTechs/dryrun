package com.dryrun.app.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlusStateTest {

    @Test
    fun `the trial is long on purpose`() {
        assertTrue(
            Plus.FREE_TRIAL_DAYS in 17..32,
            "RevenueCat's own benchmark shows long trials convert far better"
        )
        assertEquals(21, Plus.FREE_TRIAL_DAYS)
    }

    @Test
    fun `entitlement id is stable`() {
        // Changing this silently unsubscribes every paying user.
        assertEquals("plus", StoreIds.ENTITLEMENT)
    }
}
