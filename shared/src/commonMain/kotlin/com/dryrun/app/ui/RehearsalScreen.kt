package com.dryrun.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dryrun.app.coach.HedgeDetector
import com.dryrun.app.models.Speaker
import com.dryrun.app.models.Turn
import com.dryrun.app.ui.theme.LocalDryRunColors

/**
 * The run itself.
 *
 * Two things happen here that do not happen in an ordinary chat screen:
 * hedges underline live as you type, and the counterpart sometimes says
 * nothing at all for three real seconds.
 */
@Composable
fun RehearsalScreen(
    state: RehearsalUiState,
    onSend: (String) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val colors = LocalDryRunColors.current
    val listState = rememberLazyListState()

    val hedges = remember(draft) { HedgeDetector.analyse(draft) }

    LaunchedEffect(state.turns.size, state.isThinking, state.isSilent) {
        if (state.turns.isNotEmpty()) listState.animateScrollToItem(state.turns.size)
    }

    Scaffold(
        topBar = {
            RehearsalTopBar(
                counterpartRole = state.counterpartRole,
                turnCount = state.turns.count { it.speaker == Speaker.YOU },
                canFinish = state.canFinish,
                onFinish = onFinish,
                onBack = onBack
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.turns) { turn ->
                    if (turn.isSilence) SilenceBeatBubble() else TurnBubble(turn)
                }
                if (state.isThinking) item { TypingBubble() }
                state.error?.let { item { ErrorNote(it) } }
            }

            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                hedgeCount = hedges.count,
                hedgePhrase = hedges.mostUsed,
                enabled = !state.isThinking,
                hedgeColour = colors.hedge,
                onSend = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        onSend(text)
                        draft = ""
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RehearsalTopBar(
    counterpartRole: String,
    turnCount: Int,
    canFinish: Boolean,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(counterpartRole, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    if (turnCount == 0) "They're waiting" else "$turnCount from you",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            TextButton(onClick = onBack) { Text("Leave") }
        },
        actions = {
            TextButton(onClick = onFinish, enabled = canFinish) { Text("Done") }
        }
    )
}

@Composable
private fun TurnBubble(turn: Turn) {
    val colors = LocalDryRunColors.current
    val isYou = turn.speaker == Speaker.YOU

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isYou) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isYou) colors.youBubble else colors.themBubble,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isYou) 16.dp else 4.dp,
                bottomEnd = if (isYou) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            if (isYou) {
                // Their own hedges stay marked in the transcript, so the
                // feedback afterwards is not the first time they see them.
                Text(
                    text = highlightHedges(turn.text, colors.hedge),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            } else {
                Text(
                    text = turn.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * Real dead air. The typing indicator starts, then stops, and nothing comes.
 * If they send another message inside this window they filled the silence,
 * and that is recorded as fact rather than inferred later from a transcript.
 */
@Composable
private fun SilenceBeatBubble() {
    val transition = rememberInfiniteTransition()
    val fade by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse)
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .alpha(fade)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
            Text(
                "They're not saying anything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    val transition = rememberInfiniteTransition()
    val fade by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse)
    )
    Row(Modifier.fillMaxWidth().alpha(fade), horizontalArrangement = Arrangement.Start) {
        Text(
            "…",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun ErrorNote(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    )
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    hedgeCount: Int,
    hedgePhrase: String?,
    enabled: Boolean,
    hedgeColour: Color,
    onSend: () -> Unit
) {
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // The live count. Never blocks sending, never scolds.
            if (hedgeCount > 0) {
                Text(
                    text = hedgeCountLine(hedgeCount, hedgePhrase),
                    style = MaterialTheme.typography.bodySmall,
                    color = hedgeColour,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    placeholder = { Text("Say it") },
                    visualTransformation = rememberHedgeUnderline(hedgeColour),
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onSend,
                    enabled = enabled && draft.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("↑", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun hedgeCountLine(count: Int, phrase: String?): String = when {
    phrase == null -> "$count hedge"
    count == 1 -> "1 hedge · \"$phrase\""
    else -> "$count hedges · mostly \"$phrase\""
}

data class RehearsalUiState(
    val counterpartRole: String,
    val turns: List<Turn>,
    val isThinking: Boolean = false,
    /** True while the counterpart is deliberately saying nothing. */
    val isSilent: Boolean = false,
    val error: String? = null
) {
    val canFinish: Boolean get() = turns.count { it.speaker == Speaker.YOU } >= 2
}
