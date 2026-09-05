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
import com.dryrun.app.data.randomUuidV4
import com.dryrun.app.drill.DailyDrill
import com.dryrun.app.models.Rehearsal
import com.dryrun.app.models.SeedScenario
import com.dryrun.app.models.RunRecord
import com.dryrun.app.notifications.LocalNotifier
import com.dryrun.app.platform.currentTimeMillis
import com.dryrun.app.platform.localDayIndex
import com.dryrun.app.ui.*
import com.dryrun.app.ui.theme.DryRunTheme
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Scenarios : Screen
    data object Conversations : Screen
    data class Schedule(val scenario: SeedScenario) : Screen
    data object Drill : Screen
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

        var rehearsal by remember { mutableStateOf(store.activeRehearsal()) }
        var conversations by remember { mutableStateOf(store.rehearsals()) }
        // Scoped to the open conversation; the free tier counts every run.
        var runs by remember { mutableStateOf(store.runs(rehearsal?.id.orEmpty())) }
        var totalRuns by remember { mutableStateOf(store.allRuns().size) }
        var drillDay by remember { mutableStateOf(store.drillLastCompletedDay()) }
        var screen by remember {
            // The library first, not a blank form: opening to seven concrete
            // conversations is a far easier start than an empty text field.
            mutableStateOf<Screen>(
                if (store.activeRehearsal() == null) Screen.Scenarios else Screen.Home
            )
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

        /** Re-reads everything the screens draw from, after any write. */
        fun refresh() {
            conversations = store.rehearsals()
            rehearsal = store.activeRehearsal()
            runs = store.runs(rehearsal?.id.orEmpty())
            totalRuns = store.allRuns().size
        }

        fun openConversation(fresh: Rehearsal) {
            store.saveRehearsal(fresh)
            refresh()
            screen = Screen.Home
        }

        fun startRun(scenarioCounterpart: String, scenarioSituation: String, scenarioId: String) {
            if (!FreeTier.canStartRun(totalRuns, plusActive)) {
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
            Screen.Onboarding -> {
                // Fresh per visit, so two hand-written conversations never
                // collide on one id -- and so their reminders stay separate.
                val newId = remember { "custom_" + randomUuidV4() }
                OnboardingScreen(localNotifier, newId) { role, personality, situation, whenMillis ->
                    openConversation(
                        Rehearsal(
                            id = newId,
                            counterpartRole = role,
                            counterpartPersonality = personality,
                            situation = situation,
                            scheduledEpochMillis = whenMillis,
                            createdAtMillis = currentTimeMillis()
                        )
                    )
                }
            }

            Screen.Home -> rehearsal?.let { active ->
                HomeScreen(
                    rehearsal = active,
                    runs = runs,
                    runsLeft = FreeTier.runsLeft(totalRuns, plusActive),
                    conversationCount = conversations.size,
                    drillDoneToday = drillDay == localDayIndex(currentTimeMillis()),
                    onStartRun = {
                        startRun(
                            scenarioCounterpart = describeCounterpart(active),
                            scenarioSituation = active.situation,
                            scenarioId = active.id
                        )
                    },
                    onOpenRun = { screen = Screen.Feedback(it) },
                    onSeeProgress = { screen = Screen.Progress },
                    onOpenDrill = { screen = Screen.Drill },
                    onSeeConversations = { screen = Screen.Conversations },
                    onForgetEverything = {
                        store.forgetEverything()
                        refresh()
                        screen = Screen.Scenarios
                    }
                )
            } ?: run { screen = Screen.Scenarios }

            Screen.Scenarios -> ScenarioPickerScreen(
                onPick = { scenario ->
                    // Picking a scenario already on the list reopens it rather
                    // than resetting it, so its runs and date survive.
                    val existing = conversations.firstOrNull { it.id == scenario.id }
                    if (existing != null) openConversation(existing)
                    else screen = Screen.Schedule(scenario)
                },
                onWriteMyOwn = { screen = Screen.Onboarding },
                onBack = if (conversations.isEmpty()) null else ({ screen = Screen.Home })
            )

            is Screen.Schedule -> {
                val scenario = current.scenario
                ScheduleScreen(
                    counterpartRole = scenario.counterpartRole,
                    onBack = { screen = Screen.Scenarios },
                    onDone = { whenMillis, wantsReminder ->
                        if (wantsReminder) {
                            scheduleReminders(
                                localNotifier, scenario.id, scenario.counterpartRole, whenMillis
                            )
                        }
                        openConversation(
                            Rehearsal(
                                id = scenario.id,
                                counterpartRole = scenario.counterpartRole,
                                counterpartPersonality = scenario.counterpartPersonality,
                                situation = scenario.description,
                                scheduledEpochMillis = whenMillis,
                                createdAtMillis = currentTimeMillis()
                            )
                        )
                    }
                )
            }

            Screen.Conversations -> ConversationsScreen(
                rehearsals = conversations,
                activeId = rehearsal?.id,
                runsFor = { store.runs(it) },
                onOpen = { openConversation(it) },
                onNew = { screen = Screen.Scenarios },
                onDelete = { target ->
                    localNotifier.cancelAll(target.id)
                    store.deleteRehearsal(target.id)
                    refresh()
                    if (store.activeRehearsal() == null) screen = Screen.Scenarios
                },
                onBack = { screen = Screen.Home }
            )

            Screen.Drill -> {
                val today = localDayIndex(currentTimeMillis())
                DrillScreen(
                    drill = DailyDrill.forDay(today),
                    alreadyDoneToday = drillDay == today,
                    onDone = {
                        store.markDrillCompleted(today)
                        drillDay = today
                        screen = Screen.Home
                    },
                    onBack = { screen = Screen.Home }
                )
            }

            Screen.Rehearsing -> {
                val active = session
                val activeRehearsal = rehearsal
                if (active == null || activeRehearsal == null) {
                    screen = Screen.Home
                } else {
                    val sessionState by active.state.collectAsState()

                    // Finishing is one function so the retry runs exactly the
                    // same path as the first attempt.
                    fun finishRun() {
                        scope.launch {
                            active.finish()?.let { finished ->
                                val record = finished.copy(rehearsalId = activeRehearsal.id)
                                store.saveRun(record)
                                refresh()
                                // Their own best line, handed back the morning of.
                                localNotifier.scheduleMorningOfNudge(
                                    scheduleId = activeRehearsal.id,
                                    bestLine = record.feedback.bestLine(),
                                    epochMillis = activeRehearsal.scheduledEpochMillis
                                )
                                screen = Screen.Feedback(record)
                            }
                        }
                    }

                    RehearsalScreen(
                        state = RehearsalUiState(
                            counterpartRole = activeRehearsal.counterpartRole,
                            turns = sessionState.turns,
                            isThinking = sessionState.isThinking,
                            error = sessionState.error
                        ),
                        onSend = { active.send(it) },
                        onFinish = { finishRun() },
                        onBack = { screen = Screen.Home }
                    )

                    if (sessionState.isScoring) ScoringOverlay()

                    // Scoring failed. The run is still in memory, so offer the
                    // retry here rather than dropping them back to a home
                    // screen with nothing to show for the conversation.
                    sessionState.scoringError?.let { message ->
                        AlertDialog(
                            onDismissRequest = { active.dismissScoringError() },
                            title = { Text("Couldn't score that run") },
                            text = { Text(message) },
                            confirmButton = {
                                TextButton(onClick = {
                                    active.dismissScoringError()
                                    finishRun()
                                }) { Text("Try again") }
                            },
                            dismissButton = {
                                TextButton(onClick = { active.dismissScoringError() }) {
                                    Text("Keep talking")
                                }
                            }
                        )
                    }
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
                        screen = if (FreeTier.shouldShowPaywall(totalRuns, plusActive)) {
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
