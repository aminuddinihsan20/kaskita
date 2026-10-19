package com.kaskita.lokal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaskita.lokal.data.ThemeMode
import com.kaskita.lokal.ui.KasKitaApp
import com.kaskita.lokal.ui.nav.AppNav
import com.kaskita.lokal.ui.theme.KasKitaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KasKitaApp).container
        setContent {
            val themeMode by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            KasKitaTheme(themeMode) {
                AppNav(container)
            }
        }
    }
}
