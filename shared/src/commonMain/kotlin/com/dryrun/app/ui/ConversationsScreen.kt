package com.dryrun.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dryrun.app.models.Rehearsal
import com.dryrun.app.models.RunRecord
import com.dryrun.app.platform.formatConversationTime

/**
 * Every conversation being kept, soonest first.
 *
 * People do not have one hard conversation; they have the one this week and
 * the one they keep putting off. Each keeps its own runs, so switching
 * between them never renumbers or re-compares anything.
 */
@Composable
fun ConversationsScreen(
    rehearsals: List<Rehearsal>,
    activeId: String?,
    runsFor: (String) -> List<RunRecord>,
    onOpen: (Rehearsal) -> Unit,
    onNew: () -> Unit,
    onDelete: (Rehearsal) -> Unit,
    onBack: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Rehearsal?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeContentPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                TextButton(onClick = onBack) { Text("Back") }
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                item {
                    Text("Your conversations", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Each one keeps its own runs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                }

                items(rehearsals, key = { it.id }) { rehearsal ->
                    ConversationRow(
                        rehearsal = rehearsal,
                        runCount = runsFor(rehearsal.id).size,
                        isActive = rehearsal.id == activeId,
                        onClick = { onOpen(rehearsal) },
                        onDelete = { pendingDelete = rehearsal }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                item { Spacer(Modifier.height(20.dp)) }
            }

            Surface(tonalElevation = 2.dp) {
                Column(Modifier.padding(20.dp)) {
                    Button(
                        onClick = onNew,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) { Text("New conversation") }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this conversation?") },
            text = {
                Text("\"${target.counterpartRole}\" and its runs, gone from this phone. Your other conversations stay.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(target)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun ConversationRow(
    rehearsal: Rehearsal,
    runCount: Int,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatConversationTime(rehearsal.scheduledEpochMillis).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(rehearsal.counterpartRole, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    when (runCount) {
                        0 -> "Nothing rehearsed yet"
                        1 -> "1 run"
                        else -> "$runCount runs"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
