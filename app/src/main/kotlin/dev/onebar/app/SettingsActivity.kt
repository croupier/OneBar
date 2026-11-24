package dev.onebar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.onebar.app.data.DefaultHistoryStore
import dev.onebar.app.ui.SettingsScreen
import dev.onebar.app.ui.theme.OneBarTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val history = DefaultHistoryStore(applicationContext)
        enableEdgeToEdge()
        setContent {
            OneBarTheme {
                val saveHistory by history.saveHistory.collectAsStateWithLifecycle(initialValue = true)
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        onBack = { finish() },
                        saveHistory = saveHistory,
                        onSaveHistory = { enabled -> history.setSaveHistory(enabled) },
                        onClearHistory = { history.clearQueries() },
                    )
                }
            }
        }
    }
}
