package com.dryrun.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.dryrun.app.coach.Comparison
import com.dryrun.app.ui.theme.LocalDryRunColors

/**
 * Run 1 against now.
 *
 * Confidence-building is a judged criterion, so this lands on what improved
 * and stops there. Everything on it is deterministic -- the same two runs
 * always produce the same card.
 *
 * There is deliberately no share button and no export. The user has described
 * a real colleague, possibly by name.
 */
@Composable
fun ProgressScreen(comparison: Comparison, onDone: () -> Unit) {
    val colors = LocalDryRunColors.current

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeContentPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    "RUN ${comparison.firstRunNumber} → RUN ${comparison.latestRunNumber}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(headline(comparison), style = MaterialTheme.typography.headlineMedium)

                Spacer(Modifier.height(28.dp))

                comparison.deltas.forEach { delta ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            delta.criterion.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        // Muted, but never the same as the empty track.
                        ScoreBar(delta.before, MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "→",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        ScoreBar(delta.after)
                    }
                }

                Spacer(Modifier.height(8.dp))

                StatRow(
                    label = "Hedges",
                    before = comparison.hedgesBefore.toString(),
                    after = comparison.hedgesAfter.toString(),
                    highlight = comparison.hedgeDrop > 0,
                    colour = colors.hedge
                )

                if (comparison.silenceHeldBefore > 0 || comparison.silenceHeldAfter > 0) {
                    Spacer(Modifier.height(10.dp))
                    StatRow(
                        label = "Silences you sat through",
                        before = comparison.silenceHeldBefore.toString(),
                        after = comparison.silenceHeldAfter.toString(),
                        highlight = comparison.silenceHeldAfter > comparison.silenceHeldBefore,
                        colour = MaterialTheme.colorScheme.primary
                    )
                }

                comparison.mostImproved?.let { improved ->
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "THE LINE THAT CHANGED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    ChangedLine(
                        before = improved.before,
                        after = improved.after
                    )
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    "Private to this phone. No share, no export, by design.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }

            Surface(tonalElevation = 2.dp) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().padding(20.dp).height(52.dp)
                ) { Text("Close") }
            }
        }
    }
}

/** Always lands on what improved. Never on what is still wrong. */
private fun headline(c: Comparison): String = when {
    c.hedgeDrop > 0 && c.improvedCount > 0 ->
        "You hedged ${c.hedgeDrop} fewer times, and ${c.improvedCount} of the four got sharper."
    c.hedgeDrop > 0 -> "You hedged ${c.hedgeDrop} fewer times."
    c.improvedCount > 0 -> "${c.improvedCount} of the four got sharper."
    c.silenceHeldAfter > c.silenceHeldBefore -> "You stopped filling the silence."
    else -> "Same ground twice. Change one thing and go again."
}

@Composable
private fun StatRow(
    label: String,
    before: String,
    after: String,
    highlight: Boolean,
    colour: androidx.compose.ui.graphics.Color
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            before,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "→",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Text(
            after,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) colour else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChangedLine(before: String, after: String) {
    Column {
        if (before.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "THEN",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "“$before”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "NOW",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "“$after”",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
