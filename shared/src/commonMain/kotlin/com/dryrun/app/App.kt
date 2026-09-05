package com.dryrun.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.dryrun.app.billing.Plus
import com.dryrun.app.models.Rehearsal
import com.dryrun.app.notifications.LocalNotifier
import com.dryrun.app.ui.OnboardingScreen
import com.dryrun.app.ui.PlaceholderHomeScreen

@Composable
fun App(localNotifier: LocalNotifier) {
    MaterialTheme {
        // Blank store key leaves the app fully free rather than broken.
        LaunchedEffect(Unit) {
            Plus.configure()
            Plus.refresh()
        }

        var rehearsal by remember { mutableStateOf<Rehearsal?>(null) }

        val current = rehearsal
        if (current == null) {
            OnboardingScreen(localNotifier) { role, personality, situation, whenMillis ->
                rehearsal = Rehearsal(
                    counterpartRole = role,
                    counterpartPersonality = personality,
                    situation = situation,
                    scheduledEpochMillis = whenMillis
                )
            }
        } else {
            PlaceholderHomeScreen(current) { rehearsal = null }
        }
    }
}
