package dev.onebar.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")

/**
 * Избранное OneBar: порядок добавления и есть порядок показа, поэтому храним списки,
 * а не множества. lookupKey контакта может содержать запятую, его пишем JSON-массивом;
 * имена пакетов запятых не содержат — там разделитель безопасен.
 */
class FavoritesStore(private val context: Context) {

    val apps: Flow<List<String>> = context.favoritesDataStore.data.map { prefs ->
        prefs[KEY_APPS]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }

    val contacts: Flow<List<String>> = context.favoritesDataStore.data.map { prefs ->
        readKeys(prefs)
    }

    suspend fun toggleApp(packageName: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[KEY_APPS]?.split(SEPARATOR).orEmpty().filter { it.isNotBlank() }
            val updated = if (packageName in current) current - packageName
            else current + packageName
            prefs[KEY_APPS] = updated.joinToString(SEPARATOR)
        }
    }

    suspend fun toggleContact(lookupKey: String) {
        if (lookupKey.isBlank()) return
        context.favoritesDataStore.edit { prefs ->
            val current = readKeys(prefs)
            val updated = if (lookupKey in current) current - lookupKey else current + lookupKey
            prefs[KEY_CONTACTS] = JSONArray(updated).toString()
        }
    }

    private fun readKeys(prefs: Preferences): List<String> {
        val json = prefs[KEY_CONTACTS] ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val SEPARATOR = ","
        val KEY_APPS = stringPreferencesKey("apps")
        val KEY_CONTACTS = stringPreferencesKey("contacts")
    }
}
