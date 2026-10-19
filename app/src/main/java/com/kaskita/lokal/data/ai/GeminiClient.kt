package com.kaskita.lokal.data.ai

enum class GeminiError { DENIED, MODEL_NOT_FOUND, RATE_LIMITED, SERVER, TIMEOUT, NETWORK, BLOCKED, INCOMPLETE, UNKNOWN }

sealed interface GeminiResult {
    data class Success(val text: String) : GeminiResult

    /** Carries only a category, never the service's text. */
    data class Failure(val error: GeminiError) : GeminiResult
}

interface GeminiClient {
    suspend fun analyze(model: String, system: String, user: String): GeminiResult
}
