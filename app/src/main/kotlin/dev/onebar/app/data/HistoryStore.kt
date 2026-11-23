package dev.onebar.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.historyDataStore by preferencesDataStore(name = "history")

class HistoryStore(private val context: Context) {

    val recentApps: Flow<List<String>> = context.historyDataStore.data.map { prefs ->
        prefs[KEY_RECENT_APPS]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }

    val communications: Flow<List<CommunicationEntry>> = context.historyDataStore.data.map { prefs ->
        readCommunications(prefs)
    }

    val queries: Flow<List<String>> = context.historyDataStore.data.map { prefs ->
        readQueries(prefs)
    }

    suspend fun pushRecentApp(packageName: String) {
        context.historyDataStore.edit { prefs ->
            val current = prefs[KEY_RECENT_APPS]?.split(SEPARATOR).orEmpty()
            prefs[KEY_RECENT_APPS] = (listOf(packageName) + current.filter { it != packageName })
                .distinct()
                .take(MAX_RECENT_APPS)
                .joinToString(SEPARATOR)
        }
    }

    suspend fun pushCommunication(entry: CommunicationEntry) {
        context.historyDataStore.edit { prefs ->
            val updated = listOf(entry) + readCommunications(prefs).filterNot {
                it.contactId == entry.contactId && it.kind == entry.kind
            }
            val array = JSONArray()
            updated.take(MAX_COMMUNICATIONS).forEach { array.put(encode(it)) }
            prefs[KEY_COMMUNICATIONS] = array.toString()
        }
    }

    /** Пункт «Удалить из истории» убирает весь журнал контакта: звонок и СМС одного лица. */
    suspend fun removeCommunications(contactId: Long) {
        context.historyDataStore.edit { prefs ->
            val kept = readCommunications(prefs).filterNot { it.contactId == contactId }
            val array = JSONArray()
            kept.forEach { array.put(encode(it)) }
            prefs[KEY_COMMUNICATIONS] = array.toString()
        }
    }

    /** Текст из строки поиска. Запрос может содержать запятые и кавычки, поэтому храним JSON-массив. */
    suspend fun pushQuery(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        context.historyDataStore.edit { prefs ->
            val updated = listOf(query) + readQueries(prefs).filter { it != query }
            prefs[KEY_QUERIES] = JSONArray(updated.take(MAX_QUERIES)).toString()
        }
    }

    private fun readCommunications(prefs: Preferences): List<CommunicationEntry> {
        val json = prefs[KEY_COMMUNICATIONS] ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { decode(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun readQueries(prefs: Preferences): List<String> {
        val json = prefs[KEY_QUERIES] ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun encode(entry: CommunicationEntry): JSONObject = JSONObject()
        .put("kind", entry.kind.name)
        .put("id", entry.contactId)
        .put("key", entry.lookupKey)
        .put("name", entry.name)
        .put("number", entry.number)
        .put("time", entry.timestamp)

    private fun decode(obj: JSONObject): CommunicationEntry = CommunicationEntry(
        kind = CommunicationKind.valueOf(obj.getString("kind")),
        contactId = obj.getLong("id"),
        lookupKey = obj.optString("key"),
        name = obj.optString("name"),
        number = obj.optString("number"),
        timestamp = obj.getLong("time"),
    )

    private companion object {
        // Имена пакетов не содержат запятой, поэтому оно безопасно как разделитель списка.
        const val SEPARATOR = ","
        const val MAX_RECENT_APPS = 12
        const val MAX_COMMUNICATIONS = 20
        const val MAX_QUERIES = 20
        val KEY_RECENT_APPS = stringPreferencesKey("recent_apps")
        val KEY_COMMUNICATIONS = stringPreferencesKey("communications")
        val KEY_QUERIES = stringPreferencesKey("queries")
    }
}
