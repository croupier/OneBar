package dev.onebar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.onebar.app.ui.SearchScreen
import dev.onebar.app.ui.theme.OneBarTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        val appearanceStore = (application as OneBarApplication).appearanceStore
        if (appearanceStore.requiresLanguageSelection) {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_FIRST_RUN_LANGUAGE, true),
            )
            finish()
            return
        }
        setContent {
            var settingsReturnCount by remember { mutableStateOf(0) }
            val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                settingsReturnCount++
            }
            val theme by appearanceStore.theme.collectAsStateWithLifecycle()
            val keyboardStartup by appearanceStore.keyboardStartup.collectAsStateWithLifecycle()
            OneBarTheme(theme = theme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SearchScreen(
                        keyboardStartup = keyboardStartup,
                        settingsReturnCount = settingsReturnCount,
                        onOpenSettings = {
                            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                        },
                        onExit = { finish() },
                    )
                }
            }
        }
    }
}
