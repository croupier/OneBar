package dev.onebar.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import dev.onebar.app.data.KeyboardStartup
import dev.onebar.app.data.LanguageOption
import dev.onebar.app.data.toLocales
import dev.onebar.app.ui.SettingsScreen
import dev.onebar.app.ui.theme.OneBarTheme
import dev.onebar.app.ui.theme.toNightMode

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val history = DefaultHistoryStore(applicationContext)
        val searchPreferences = (application as OneBarApplication).searchPreferencesStore
        enableEdgeToEdge()
        val appearanceStore = (application as OneBarApplication).appearanceStore
        val firstRunLanguage = intent.getBooleanExtra(EXTRA_FIRST_RUN_LANGUAGE, false) &&
            appearanceStore.requiresLanguageSelection
        setContent {
            val theme by appearanceStore.theme.collectAsStateWithLifecycle()
            val keyboardStartup by appearanceStore.keyboardStartup.collectAsStateWithLifecycle()
            val historyFavoritesFirst by searchPreferences.historyFavoritesFirst.collectAsStateWithLifecycle()
            OneBarTheme(theme = theme) {
                val saveHistory by history.saveHistory.collectAsStateWithLifecycle(initialValue = true)
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        onBack = { if (!firstRunLanguage) finish() },
                        initialLanguageSelection = firstRunLanguage,
                        language = currentLanguage(),
                        theme = theme,
                        keyboardStartup = keyboardStartup,
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
                        onKeyboardStartupChange = appearanceStore::setKeyboardStartup,
                        historyFavoritesFirst = historyFavoritesFirst,
                        onHistoryFavoritesFirstChange = searchPreferences::setHistoryFavoritesFirst,
                        saveHistory = saveHistory,
                        onSaveHistory = { enabled -> history.setSaveHistory(enabled) },
                        onClearHistory = { history.clearQueries() },
                        onSend = ::sendFeedback,
                    )
                }
            }
        }
    }

    private fun sendFeedback(message: String) {
        try {
            startActivity(feedbackIntent(this, message))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.settings_feedback_no_mail_app, Toast.LENGTH_SHORT).show()
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

internal fun feedbackIntent(context: Context, message: String): Intent {
    val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    val debug = context.getString(
        R.string.settings_debug,
        versionName.ifBlank { "—" },
        Build.MANUFACTURER,
        Build.MODEL,
        Build.VERSION.RELEASE,
        Build.VERSION.SDK_INT,
    )
    val body = buildString {
        if (message.isNotBlank()) {
            appendLine(message.trim())
            appendLine()
        }
        appendLine(context.getString(R.string.settings_feedback_notice))
        appendLine()
        append(debug)
    }
    val email = context.getString(R.string.settings_email)
    // Часть почтовых клиентов игнорирует extras у SENDTO; selector оставляет выбор только среди почты.
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_feedback_subject))
        putExtra(Intent.EXTRA_TEXT, body)
    }
}
