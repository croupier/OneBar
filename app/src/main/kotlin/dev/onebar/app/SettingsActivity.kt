package dev.onebar.app

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
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.onebar.app.data.LanguageOption
import dev.onebar.app.ui.SettingsScreen
import dev.onebar.app.ui.theme.OneBarTheme
import dev.onebar.app.ui.theme.toNightMode

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        val appearanceStore = (application as OneBarApplication).appearanceStore
        setContent {
            val theme by appearanceStore.theme.collectAsStateWithLifecycle()
            OneBarTheme(theme = theme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        onBack = { finish() },
                        language = currentLanguage(),
                        theme = theme,
                        onLanguageChange = { AppCompatDelegate.setApplicationLocales(it.toLocales()) },
                        onThemeChange = {
                            appearanceStore.setTheme(it)
                            AppCompatDelegate.setDefaultNightMode(it.toNightMode())
                        },
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

    private fun LanguageOption.toLocales(): LocaleListCompat = when (this) {
        LanguageOption.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
        LanguageOption.RUSSIAN -> LocaleListCompat.forLanguageTags("ru")
        LanguageOption.ENGLISH -> LocaleListCompat.forLanguageTags("en")
    }
}
