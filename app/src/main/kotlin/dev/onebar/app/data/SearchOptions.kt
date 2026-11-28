package dev.onebar.app.data

data class SearchOptions(
    val historyFavoritesFirst: Boolean = true,
    val prefix: Boolean = true,
    val contains: Boolean = true,
    val phone: Boolean = true,
    val yo: Boolean = true,
    val translit: Boolean = true,
    val extraFields: Boolean = true,
    val webSuggestions: Boolean = true,
)
