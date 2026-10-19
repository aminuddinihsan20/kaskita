package com.kaskita.lokal.data.ai

class FakeGeminiClient(var result: GeminiResult = GeminiResult.Success("Analisis")) : GeminiClient {
    data class Call(val model: String, val system: String, val user: String)

    val calls = mutableListOf<Call>()

    /** When set, [analyze] suspends until it completes. */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** When set, [analyze] throws it (simulates a crash inside the client). */
    var failWith: Exception? = null

    override suspend fun analyze(model: String, system: String, user: String): GeminiResult {
        calls += Call(model, system, user)
        gate?.await()
        failWith?.let { throw it }
        return result
    }
}
