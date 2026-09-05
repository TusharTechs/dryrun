package com.dryrun.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dryrun.app.models.ActivityType
import com.dryrun.app.models.SeedScenario
import com.dryrun.app.models.SeedScenarios

/**
 * The seven scenarios, grouped under the three kinds of conversation this is
 * for. Grouped visibly on purpose: the coverage should be obvious at a glance
 * without playing through anything.
 */
@Composable
fun ScenarioPickerScreen(
    onPick: (SeedScenario) -> Unit,
    onWriteMyOwn: () -> Unit,
    onBack: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeContentPadding()) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp)
            ) {
                item {
                    Text("Pick one", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Or write the one you're actually dreading.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                }

                ActivityType.entries.forEach { activity ->
                    item {
                        Text(
                            activity.label.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp, top = 6.dp)
                        )
                    }
                    SeedScenarios.byActivity(activity).forEach { scenario ->
                        item {
                            ScenarioCard(scenario) { onPick(scenario) }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    item { Spacer(Modifier.height(14.dp)) }
                }
            }

            Surface(tonalElevation = 2.dp) {
                Column(Modifier.padding(20.dp)) {
                    Button(
                        onClick = onWriteMyOwn,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Write my own") }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Back")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioCard(scenario: SeedScenario, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(scenario.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                scenario.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
