package com.kaskita.lokal.ui

import android.app.Application
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.kaskita.lokal.BuildConfig
import com.kaskita.lokal.appCheckProviderFactory
import com.kaskita.lokal.seedAppCheckDebugToken
import com.kaskita.lokal.data.DataStoreSettingsStore
import com.kaskita.lokal.data.LedgerRepository
import com.kaskita.lokal.data.RoomLedgerRepository
import com.kaskita.lokal.data.SettingsStore
import com.kaskita.lokal.data.ai.FirebaseGeminiClient
import com.kaskita.lokal.data.ai.GeminiClient
import com.kaskita.lokal.data.backup.BackupFileIo
import com.kaskita.lokal.data.backup.BackupService
import com.kaskita.lokal.data.backup.DefaultBackupService
import com.kaskita.lokal.data.backup.BackupRepository
import com.kaskita.lokal.data.db.KasKitaDatabase
import java.io.File
import java.time.ZoneId

/** Hand-wired dependencies (no DI framework): one database, one settings store, shared by every screen. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings: SettingsStore = DataStoreSettingsStore.create(appContext)
    private val database: KasKitaDatabase = KasKitaDatabase.create(appContext)

    val ledger: LedgerRepository = RoomLedgerRepository(database, settings)
    val backupService: BackupService = DefaultBackupService(
        backups = BackupRepository(database, settings, BuildConfig.VERSION_NAME),
        io = BackupFileIo(appContext),
        settings = settings,
        safetyBackupDir = File(appContext.filesDir, "safety"),
    )

    /** Gemini through the app's Firebase AI Logic project; no API key is stored on the phone. */
    val gemini: GeminiClient = FirebaseGeminiClient()

    val zone: ZoneId get() = ZoneId.systemDefault()
    fun now(): Long = System.currentTimeMillis()
}

class KasKitaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Only installs the attestation provider; nothing is contacted until the user runs an analysis.
        FirebaseApp.initializeApp(this)
        seedAppCheckDebugToken(this, BuildConfig.APPCHECK_DEBUG_TOKEN)
        Firebase.appCheck.installAppCheckProviderFactory(appCheckProviderFactory())
        container = AppContainer(this)
    }
}
