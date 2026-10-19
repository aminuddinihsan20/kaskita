package com.kaskita.lokal

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * This app is for personal use and is never published, so every build (debug and release) uses the debug provider:
 * it prints a one-time token to Logcat (tag "DebugAppCheckProvider") that the owner registers in the Firebase console
 * (App Check → Apps → ⋮ → Manage debug tokens). Play Integrity would reject an APK that is not installed from Google
 * Play. If the app is ever published, switch this to `PlayIntegrityAppCheckProviderFactory`.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()

/**
 * Makes the SDK use [token] as this installation's debug secret, so a single registration in the Firebase console
 * covers every phone that installs this APK. The token then sits in the APK: acceptable for a personal app shared only
 * with trusted people; revoke it in the console and rebuild if the APK leaks. Blank [token] leaves the SDK's own random one.
 */
internal fun seedAppCheckDebugToken(context: Context, token: String) {
    if (token.isBlank()) return
    runCatching {
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        context.getSharedPreferences("com.google.firebase.appcheck.debug.store.$persistenceKey", Context.MODE_PRIVATE)
            .edit().putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", token).apply()
    }
}

/**
 * The debug token the SDK generated for this installation, so the owner can register it without a computer.
 * It lives in the SDK's own preferences file (name and key taken from firebase-appcheck-debug 20.0.0); null if the SDK
 * has not created one yet or a future version stores it elsewhere.
 */
internal fun appCheckDebugToken(context: Context): String? = runCatching {
    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    context.getSharedPreferences("com.google.firebase.appcheck.debug.store.$persistenceKey", Context.MODE_PRIVATE)
        .getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
}.getOrNull()
