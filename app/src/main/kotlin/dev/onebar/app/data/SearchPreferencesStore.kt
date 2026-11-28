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

    private val _options = MutableStateFlow(readOptions())
    val options: StateFlow<SearchOptions> = _options.asStateFlow()

    fun setOptions(options: SearchOptions) {
        preferences.edit()
            .putBoolean(KEY_HISTORY_FAVORITES_FIRST, options.historyFavoritesFirst)
            .putBoolean(KEY_PREFIX, options.prefix)
            .putBoolean(KEY_CONTAINS, options.contains)
            .putBoolean(KEY_PHONE, options.phone)
            .putBoolean(KEY_YO, options.yo)
            .putBoolean(KEY_TRANSLIT, options.translit)
            .putBoolean(KEY_EXTRA_FIELDS, options.extraFields)
            .putBoolean(KEY_WEB_SUGGESTIONS, options.webSuggestions)
            .apply()
        _options.value = options
    }

    private fun readOptions(): SearchOptions = SearchOptions(
        historyFavoritesFirst = preferences.getBoolean(KEY_HISTORY_FAVORITES_FIRST, true),
        prefix = preferences.getBoolean(KEY_PREFIX, true),
        contains = preferences.getBoolean(KEY_CONTAINS, true),
        phone = preferences.getBoolean(KEY_PHONE, true),
        yo = preferences.getBoolean(KEY_YO, true),
        translit = preferences.getBoolean(KEY_TRANSLIT, true),
        extraFields = preferences.getBoolean(KEY_EXTRA_FIELDS, true),
        webSuggestions = preferences.getBoolean(KEY_WEB_SUGGESTIONS, true),
    )

    private companion object {
        const val PREFERENCES_NAME = "search_preferences"
        const val KEY_HISTORY_FAVORITES_FIRST = "history_favorites_first"
        const val KEY_PREFIX = "prefix"
        const val KEY_CONTAINS = "contains"
        const val KEY_PHONE = "phone"
        const val KEY_YO = "yo"
        const val KEY_TRANSLIT = "translit"
        const val KEY_EXTRA_FIELDS = "extra_fields"
        const val KEY_WEB_SUGGESTIONS = "web_suggestions"
    }
}
