package dev.onebar.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.File

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")

/** Тот же формат, что у приложения, но в отдельном файле — ради тестов в телефоне. */
internal fun favoritesStoreForFile(file: File): FavoritesStore =
    DefaultFavoritesStore(PreferenceDataStoreFactory.create(produceFile = { file }))

/** Избранное OneBar: порядок добавления и есть порядок показа, поэтому списки, а не множества. */
interface FavoritesStore {
    val apps: Flow<List<String>>

    val contacts: Flow<List<String>>

    suspend fun toggleApp(packageName: String)

    suspend fun toggleContact(lookupKey: String)
}

/**
 * Порядок добавления и есть порядок показа. lookupKey контакта может содержать запятую,
 * его пишем JSON-массивом; имена пакетов запятых не содержат — там разделитель безопасен.
 */
class DefaultFavoritesStore internal constructor(private val store: DataStore<Preferences>) : FavoritesStore {

    constructor(context: Context) : this(context.favoritesDataStore)

    override val apps: Flow<List<String>> = store.data.map { prefs ->
        prefs[KEY_APPS]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }

    override val contacts: Flow<List<String>> = store.data.map { prefs ->
        readKeys(prefs)
    }

    override suspend fun toggleApp(packageName: String) {
        store.edit { prefs ->
            val current = prefs[KEY_APPS]?.split(SEPARATOR).orEmpty().filter { it.isNotBlank() }
            val updated = if (packageName in current) current - packageName
            else current + packageName
            prefs[KEY_APPS] = updated.joinToString(SEPARATOR)
        }
    }

    override suspend fun toggleContact(lookupKey: String) {
        if (lookupKey.isBlank()) return
        store.edit { prefs ->
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
