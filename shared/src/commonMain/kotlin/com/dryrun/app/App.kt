package com.dryrun.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dryrun.app.billing.FreeTier
import com.dryrun.app.billing.Offer
import com.dryrun.app.billing.Plus
import com.dryrun.app.billing.PurchaseOutcome
import com.dryrun.app.coach.RunComparison
import com.dryrun.app.data.DryRunApi
import com.dryrun.app.data.DryRunStore
import com.dryrun.app.models.Rehearsal
import com.dryrun.app.models.RunRecord
import com.dryrun.app.notifications.LocalNotifier
import com.dryrun.app.platform.currentTimeMillis
import com.dryrun.app.ui.*
import com.dryrun.app.ui.theme.DryRunTheme
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Scenarios : Screen
    data object Rehearsing : Screen
    data class Feedback(val run: RunRecord) : Screen
    data object Progress : Screen
    data object Paywall : Screen
}

@Composable
fun App(localNotifier: LocalNotifier) {
    DryRunTheme {
        val scope = rememberCoroutineScope()
        val store = remember { DryRunStore() }
        val api = remember { DryRunApi(store) }

        var rehearsal by remember { mutableStateOf(store.rehearsal()) }
        var runs by remember { mutableStateOf(store.runs()) }
        var screen by remember {
            mutableStateOf<Screen>(if (store.rehearsal() == null) Screen.Onboarding else Screen.Home)
        }
        var session by remember { mutableStateOf<RehearsalSession?>(null) }
        var offers by remember { mutableStateOf<List<Offer>>(emptyList()) }
        var purchasing by remember { mutableStateOf(false) }
        var purchaseError by remember { mutableStateOf<String?>(null) }

        val plusState by Plus.state.collectAsState()
        // Plus.isActive is deliberately true when billing is unavailable, so a
        // build with no store key is a complete free app rather than a locked one.
        val plusActive = plusState.isSubscribed || !plusState.isAvailable

        LaunchedEffect(Unit) {
            Plus.configure()
            Plus.refresh()
        }

        fun startRun(scenarioCounterpart: String, scenarioSituation: String, scenarioId: String) {
            if (!FreeTier.canStartRun(runs.size, plusActive)) {
                scope.launch { offers = Plus.offers() }
                screen = Screen.Paywall
                return
            }
            session = RehearsalSession(
                api = api,
                scope = scope,
                scenarioId = scenarioId,
                counterpart = scenarioCounterpart,
                situation = scenarioSituation,
                runNumber = runs.size + 1,
                // The counterpart gets harder once they've beaten it once.
                difficulty = if (runs.size >= 2) "harder" else "normal"
            ).also { it.start() }
            screen = Screen.Rehearsing
        }

        when (val current = screen) {
            Screen.Onboarding -> OnboardingScreen(localNotifier) { role, personality, situation, whenMillis ->
                val fresh = Rehearsal(
                    counterpartRole = role,
                    counterpartPersonality = personality,
                    situation = situation,
                    scheduledEpochMillis = whenMillis
                )
                store.saveRehearsal(fresh)
                rehearsal = fresh
                screen = Screen.Home
            }

            Screen.Home -> rehearsal?.let { active ->
                HomeScreen(
                    rehearsal = active,
                    runs = runs,
                    runsLeft = FreeTier.runsLeft(runs.size, plusActive),
                    onStartRun = {
                        startRun(
                            scenarioCounterpart = describeCounterpart(active),
                            scenarioSituation = active.situation,
                            scenarioId = active.id
                        )
                    },
                    onOpenRun = { screen = Screen.Feedback(it) },
                    onSeeProgress = { screen = Screen.Progress },
                    onChangeConversation = { screen = Screen.Scenarios },
                    onForgetEverything = {
                        store.forgetEverything()
                        runs = emptyList()
                        rehearsal = null
                        screen = Screen.Onboarding
                    }
                )
            } ?: run { screen = Screen.Onboarding }

            Screen.Scenarios -> ScenarioPickerScreen(
                onPick = { scenario ->
                    val fresh = Rehearsal(
                        id = scenario.id,
                        counterpartRole = scenario.counterpartRole,
                        counterpartPersonality = scenario.counterpartPersonality,
                        situation = scenario.description,
                        scheduledEpochMillis = rehearsal?.scheduledEpochMillis
                            ?: (currentTimeMillis() + 24L * 60 * 60 * 1000)
                    )
                    store.saveRehearsal(fresh)
                    rehearsal = fresh
                    screen = Screen.Home
                },
                onWriteMyOwn = { screen = Screen.Onboarding },
                onBack = { screen = Screen.Home }
            )

            Screen.Rehearsing -> {
                val active = session
                val activeRehearsal = rehearsal
                if (active == null || activeRehearsal == null) {
                    screen = Screen.Home
                } else {
                    val sessionState by active.state.collectAsState()

                    RehearsalScreen(
                        state = RehearsalUiState(
                            counterpartRole = activeRehearsal.counterpartRole,
                            turns = sessionState.turns,
                            isThinking = sessionState.isThinking,
                            error = sessionState.error
                        ),
                        onSend = { active.send(it) },
                        onFinish = {
                            scope.launch {
                                active.finish()?.let { record ->
                                    store.saveRun(record)
                                    runs = store.runs()
                                    // Their own best line, handed back the morning of.
                                    localNotifier.scheduleMorningOfNudge(
                                        scheduleId = "schedule_1",
                                        bestLine = record.feedback.bestLine(),
                                        epochMillis = activeRehearsal.scheduledEpochMillis
                                    )
                                    screen = Screen.Feedback(record)
                                }
                            }
                        },
                        onBack = { screen = Screen.Home }
                    )

                    if (sessionState.isScoring) ScoringOverlay()
                    sessionState.blocked?.let { message ->
                        AlertDialog(
                            onDismissRequest = { screen = Screen.Home },
                            title = { Text("Not this one") },
                            text = { Text(message) },
                            confirmButton = {
                                TextButton(onClick = { screen = Screen.Home }) { Text("OK") }
                            }
                        )
                    }
                }
            }

            is Screen.Feedback -> FeedbackScreen(
                run = current.run,
                canRunAgain = true,
                hasComparison = runs.size >= 2,
                onSeeProgress = { screen = Screen.Progress },
                onRunAgain = {
                    rehearsal?.let { active ->
                        startRun(describeCounterpart(active), active.situation, active.id)
                    }
                }
            )

            Screen.Progress -> {
                val first = runs.firstOrNull()
                val latest = runs.lastOrNull()
                if (first == null || latest == null || runs.size < 2) {
                    screen = Screen.Home
                } else {
                    ProgressScreen(RunComparison.compare(first, latest)) {
                        // The wall sits here, after the comparison has been seen.
                        screen = if (FreeTier.shouldShowPaywall(runs.size, plusActive)) {
                            scope.launch { offers = Plus.offers() }
                            Screen.Paywall
                        } else {
                            Screen.Home
                        }
                    }
                }
            }

            Screen.Paywall -> {
                // No offers means no store. Never show an empty sheet.
                if (offers.isEmpty() && !purchasing) {
                    LaunchedEffect(Unit) {
                        offers = Plus.offers()
                        if (offers.isEmpty()) screen = Screen.Home
                    }
                }
                PaywallScreen(
                    offers = offers,
                    isWorking = purchasing,
                    error = purchaseError,
                    onBuy = { offer ->
                        purchasing = true
                        purchaseError = null
                        scope.launch {
                            when (Plus.purchase(offer.id)) {
                                PurchaseOutcome.Success -> screen = Screen.Home
                                PurchaseOutcome.Cancelled -> Unit
                                PurchaseOutcome.Failed,
                                PurchaseOutcome.Unavailable ->
                                    purchaseError = "That didn't go through. Nothing was charged."
                            }
                            purchasing = false
                        }
                    },
                    onRestore = {
                        scope.launch {
                            purchasing = true
                            if (Plus.restore()) screen = Screen.Home
                            else purchaseError = "Nothing to restore on this account."
                            purchasing = false
                        }
                    },
                    onClose = { screen = Screen.Home }
                )
            }
        }
    }
}

/** What the model is told about who is in the room. */
private fun describeCounterpart(rehearsal: Rehearsal): String =
    if (rehearsal.counterpartPersonality.isBlank()) rehearsal.counterpartRole
    else "${rehearsal.counterpartRole}. ${rehearsal.counterpartPersonality}"

@Composable
private fun ScoringOverlay() {
    Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
