package com.kaskita.lokal

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * This app is for personal use and is never published, so every build (debug and release) uses the debug provider with
 * the owner's fixed token (see [seedAppCheckDebugToken]), registered once in the Firebase console. Play Integrity would
 * reject an APK that is not installed from Google Play. If the app is ever published, switch this to
 * `PlayIntegrityAppCheckProviderFactory`. The token is never shown in the UI.
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
