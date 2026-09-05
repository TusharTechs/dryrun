package com.dryrun.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dryrun.app.notifications.LocalNotifier
import com.dryrun.app.notifications.OneSignalBridgeProvider
import com.dryrun.app.platform.atLocalTimeOfDay
import com.dryrun.app.platform.currentTimeMillis
import com.dryrun.app.platform.formatConversationTime

private const val DAY_MS = 24L * 60 * 60 * 1000

/** The three questions. Who, what, when. Nothing else. */
@Composable
fun OnboardingScreen(
    localNotifier: LocalNotifier,
    onComplete: (role: String, personality: String, situation: String, dateMillis: Long) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var role by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var situation by remember { mutableStateOf("") }
    var whenMillis by remember { mutableStateOf(defaultSlot()) }
    var askAboutReminder by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeContentPadding().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    text = "$step of 3",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                when (step) {
                    1 -> StepWho(role, personality, { role = it }, { personality = it })
                    2 -> StepWhat(situation) { situation = it }
                    else -> StepWhen(whenMillis) { whenMillis = it }
                }
            }

            Column {
                Text(
                    text = "What you type stays on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { if (step < 3) step++ else askAboutReminder = true },
                    enabled = when (step) {
                        1 -> role.isNotBlank()
                        2 -> situation.isNotBlank()
                        else -> true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (step < 3) "Next" else "Start the run")
                }
            }
        }
    }

    // The permission ask happens here, at the moment the date is set — never on launch.
    if (askAboutReminder) {
        AlertDialog(
            onDismissRequest = { askAboutReminder = false },
            title = { Text("Want a nudge the night before?") },
            text = {
                Text(
                    "One reminder the evening before, so you get a last run in. " +
                        "No streaks, no daily pestering."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askAboutReminder = false
                    localNotifier.requestPermission { granted ->
                        if (granted) {
                            localNotifier.scheduleEveningBeforeReminder("schedule_1", role, whenMillis)
                            localNotifier.scheduleFollowUp("schedule_1", whenMillis)
                        }
                    }
                    OneSignalBridgeProvider.instance?.requestPermission { }
                    onComplete(role, personality, situation, whenMillis)
                }) { Text("Yes, remind me") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askAboutReminder = false
                    onComplete(role, personality, situation, whenMillis)
                }) { Text("No thanks") }
            }
        )
    }
}

@Composable
private fun StepWho(
    role: String,
    personality: String,
    onRole: (String) -> Unit,
    onPersonality: (String) -> Unit
) {
    Column {
        Text("Who are you talking to?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = role,
            onValueChange = onRole,
            label = { Text("Their role") },
            placeholder = { Text("Senior engineer on my team") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = personality,
            onValueChange = onPersonality,
            label = { Text("What are they like? (optional)") },
            placeholder = { Text("Gets defensive fast. Talks over people.") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "The more specific you are, the less they behave like a chatbot.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepWhat(situation: String, onChange: (String) -> Unit) {
    Column {
        Text("What do you have to say?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = situation,
            onValueChange = onChange,
            label = { Text("The thing you're dreading") },
            placeholder = { Text("Their work has slipped for two months and they don't see it.") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepWhen(selected: Long, onSelect: (Long) -> Unit) {
    Column {
        Text("When is it?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        slots().forEach { slot ->
            val isSelected = slot.millis == selected
            Card(
                onClick = { onSelect(slot.millis) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text(
                    text = formatConversationTime(slot.millis),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

private data class Slot(val millis: Long)

private fun defaultSlot(): Long = atLocalTimeOfDay(currentTimeMillis() + DAY_MS, 9, 0)

private fun slots(): List<Slot> {
    val now = currentTimeMillis()
    return listOf(
        Slot(atLocalTimeOfDay(now + DAY_MS, 9, 0)),
        Slot(atLocalTimeOfDay(now + DAY_MS, 14, 0)),
        Slot(atLocalTimeOfDay(now + 2 * DAY_MS, 10, 0)),
        Slot(atLocalTimeOfDay(now + 7 * DAY_MS, 10, 0))
    )
}
