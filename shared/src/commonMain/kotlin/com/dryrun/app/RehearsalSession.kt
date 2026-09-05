package com.dryrun.app

import com.dryrun.app.coach.HedgeDetector
import com.dryrun.app.data.DryRunApi
import com.dryrun.app.data.RoleplayResult
import com.dryrun.app.models.CounterpartState
import com.dryrun.app.models.FeedbackReport
import com.dryrun.app.models.RunRecord
import com.dryrun.app.models.SilenceLog
import com.dryrun.app.models.Speaker
import com.dryrun.app.models.Turn
import com.dryrun.app.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One run, from the counterpart's opening line to the scored feedback.
 *
 * The silence beat lives here. When the Worker hands back silence, the
 * counterpart genuinely says nothing and a three second window opens. If the
 * user sends inside that window they filled it, and that is recorded as fact.
 * Nothing about it is inferred from the transcript afterwards.
 */
class RehearsalSession(
    private val api: DryRunApi,
    private val scope: CoroutineScope,
    private val scenarioId: String,
    private val counterpart: String,
    private val situation: String,
    private val runNumber: Int,
    private val difficulty: String = "normal"
) {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var counterpartState = CounterpartState()
    private var silenceLog = SilenceLog()
    private var beatsOffered = 0
    private var turnsSinceLastBeat = 99
    private var beatJob: Job? = null

    /** True only while real dead air is on screen. */
    private var awaitingSilence = false

    fun start() {
        if (_state.value.turns.isNotEmpty()) return
        scope.launch {
            _state.value = _state.value.copy(isThinking = true)
            respond(userText = null)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isThinking) return

        // They spoke into the dead air rather than waiting it out.
        if (awaitingSilence) {
            beatJob?.cancel()
            awaitingSilence = false
            silenceLog = silenceLog.beatFilled()
        }

        scope.launch {
            _state.value = _state.value.copy(
                turns = _state.value.turns + Turn(Speaker.YOU, trimmed),
                isThinking = true,
                error = null
            )
            respond(userText = trimmed)
        }
    }

    private suspend fun respond(userText: String?) {
        val result = api.roleplay(
            scenarioId = scenarioId,
            counterpart = counterpart,
            situation = situation,
            transcript = _state.value.turns,
            state = counterpartState,
            difficulty = difficulty,
            beatsOffered = beatsOffered,
            turnsSinceLastBeat = turnsSinceLastBeat
        )

        when (result) {
            is RoleplayResult.Ok -> {
                counterpartState = result.state
                if (result.silence) openSilenceBeat() else appendReply(result.reply)
            }
            is RoleplayResult.Blocked ->
                _state.value = _state.value.copy(isThinking = false, blocked = result.message)
            RoleplayResult.RateLimited ->
                fail("That's a lot of practice for one day. Come back tomorrow.")
            RoleplayResult.Offline ->
                fail("No connection. Your words didn't land — try again.")
            RoleplayResult.Failed ->
                fail("Something broke on our end. Try again in a few seconds.")
        }
        // A first turn that never produced an opening line would leave an
        // empty room, so give them something to push against.
        if (userText == null && _state.value.turns.isEmpty() && _state.value.error != null) {
            appendReply("Yeah? What's up.")
        }
    }

    private fun appendReply(reply: String) {
        turnsSinceLastBeat++
        _state.value = _state.value.copy(
            turns = _state.value.turns + Turn(Speaker.THEM, reply),
            isThinking = false
        )
    }

    private fun openSilenceBeat() {
        beatsOffered++
        turnsSinceLastBeat = 0
        silenceLog = silenceLog.beatOffered()
        awaitingSilence = true

        _state.value = _state.value.copy(
            turns = _state.value.turns + Turn(Speaker.THEM, "", isSilence = true),
            isThinking = false
        )

        beatJob = scope.launch {
            delay(SilenceLog.WINDOW_MILLIS)
            // They sat through it. That is the thing being practised.
            awaitingSilence = false
        }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(isThinking = false, error = message)
    }

    /** Everything the user actually said, for the deterministic hedge count. */
    private fun spokenByUser(): String =
        _state.value.turns.filter { it.speaker == Speaker.YOU }.joinToString(" ") { it.text }

    suspend fun finish(): RunRecord? {
        beatJob?.cancel()
        _state.value = _state.value.copy(isScoring = true)

        val hedges = HedgeDetector.analyse(spokenByUser())
        val feedback: FeedbackReport? = api.feedback(
            scenarioId = scenarioId,
            counterpart = counterpart,
            situation = situation,
            transcript = _state.value.turns,
            hedgeCount = hedges.count,
            hedgeTopPhrase = hedges.mostUsed.orEmpty(),
            hedgeTopCount = hedges.mostUsed?.let { hedges.countOf(it) } ?: 0,
            silenceOffered = silenceLog.offered,
            silenceFilled = silenceLog.filled
        )

        _state.value = _state.value.copy(isScoring = false)
        if (feedback == null) {
            fail("Couldn't score that run. Check your connection and try again.")
            return null
        }

        return RunRecord(
            runNumber = runNumber,
            transcript = _state.value.turns,
            feedback = feedback,
            hedgeCount = hedges.count,
            hedgeTopPhrase = hedges.mostUsed.orEmpty(),
            silenceOffered = silenceLog.offered,
            silenceFilled = silenceLog.filled,
            completedAtMillis = currentTimeMillis()
        )
    }
}

data class SessionState(
    val turns: List<Turn> = emptyList(),
    val isThinking: Boolean = false,
    val isScoring: Boolean = false,
    val error: String? = null,
    /** Set when the safety filter declines to roleplay something. */
    val blocked: String? = null
)
