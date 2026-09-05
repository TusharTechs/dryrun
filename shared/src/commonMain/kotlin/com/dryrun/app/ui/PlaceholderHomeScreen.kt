package com.dryrun.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dryrun.app.models.Rehearsal
import com.dryrun.app.platform.formatConversationTime

/**
 * Temporary landing spot so the app is launchable end-to-end while the
 * rehearsal screen is being built. Replaced in the rehearsal phase.
 */
@Composable
fun PlaceholderHomeScreen(rehearsal: Rehearsal, onStartOver: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Your run", style = MaterialTheme.typography.headlineMedium)
            Text(
                formatConversationTime(rehearsal.scheduledEpochMillis),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text("Talking to", style = MaterialTheme.typography.labelMedium)
            Text(rehearsal.counterpartRole, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text("What you have to say", style = MaterialTheme.typography.labelMedium)
            Text(rehearsal.situation, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Start over")
            }
        }
    }
}
