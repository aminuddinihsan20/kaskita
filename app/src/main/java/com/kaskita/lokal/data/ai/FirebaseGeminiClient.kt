package com.kaskita.lokal.data.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.InvalidLocationException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException

enum class Finish { STOP, TRUNCATED, BLOCKED }

/** What a Firebase AI Logic call produced, reduced to what the app needs. */
data class FirebaseReply(val text: String?, val finish: Finish)

/**
 * Gemini through the app's Firebase AI Logic project: no API key on the phone, App Check proves the request comes from
 * this app. [generate] is the SDK call, replaceable so the logic around it can be tested.
 */
class FirebaseGeminiClient(
    private val generate: suspend (model: String, system: String, user: String) -> FirebaseReply = ::sdkGenerate,
) : GeminiClient {

    override suspend fun analyze(model: String, system: String, user: String): GeminiResult {
        val name = normalizeGeminiModel(model) ?: return GeminiResult.Failure(GeminiError.MODEL_NOT_FOUND)
        return try {
            val reply = generate(name, system, user)
            val text = reply.text?.trim().orEmpty()
            when {
                reply.finish == Finish.BLOCKED -> GeminiResult.Failure(GeminiError.BLOCKED)
                // An answer cut off by the token limit lacks the closing advice and disclaimer: never show it as complete.
                reply.finish == Finish.TRUNCATED || text.isEmpty() -> GeminiResult.Failure(GeminiError.INCOMPLETE)
                else -> GeminiResult.Success(text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GeminiResult.Failure(FirebaseErrorMapper.map(e))
        }
    }
}

object FirebaseErrorMapper {
    private val refusal = Regex("app check|PERMISSION_DENIED|\\b403\\b", RegexOption.IGNORE_CASE)

    fun map(e: Throwable): GeminiError {
        val chain = generateSequence(e) { it.cause }.take(6).toList()
        if (chain.any { it is SocketTimeoutException }) return GeminiError.TIMEOUT
        return when (e) {
            is QuotaExceededException -> GeminiError.RATE_LIMITED
            is RequestTimeoutException -> GeminiError.TIMEOUT
            is PromptBlockedException, is ContentBlockedException -> GeminiError.BLOCKED
            is ResponseStoppedException -> GeminiError.INCOMPLETE
            // Not the user's fault and not fixable by retrying: the Firebase project, App Check or region refused.
            is InvalidAPIKeyException, is PermissionMissingException, is ServiceDisabledException,
            is APINotConfiguredException, is UnsupportedUserLocationException, is InvalidLocationException,
            -> GeminiError.DENIED
            else -> when {
                chain.any { it is IOException } -> GeminiError.NETWORK
                // App Check or permission refusals come back as a plain server error whose text says so.
                e is ServerException && refusal.containsMatchIn(e.message.orEmpty()) -> GeminiError.DENIED
                e is ServerException -> GeminiError.SERVER
                else -> GeminiError.UNKNOWN
            }
        }
    }
}

private suspend fun sdkGenerate(model: String, system: String, user: String): FirebaseReply {
    val generative = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = model,
        generationConfig = generationConfig { maxOutputTokens = 8192 },
        systemInstruction = content { text(system) },
    )
    val response = generative.generateContent(user)
    val finish = when (response.candidates.firstOrNull()?.finishReason) {
        FinishReason.MAX_TOKENS -> Finish.TRUNCATED
        FinishReason.SAFETY, FinishReason.RECITATION, FinishReason.BLOCKLIST,
        FinishReason.PROHIBITED_CONTENT, FinishReason.SPII, FinishReason.IMAGE_SAFETY,
        -> Finish.BLOCKED
        else -> Finish.STOP
    }
    return FirebaseReply(response.text, finish)
}
