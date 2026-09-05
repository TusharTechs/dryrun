package com.dryrun.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * When is it, for a conversation picked from the library.
 *
 * The written-your-own path always asked this as its third step. The library
 * path used to skip it and silently assume tomorrow, so the home screen showed
 * a date that nothing would ever remind you about. Both paths now end the same
 * way: choose a slot, then get asked once about the nudge.
 */
@Composable
fun ScheduleScreen(
    counterpartRole: String,
    onBack: () -> Unit,
    onDone: (whenMillis: Long, wantsReminder: Boolean) -> Unit
) {
    var whenMillis by remember { mutableStateOf(defaultSlot()) }
    var askAboutReminder by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeContentPadding().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    counterpartRole.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                StepWhen(whenMillis) { whenMillis = it }
            }

            Column {
                Text(
                    "What you type stays on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { askAboutReminder = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Start the run") }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
        }
    }

    if (askAboutReminder) {
        ReminderPrompt { wantsReminder ->
            askAboutReminder = false
            onDone(whenMillis, wantsReminder)
        }
    }
}
