package com.dryrun.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.dryrun.app.models.Criterion
import com.dryrun.app.models.RunRecord
import com.dryrun.app.ui.theme.LocalDryRunColors

/**
 * What happened, said plainly.
 *
 * Four criteria, each with the exact words that earned the score. No praise
 * for turning up, no advice they did not ask for.
 */
@Composable
fun FeedbackScreen(
    run: RunRecord,
    canRunAgain: Boolean,
    onRunAgain: () -> Unit,
    onSeeProgress: () -> Unit,
    hasComparison: Boolean
) {
    val colors = LocalDryRunColors.current

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeContentPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    "RUN ${run.runNumber}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(run.feedback.overall, style = MaterialTheme.typography.headlineSmall)

                Spacer(Modifier.height(24.dp))

                Criterion.entries.forEach { criterion ->
                    val score = run.feedback.scoreFor(criterion)
                    val line = run.feedback.lineFor(criterion)
                    val note = run.feedback.criteria
                        .firstOrNull { it.criterionId == criterion.id }?.note.orEmpty()
                    CriterionRow(criterion.label, score, line, note)
                    Spacer(Modifier.height(18.dp))
                }

                Spacer(Modifier.height(4.dp))
                HedgeSummary(
                    count = run.hedgeCount,
                    topPhrase = run.hedgeTopPhrase,
                    colour = colors.hedge
                )

                if (run.silenceOffered > 0) {
                    Spacer(Modifier.height(14.dp))
                    SilenceSummary(offered = run.silenceOffered, filled = run.silenceFilled)
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "This stays on your phone. Nothing here is uploaded or shared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(tonalElevation = 2.dp) {
                Column(Modifier.padding(20.dp)) {
                    if (hasComparison) {
                        OutlinedButton(
                            onClick = onSeeProgress,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("Run 1 vs now") }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = onRunAgain,
                        enabled = canRunAgain,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text(if (canRunAgain) "Run it again" else "Run it again") }
                }
            }
        }
    }
}

@Composable
private fun CriterionRow(label: String, score: Int, quote: String, note: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            ScoreBar(score)
        }
        if (note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (quote.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Quote(quote)
        }
    }
}

/**
 * Two segments, 0 to 2. Small, factual, not a gauge or a trophy.
 *
 * [filledColour] and the empty track must stay clearly distinct: the earlier
 * run's bar is drawn muted, and if it matched the track colour every
 * criterion would read as though it went from nothing to full.
 */
@Composable
fun ScoreBar(score: Int, filledColour: androidx.compose.ui.graphics.Color? = null) {
    val filled = filledColour ?: MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) { index ->
            Box(
                Modifier
                    .width(22.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (index < score) filled else MaterialTheme.colorScheme.outline
                    )
            )
        }
    }
}

/** Their exact words. Never paraphrased -- that is the whole point. */
@Composable
private fun Quote(text: String) {
    Row {
        Box(
            Modifier
                .width(2.dp)
                .heightIn(min = 20.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "“$text”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
private fun HedgeSummary(count: Int, topPhrase: String, colour: androidx.compose.ui.graphics.Color) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("HEDGING", style = MaterialTheme.typography.labelMedium, color = colour)
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    count == 0 -> "None. You asked for what you wanted."
                    topPhrase.isBlank() -> "$count in this run."
                    else -> "$count in this run. Mostly “$topPhrase”."
                },
                style = MaterialTheme.typography.bodyLarge
            )
            if (count > 0 && topPhrase.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Each one made the ask sound optional.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SilenceSummary(offered: Int, filled: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "SILENCE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    filled == 0 && offered == 1 -> "They went quiet once. You waited."
                    filled == 0 -> "They went quiet $offered times. You waited both times."
                    filled == offered && offered == 1 -> "They went quiet once. You filled it."
                    filled == offered -> "They went quiet $offered times. You filled every one."
                    else -> "They went quiet $offered times. You filled $filled."
                },
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
