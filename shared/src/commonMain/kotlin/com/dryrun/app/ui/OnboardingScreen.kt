package com.dryrun.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dryrun.app.notifications.LocalNotifier
import com.dryrun.app.notifications.OneSignalBridgeProvider

@Composable
fun OnboardingScreen(
    localNotifier: LocalNotifier,
    onComplete: (role: String, text: String, dateMillis: Long) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var role by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var situationText by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 86400000) }
    var showPrePrompt by remember { mutableStateOf(&alse) }

    Surface(modifier = Modifier.fillMaxSize(), color= MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "step $step of 3",
                style = MaterialTheme.typography.labelLarge,
                color= MaterialTheme.colorScheme.secondary
            )

            when (step) {
                1 -> StepOne(role, personality, { role = it }, { personality = it })
                2 -> StepTwo(situationText) { situationText = it }
                3 -> StepThree(selectedDateMillis) { selectedDateMillis = it }
            }

            Button(
                onClick = {
                    if (step < 3) {
                        step++
                    } else {
                        showPrePrompt = true
                    }
                },
                enabled = when (step) {
                    1 -> role.isNotBlank()
                    2 -> situationText.isNotBlank()
                    else -> true
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (step < 3) "Next" else "Schedule & Practice")
            }
        }
    }

    if (showPrePrompt) {
        AlertDialog(
            onDismissRequest = { showPrePrompt = false },
            title = { Text("Stay on track?") },
            text = { Text("We can send a single nudge the evening before your conversation so you can fit in one last run-through.") },
            confirmButton = {
                TextButton(onClick = {
                    showPrePrompt = false
                    localNotifier.scheduleEveningBeforeReminder("schedule_1", role, selectedDateMillis)
                    OneSignalBridgeProvider.instance?.requestPermission { accepted ->
                        println("OneSignal permission accepted: $accepted")
                    }
                    onComplete(role, situationText, selectedDateMillis)
                }) {
                    Text("Enable Reminders")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPrePrompt = false
                    onComplete(role, situationText, selectedDateMillis)
                }) {
                    Text("Not Now")
                }
            }
        )
    }
}

@Composable
private fun StepOne(role: String, personality: String, onRoleChange: (String) -> Unit, onPersChange: (String) -> Unit) {
    Column {
        Text("Who are you talking to?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = role,
            onValueChange = onRoleChange,
            label = { Text("Their role (e.g., Tech Lead)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = personality,
            onValueChange = onPersChange,
            label = { Text("What are they like? (e.g., gets defensive fast)") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepTwo(text: String, onChange: (String) -> Unit) {
    Column {
        Text("What do you need to say?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            label = { Text("The core message you're dreading") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            maxLines = 5
        )
    }
}

@Composable
private fun StepThree(dateMillis: Long, onDateSelected: (Long) -> Unit) {
    Column {
        Text("When is the conversation?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Scheduled for 24 hours from now",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}