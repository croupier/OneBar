package dev.onebar.app.data

import android.content.Context
import android.content.SharedPreferences
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

class AppearanceStore internal constructor(private val preferences: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    private val _theme = MutableStateFlow(readTheme())
    val theme: StateFlow<ThemeOption> = _theme.asStateFlow()

    fun setTheme(theme: ThemeOption) {
        preferences.edit().putString(KEY_THEME, theme.name).apply()
        _theme.value = theme
    }

    private fun readTheme(): ThemeOption {
        val stored = preferences.getString(KEY_THEME, null)
        return ThemeOption.entries.firstOrNull { it.name == stored } ?: ThemeOption.SYSTEM
    }

    private companion object {
        const val PREFERENCES_NAME = "appearance"
        const val KEY_THEME = "theme"
    }
}
