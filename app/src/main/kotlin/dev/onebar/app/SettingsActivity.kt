package dev.onebar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.onebar.app.data.DefaultHistoryStore
import dev.onebar.app.data.LanguageOption
import dev.onebar.app.data.toLocales
import dev.onebar.app.ui.SettingsScreen
import dev.onebar.app.ui.theme.OneBarTheme
import dev.onebar.app.ui.theme.toNightMode

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val history = DefaultHistoryStore(applicationContext)
        enableEdgeToEdge()
        val appearanceStore = (application as OneBarApplication).appearanceStore
        val firstRunLanguage = intent.getBooleanExtra(EXTRA_FIRST_RUN_LANGUAGE, false) &&
            appearanceStore.requiresLanguageSelection
        setContent {
            val theme by appearanceStore.theme.collectAsStateWithLifecycle()
            OneBarTheme(theme = theme) {
                val saveHistory by history.saveHistory.collectAsStateWithLifecycle(initialValue = true)
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        onBack = { if (!firstRunLanguage) finish() },
                        initialLanguageSelection = firstRunLanguage,
                        language = currentLanguage(),
                        theme = theme,
                        onLanguageChange = {
                            appearanceStore.completeLanguageSelection()
                            AppCompatDelegate.setApplicationLocales(it.toLocales())
                            if (firstRunLanguage) {
                                startActivity(
                                    Intent(this, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                                )
                                finish()
                            }
                        },
                        onThemeChange = {
                            appearanceStore.setTheme(it)
                            AppCompatDelegate.setDefaultNightMode(it.toNightMode())
                        },
                        saveHistory = saveHistory,
                        onSaveHistory = { enabled -> history.setSaveHistory(enabled) },
                        onClearHistory = { history.clearQueries() },
                    )
                }
            }
        }
    }

    private fun currentLanguage(): LanguageOption {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty()) return LanguageOption.SYSTEM
        return when (locales.get(0)?.language) {
            "ru" -> LanguageOption.RUSSIAN
            "en" -> LanguageOption.ENGLISH
            else -> LanguageOption.SYSTEM
        }
    }

    companion object {
        const val EXTRA_FIRST_RUN_LANGUAGE = "first_run_language"
    }
}
