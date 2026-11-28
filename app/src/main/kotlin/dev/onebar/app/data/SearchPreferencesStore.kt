package dev.onebar.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchPreferencesStore internal constructor(private val preferences: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    private val _historyFavoritesFirst = MutableStateFlow(readHistoryFavoritesFirst())
    val historyFavoritesFirst: StateFlow<Boolean> = _historyFavoritesFirst.asStateFlow()

    fun setHistoryFavoritesFirst(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HISTORY_FAVORITES_FIRST, enabled).apply()
        _historyFavoritesFirst.value = enabled
    }

    private fun readHistoryFavoritesFirst(): Boolean =
        preferences.getBoolean(KEY_HISTORY_FAVORITES_FIRST, true)

    private companion object {
        const val PREFERENCES_NAME = "search_preferences"
        const val KEY_HISTORY_FAVORITES_FIRST = "history_favorites_first"
    }
}
