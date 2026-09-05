package com.dryrun.app.data

import com.dryrun.app.models.CounterpartState
import com.dryrun.app.models.CriterionScore
import com.dryrun.app.models.FeedbackReport
import com.dryrun.app.models.Speaker
import com.dryrun.app.models.Turn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://dry-run-worker.tushar-dev.workers.dev"

class DryRunApi(private val store: DryRunStore) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
    }

    private suspend fun token(): String? {
        store.token()?.let { return it }
        return try {
            val res: HttpResponse = client.post("$BASE_URL/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterBody(store.deviceId()))
            }
            if (!res.status.isSuccess()) return null
            val token = res.body<RegisterResponse>().token
            store.saveToken(token)
            token
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun roleplay(
        scenarioId: String,
        counterpart: String,
        situation: String,
        transcript: List<Turn>,
        state: CounterpartState,
        difficulty: String,
        beatsOffered: Int,
        turnsSinceLastBeat: Int
    ): RoleplayResult {
        val token = token() ?: return RoleplayResult.Offline
        return try {
            val res: HttpResponse = client.post("$BASE_URL/roleplay") {
                contentType(ContentType.Application.Json)
                headers { append(HttpHeaders.Authorization, "Bearer $token") }
                setBody(
                    RoleplayBody(
                        scenarioId = scenarioId,
                        counterpart = counterpart,
                        situation = situation,
                        transcript = transcript.filterNot { it.isSilence }.map { it.toWire() },
                        state = state,
                        difficulty = difficulty,
                        beatsOffered = beatsOffered,
                        turnsSinceLastBeat = turnsSinceLastBeat
                    )
                )
            }
            when {
                res.status.value == 422 -> RoleplayResult.Blocked(res.body<BlockedResponse>().message)
                res.status.value == 429 -> RoleplayResult.RateLimited
                // 503 is the provider refusing us, not a passing blip.
                res.status.value == 503 -> RoleplayResult.ServiceDown
                !res.status.isSuccess() -> RoleplayResult.Failed
                else -> {
                    val body = res.body<RoleplayResponse>()
                    RoleplayResult.Ok(body.reply, body.state, body.silence)
                }
            }
        } catch (_: Throwable) {
            RoleplayResult.Offline
        }
    }

    suspend fun feedback(
        scenarioId: String,
        counterpart: String,
        situation: String,
        transcript: List<Turn>,
        hedgeCount: Int,
        hedgeTopPhrase: String,
        hedgeTopCount: Int,
        silenceOffered: Int,
        silenceFilled: Int
    ): FeedbackResult {
        val token = token() ?: return FeedbackResult.Offline
        return try {
            val res: HttpResponse = client.post("$BASE_URL/feedback") {
                contentType(ContentType.Application.Json)
                headers { append(HttpHeaders.Authorization, "Bearer $token") }
                setBody(
                    FeedbackBody(
                        scenarioId = scenarioId,
                        counterpart = counterpart,
                        situation = situation,
                        transcript = transcript.filterNot { it.isSilence }.map { it.toWire() },
                        facts = Facts(
                            hedgeCount = hedgeCount,
                            hedgeTopPhrase = hedgeTopPhrase,
                            hedgeTopCount = hedgeTopCount,
                            silenceOffered = silenceOffered,
                            silenceFilled = silenceFilled
                        )
                    )
                )
            }
            if (res.status.value == 503) return FeedbackResult.ServiceDown
            if (!res.status.isSuccess()) return FeedbackResult.Failed
            val body = res.body<FeedbackResponseWire>()
            if (body.criteria.size != 4) return FeedbackResult.Failed
            FeedbackResult.Ok(
                FeedbackReport(
                    criteria = body.criteria.map {
                        CriterionScore(it.id, it.score, it.triggerLine, it.note)
                    },
                    overall = body.overall,
                    strongestLine = body.strongestLine
                )
            )
        } catch (_: Throwable) {
            FeedbackResult.Offline
        }
    }
}

/**
 * Why scoring didn't produce a report. The reason matters: telling someone to
 * check their connection when the service is down sends them to fix something
 * that isn't broken.
 */
sealed interface FeedbackResult {
    data class Ok(val report: FeedbackReport) : FeedbackResult
    data object ServiceDown : FeedbackResult
    data object Offline : FeedbackResult
    data object Failed : FeedbackResult
}

sealed interface RoleplayResult {
    data class Ok(val reply: String, val state: CounterpartState, val silence: Boolean) : RoleplayResult
    data class Blocked(val message: String) : RoleplayResult
    data object RateLimited : RoleplayResult
    data object Offline : RoleplayResult
    /** The service is down at our end. Retrying now will not help. */
    data object ServiceDown : RoleplayResult
    data object Failed : RoleplayResult
}

// ---- wire types -----------------------------------------------------------

private fun Turn.toWire() = WireTurn(
    role = if (speaker == Speaker.YOU) "user" else "counterpart",
    text = text
)

@Serializable private data class RegisterBody(@SerialName("device_id") val deviceId: String)
@Serializable private data class RegisterResponse(val token: String)
@Serializable private data class WireTurn(val role: String, val text: String)
@Serializable private data class BlockedResponse(val message: String = "")

@Serializable
private data class RoleplayBody(
    val scenarioId: String,
    val counterpart: String,
    val situation: String,
    val transcript: List<WireTurn>,
    val state: CounterpartState,
    val difficulty: String,
    val beatsOffered: Int,
    val turnsSinceLastBeat: Int
)

@Serializable
private data class RoleplayResponse(
    val reply: String = "",
    val state: CounterpartState = CounterpartState(),
    val silence: Boolean = false
)

@Serializable
private data class Facts(
    val hedgeCount: Int,
    val hedgeTopPhrase: String,
    val hedgeTopCount: Int,
    val silenceOffered: Int,
    val silenceFilled: Int
)

@Serializable
private data class FeedbackBody(
    val scenarioId: String,
    val counterpart: String,
    val situation: String,
    val transcript: List<WireTurn>,
    val facts: Facts
)

@Serializable
private data class WireCriterion(
    val id: String,
    val score: Int,
    @SerialName("trigger_line") val triggerLine: String = "",
    val note: String = ""
)

@Serializable
private data class FeedbackResponseWire(
    @SerialName("schema_version") val schemaVersion: Int = 0,
    val criteria: List<WireCriterion> = emptyList(),
    val overall: String = "",
    @SerialName("strongest_line") val strongestLine: String = ""
)
