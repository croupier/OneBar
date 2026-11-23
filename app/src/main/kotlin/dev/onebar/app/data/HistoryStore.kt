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
import org.json.JSONObject
import java.io.File

private val Context.historyDataStore by preferencesDataStore(name = "history")

/** Журнал поверх отдельного файла: тесты в телефоне не зависят от накопленного состояния. */
internal fun historyStoreForFile(file: File): HistoryStore =
    DefaultHistoryStore(PreferenceDataStoreFactory.create(produceFile = { file }))

/** Журнал OneBar: недавние приложения, коммуникации и строки поиска. */
interface HistoryStore {
    val recentApps: Flow<List<String>>

    val communications: Flow<List<CommunicationEntry>>

    val queries: Flow<List<String>>

    suspend fun pushRecentApp(packageName: String)

    suspend fun pushCommunication(entry: CommunicationEntry)

    /** Пункт «Удалить из истории» убирает весь журнал контакта: звонок и СМС одного лица. */
    suspend fun removeCommunications(contactId: Long)

    suspend fun pushQuery(text: String)
}

class DefaultHistoryStore internal constructor(private val store: DataStore<Preferences>) : HistoryStore {

    constructor(context: Context) : this(context.historyDataStore)

    override val recentApps: Flow<List<String>> = store.data.map { prefs ->
        prefs[KEY_RECENT_APPS]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }

    override val communications: Flow<List<CommunicationEntry>> = store.data.map { prefs ->
        readCommunications(prefs)
    }

    override val queries: Flow<List<String>> = store.data.map { prefs ->
        readQueries(prefs)
    }

    override suspend fun pushRecentApp(packageName: String) {
        store.edit { prefs ->
            val current = prefs[KEY_RECENT_APPS]?.split(SEPARATOR).orEmpty()
            prefs[KEY_RECENT_APPS] = (listOf(packageName) + current.filter { it != packageName })
                .distinct()
                .take(MAX_RECENT_APPS)
                .joinToString(SEPARATOR)
        }
    }

    override suspend fun pushCommunication(entry: CommunicationEntry) {
        store.edit { prefs ->
            val updated = listOf(entry) + readCommunications(prefs).filterNot {
                it.contactId == entry.contactId && it.kind == entry.kind
            }
            val array = JSONArray()
            updated.take(MAX_COMMUNICATIONS).forEach { array.put(encode(it)) }
            prefs[KEY_COMMUNICATIONS] = array.toString()
        }
    }

    override suspend fun removeCommunications(contactId: Long) {
        store.edit { prefs ->
            val kept = readCommunications(prefs).filterNot { it.contactId == contactId }
            val array = JSONArray()
            kept.forEach { array.put(encode(it)) }
            prefs[KEY_COMMUNICATIONS] = array.toString()
        }
    }

    /** Текст из строки поиска. Запрос может содержать запятые и кавычки, поэтому храним JSON-массив. */
    override suspend fun pushQuery(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        store.edit { prefs ->
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
