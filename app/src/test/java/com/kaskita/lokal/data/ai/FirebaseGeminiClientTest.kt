package com.kaskita.lokal.data.ai

import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.UnknownException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class FirebaseGeminiClientTest {
    /** The SDK's exception constructors are Kotlin-internal but public on the JVM; build them the way the SDK does. */
    private fun sdk(type: Class<*>, cause: Throwable? = null, message: String = "msg"): Throwable {
        val withHistory = type.constructors.firstOrNull { it.parameterCount == 3 && it.parameterTypes[2] == List::class.java }
        return (if (withHistory != null) {
            withHistory.newInstance(message, cause, emptyList<Any>())
        } else {
            type.getConstructor(String::class.java, Throwable::class.java).newInstance(message, cause)
        }) as Throwable
    }

    private fun analyze(model: String = "gemini-3.8-flash", generate: suspend (String, String, String) -> FirebaseReply) =
        runBlocking { FirebaseGeminiClient(generate).analyze(model, "SISTEM", "DATA") }

    @Test
    fun passesTheModelSystemAndDataToTheSdkAndReturnsTheTrimmedText() {
        var seen: Triple<String, String, String>? = null
        val r = analyze("models/gemini-2.5-flash") { m, s, u -> seen = Triple(m, s, u); FirebaseReply("  Halo \n", Finish.STOP) }
        assertEquals(GeminiResult.Success("Halo"), r)
        assertEquals(Triple("gemini-2.5-flash", "SISTEM", "DATA"), seen)
    }

    @Test
    fun anInvalidModelNameNeverReachesTheSdk() {
        var called = false
        val r = analyze("a b/../x") { _, _, _ -> called = true; FirebaseReply("x", Finish.STOP) }
        assertEquals(GeminiResult.Failure(GeminiError.MODEL_NOT_FOUND), r)
        assertFalse(called)
    }

    @Test
    fun truncatedBlockedAndEmptyRepliesAreNotShownAsAnAnswer() {
        assertEquals(GeminiResult.Failure(GeminiError.INCOMPLETE), analyze { _, _, _ -> FirebaseReply("Terpotong", Finish.TRUNCATED) })
        assertEquals(GeminiResult.Failure(GeminiError.BLOCKED), analyze { _, _, _ -> FirebaseReply(null, Finish.BLOCKED) })
        assertEquals(GeminiResult.Failure(GeminiError.INCOMPLETE), analyze { _, _, _ -> FirebaseReply("  ", Finish.STOP) })
        assertEquals(GeminiResult.Failure(GeminiError.INCOMPLETE), analyze { _, _, _ -> FirebaseReply(null, Finish.STOP) })
    }

    @Test
    fun sdkExceptionsBecomeFailuresNeverCrashes() {
        assertEquals(GeminiResult.Failure(GeminiError.RATE_LIMITED), analyze { _, _, _ -> throw sdk(QuotaExceededException::class.java) })
        assertEquals(GeminiResult.Failure(GeminiError.UNKNOWN), analyze { _, _, _ -> throw IllegalStateException("boom") })
    }

    @Test
    fun cancellationIsNeverSwallowed() {
        try {
            analyze { _, _, _ -> throw CancellationException("cancelled") }
            fail("expected the cancellation to propagate")
        } catch (expected: CancellationException) {
            // fine
        }
    }

    @Test
    fun errorMappingCoversTheFirebaseExceptionFamily() {
        assertEquals(GeminiError.RATE_LIMITED, FirebaseErrorMapper.map(sdk(QuotaExceededException::class.java)))
        assertEquals(GeminiError.TIMEOUT, FirebaseErrorMapper.map(sdk(RequestTimeoutException::class.java)))
        assertEquals(GeminiError.DENIED, FirebaseErrorMapper.map(sdk(InvalidAPIKeyException::class.java)))
        assertEquals(GeminiError.DENIED, FirebaseErrorMapper.map(sdk(PermissionMissingException::class.java)))
        assertEquals(GeminiError.DENIED, FirebaseErrorMapper.map(sdk(APINotConfiguredException::class.java)))
        assertEquals(GeminiError.SERVER, FirebaseErrorMapper.map(sdk(ServerException::class.java)))
        assertEquals(GeminiError.UNKNOWN, FirebaseErrorMapper.map(sdk(UnknownException::class.java)))
    }

    @Test
    fun aServerErrorThatSaysAppCheckOrPermissionRefusedIsDeniedNotAnOutage() {
        assertEquals(GeminiError.DENIED, FirebaseErrorMapper.map(sdk(ServerException::class.java, message = "Firebase App Check token is invalid.")))
        assertEquals(GeminiError.DENIED, FirebaseErrorMapper.map(sdk(ServerException::class.java, message = "Error 403: PERMISSION_DENIED")))
        assertEquals(GeminiError.SERVER, FirebaseErrorMapper.map(sdk(ServerException::class.java, message = "The model is overloaded. Please try again later.")))
    }

    @Test
    fun aWrappedNetworkOrTimeoutCauseIsRecognised() {
        assertEquals(GeminiError.NETWORK, FirebaseErrorMapper.map(sdk(ServerException::class.java, UnknownHostException("host"))))
        assertEquals(GeminiError.NETWORK, FirebaseErrorMapper.map(sdk(UnknownException::class.java, IOException("reset"))))
        assertEquals(GeminiError.TIMEOUT, FirebaseErrorMapper.map(sdk(UnknownException::class.java, SocketTimeoutException("slow"))))
    }
}
