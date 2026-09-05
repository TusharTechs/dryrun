package com.dryrun.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dryrun.app.models.Rehearsal
import com.dryrun.app.models.RunRecord
import com.dryrun.app.platform.formatConversationTime
import com.dryrun.app.ui.theme.LocalDryRunColors

@Composable
fun HomeScreen(
    rehearsal: Rehearsal,
    runs: List<RunRecord>,
    runsLeft: Int?,
    conversationCount: Int,
    drillDoneToday: Boolean,
    onStartRun: () -> Unit,
    onOpenRun: (RunRecord) -> Unit,
    onSeeProgress: () -> Unit,
    onOpenDrill: () -> Unit,
    onSeeConversations: () -> Unit,
    onForgetEverything: () -> Unit
) {
    val colors = LocalDryRunColors.current
    var confirmForget by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeContentPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Text(
                    formatConversationTime(rehearsal.scheduledEpochMillis).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(rehearsal.counterpartRole, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                Text(
                    rehearsal.situation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(32.dp))

                if (runs.isEmpty()) {
                    Text(
                        "Nothing rehearsed yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "YOUR RUNS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    runs.reversed().forEach { run ->
                        RunRow(run, colors.hedge) { onOpenRun(run) }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (runs.size >= 2) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onSeeProgress,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("Run 1 vs now") }
                    }
                }

                Spacer(Modifier.height(28.dp))
                DrillCard(drillDoneToday, onOpenDrill)

                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onSeeConversations) {
                    Text(
                        if (conversationCount <= 1) "Your conversations"
                        else "Your conversations ($conversationCount)"
                    )
                }
                TextButton(onClick = { confirmForget = true }) { Text("Delete everything") }
            }

            Surface(tonalElevation = 2.dp) {
                Column(Modifier.padding(20.dp)) {
                    if (runsLeft != null && runsLeft <= 1) {
                        Text(
                            if (runsLeft == 0) "No free runs left" else "1 free run left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Button(
                        onClick = onStartRun,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Text(if (runs.isEmpty()) "Start the run" else "Run it again")
                    }
                }
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Delete everything?") },
            text = { Text("Every conversation and every run, gone from this phone. There is no copy anywhere else.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    onForgetEverything()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Keep") }
            }
        )
    }
}

/**
 * The one thing here that is worth opening on a day with nothing scheduled.
 * Stated as an offer with its cost up front, and it looks the same whether or
 * not it has been done -- a finished state that nagged would be a streak.
 */
@Composable
private fun DrillCard(doneToday: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Today's line", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (doneToday) "Done today. Read it again if you like."
                    else "One softened sentence. Say it straight. 60 seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RunRow(
    run: RunRecord,
    hedgeColour: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Run ${run.runNumber}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (run.hedgeCount == 0) "No hedges" else "${run.hedgeCount} hedges",
                    style = MaterialTheme.typography.bodySmall,
                    color = hedgeColour
                )
            }
            Text(
                "${run.feedback.total}/8",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
