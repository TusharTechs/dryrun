package com.dryrun.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.dryrun.app.coach.HedgeDetector
import com.dryrun.app.drill.Drill
import com.dryrun.app.ui.theme.LocalDryRunColors

/**
 * Today's line. One softened sentence, rewritten straight.
 *
 * There is no score and no right answer -- the hedge underlining is the whole
 * feedback mechanism, and it is the same detector the rehearsal uses, so the
 * habit built here is the one that pays off there. A straight version is
 * available after they've written their own, never before: seeing the answer
 * first turns the exercise into reading.
 */
@Composable
fun DrillScreen(
    drill: Drill,
    alreadyDoneToday: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalDryRunColors.current
    var attempt by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    val report = remember(attempt) { HedgeDetector.analyse(attempt) }
    val written = attempt.isNotBlank()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeContentPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(onClick = onBack) { Text("Back") }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    if (alreadyDoneToday) "TODAY'S LINE · DONE" else "TODAY'S LINE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text("Say it straight.", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))

                // The softened line, with its hedges already marked.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        highlightHedges(drill.soft, colors.hedge),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = attempt,
                    onValueChange = { attempt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your version") },
                    minLines = 3,
                    visualTransformation = rememberHedgeUnderline(colors.hedge)
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        !written -> "Same meaning. Fewer words."
                        report.count == 0 -> "No hedges."
                        report.count == 1 -> "1 hedge left."
                        else -> "${report.count} hedges left."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (written && report.count > 0) colors.hedge
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (revealed) {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "ONE WAY TO SAY IT",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            drill.straight,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        drill.note,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Yours doesn't have to match. Straight is the point, not identical.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(28.dp))
            }

            Surface(tonalElevation = 2.dp) {
                Column(Modifier.padding(20.dp)) {
                    if (!revealed) {
                        Button(
                            onClick = { revealed = true },
                            enabled = written,
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) { Text(if (written) "Compare" else "Write your version") }
                    } else {
                        Button(
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) { Text("Done") }
                    }
                }
            }
        }
    }
}
