package dev.onebar.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.os.LocaleListCompat
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LanguageOption {
    SYSTEM,
    RUSSIAN,
    ENGLISH,
}

enum class ThemeOption {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class KeyboardStartup {
    IMMEDIATE,
    ON_FOCUS,
}

class AppearanceStore internal constructor(private val preferences: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    private val _theme = MutableStateFlow(readTheme())
    val theme: StateFlow<ThemeOption> = _theme.asStateFlow()

    private val _keyboardStartup = MutableStateFlow(readKeyboardStartup())
    val keyboardStartup: StateFlow<KeyboardStartup> = _keyboardStartup.asStateFlow()

    val requiresLanguageSelection: Boolean
        get() = readLanguageSetup() != LanguageSetup.COMPLETE

    val usesEnglishLanguageDefault: Boolean
        get() = readLanguageSetup() == LanguageSetup.DEFAULT_ENGLISH

    fun initializeLanguage(systemLanguages: List<String>): LanguageOption? {
        if (!requiresLanguageSelection) return null
        val systemLanguage = systemLanguages.firstNotNullOfOrNull { tag ->
            when (tag.substringBefore('-').lowercase(Locale.ROOT)) {
                "ru" -> LanguageOption.RUSSIAN
                "en" -> LanguageOption.ENGLISH
                else -> null
            }
        }
        if (systemLanguage != null) {
            completeLanguageSelection()
            return systemLanguage
        }
        writeLanguageSetup(LanguageSetup.DEFAULT_ENGLISH)
        return LanguageOption.ENGLISH
    }

    fun completeLanguageSelection() {
        writeLanguageSetup(LanguageSetup.COMPLETE)
    }

    fun setTheme(theme: ThemeOption) {
        preferences.edit().putString(KEY_THEME, theme.name).apply()
        _theme.value = theme
    }

    fun setKeyboardStartup(option: KeyboardStartup) {
        preferences.edit().putString(KEY_KEYBOARD_STARTUP, option.name).apply()
        _keyboardStartup.value = option
    }

    private fun readTheme(): ThemeOption {
        val stored = preferences.getString(KEY_THEME, null)
        return ThemeOption.entries.firstOrNull { it.name == stored } ?: ThemeOption.SYSTEM
    }

    private fun readKeyboardStartup(): KeyboardStartup {
        val stored = preferences.getString(KEY_KEYBOARD_STARTUP, null)
        return KeyboardStartup.entries.firstOrNull { it.name == stored } ?: KeyboardStartup.IMMEDIATE
    }

    private fun readLanguageSetup(): LanguageSetup {
        val stored = preferences.getString(KEY_LANGUAGE_SETUP, null)
        return LanguageSetup.entries.firstOrNull { it.name == stored } ?: LanguageSetup.PENDING
    }

    private fun writeLanguageSetup(setup: LanguageSetup) {
        preferences.edit().putString(KEY_LANGUAGE_SETUP, setup.name).apply()
    }

    private enum class LanguageSetup {
        PENDING,
        DEFAULT_ENGLISH,
        COMPLETE,
    }

    private companion object {
        const val PREFERENCES_NAME = "appearance"
        const val KEY_THEME = "theme"
        const val KEY_KEYBOARD_STARTUP = "keyboard_startup"
        const val KEY_LANGUAGE_SETUP = "language_setup"
    }
}

internal fun LanguageOption.toLocales(): LocaleListCompat = when (this) {
    LanguageOption.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
    LanguageOption.RUSSIAN -> LocaleListCompat.forLanguageTags("ru")
    LanguageOption.ENGLISH -> LocaleListCompat.forLanguageTags("en")
}
