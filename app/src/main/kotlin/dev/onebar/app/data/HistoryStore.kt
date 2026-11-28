package dev.onebar.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    val appLaunchCounts: Flow<Map<String, Int>>

    val communications: Flow<List<CommunicationEntry>>

    val queries: Flow<List<String>>

    val saveHistory: Flow<Boolean>

    suspend fun pushRecentApp(packageName: String)

    suspend fun pushCommunication(entry: CommunicationEntry)

    /** Пункт «Удалить из истории» убирает весь журнал контакта: звонок и СМС одного лица. */
    suspend fun removeCommunications(contactId: Long)

    suspend fun pushQuery(text: String)

    suspend fun setSaveHistory(enabled: Boolean)

    suspend fun clearQueries()

    suspend fun replaceHistory(
        recentApps: List<String>,
        appLaunchCounts: Map<String, Int>,
        communications: List<CommunicationEntry>,
        queries: List<String>,
    )
}

class DefaultHistoryStore internal constructor(private val store: DataStore<Preferences>) : HistoryStore {

    constructor(context: Context) : this(context.historyDataStore)

    override val recentApps: Flow<List<String>> = store.data.map { prefs ->
        prefs[KEY_RECENT_APPS]?.split(SEPARATOR)?.filter { it.isNotBlank() }?.distinct() ?: emptyList()
    }

    override val appLaunchCounts: Flow<Map<String, Int>> = store.data.map { prefs ->
        readAppLaunchCounts(prefs)
    }

    override val communications: Flow<List<CommunicationEntry>> = store.data.map { prefs ->
        readCommunications(prefs)
    }

    override val queries: Flow<List<String>> = store.data.map { prefs ->
        if (prefs[KEY_SAVE_HISTORY] == false) emptyList() else readQueries(prefs)
    }

    override val saveHistory: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_SAVE_HISTORY] ?: true
    }

    override suspend fun pushRecentApp(packageName: String) {
        if (packageName.isBlank()) return
        store.edit { prefs ->
            val current = prefs[KEY_RECENT_APPS]?.split(SEPARATOR).orEmpty()
            val counts = readAppLaunchCounts(prefs).toMutableMap()
            prefs[KEY_RECENT_APPS] = (listOf(packageName) + current.filter { it != packageName })
                .distinct()
                .take(MAX_RECENT_APPS)
                .joinToString(SEPARATOR)
            counts[packageName] = (counts[packageName] ?: 0) + 1
            prefs[KEY_APP_LAUNCH_COUNTS] = encodeAppLaunchCounts(counts)
        }
    }

    override suspend fun pushCommunication(entry: CommunicationEntry) {
        store.edit { prefs ->
            val current = readCommunications(prefs)
            val previous = current.firstOrNull {
                it.contactId == entry.contactId && it.kind == entry.kind
            }
            val updated = listOf(entry.copy(launchCount = (previous?.launchCount ?: 0) + 1)) +
                current.filterNot { it.contactId == entry.contactId && it.kind == entry.kind }
            prefs[KEY_COMMUNICATIONS] = encodeCommunications(updated.take(MAX_COMMUNICATIONS))
        }
    }

    override suspend fun removeCommunications(contactId: Long) {
        store.edit { prefs ->
            val kept = readCommunications(prefs).filterNot { it.contactId == contactId }
            prefs[KEY_COMMUNICATIONS] = encodeCommunications(kept)
        }
    }

    /** Текст из строки поиска. Запрос может содержать запятые и кавычки, поэтому храним JSON-массив. */
    override suspend fun pushQuery(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        store.edit { prefs ->
            if (prefs[KEY_SAVE_HISTORY] == false) return@edit
            val updated = listOf(query) + readQueries(prefs).filter { it != query }
            prefs[KEY_QUERIES] = JSONArray(updated.take(MAX_QUERIES)).toString()
        }
    }

    override suspend fun setSaveHistory(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_SAVE_HISTORY] = enabled }
    }

    override suspend fun clearQueries() {
        store.edit { prefs -> prefs.remove(KEY_QUERIES) }
    }

    override suspend fun replaceHistory(
        recentApps: List<String>,
        appLaunchCounts: Map<String, Int>,
        communications: List<CommunicationEntry>,
        queries: List<String>,
    ) {
        store.edit { prefs ->
            prefs[KEY_RECENT_APPS] = recentApps.distinct().take(MAX_RECENT_APPS).joinToString(SEPARATOR)
            prefs[KEY_APP_LAUNCH_COUNTS] = encodeAppLaunchCounts(appLaunchCounts)
            prefs[KEY_COMMUNICATIONS] = encodeCommunications(communications.take(MAX_COMMUNICATIONS))
            prefs[KEY_QUERIES] = JSONArray(queries.distinct().take(MAX_QUERIES)).toString()
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

    private fun readAppLaunchCounts(prefs: Preferences): Map<String, Int> {
        val stored = prefs[KEY_APP_LAUNCH_COUNTS]
        if (stored != null) {
            runCatching {
                val obj = JSONObject(stored)
                val counts = mutableMapOf<String, Int>()
                obj.keys().forEach { key ->
                    counts[key] = obj.optInt(key, 0).coerceAtLeast(0)
                }
                return counts
            }
        }
        return prefs[KEY_RECENT_APPS]?.split(SEPARATOR).orEmpty()
            .filter { it.isNotBlank() }
            .associateWith { 1 }
    }

    private fun encodeCommunications(entries: List<CommunicationEntry>): String =
        JSONArray().apply { entries.forEach { put(encode(it)) } }.toString()

    private fun encodeAppLaunchCounts(counts: Map<String, Int>): String =
        JSONObject().apply {
            counts.filterKeys { it.isNotBlank() }.forEach { (packageName, count) ->
                put(packageName, count.coerceAtLeast(0))
            }
        }.toString()

    private fun encode(entry: CommunicationEntry): JSONObject = JSONObject()
        .put("kind", entry.kind.name)
        .put("id", entry.contactId)
        .put("key", entry.lookupKey)
        .put("name", entry.name)
        .put("number", entry.number)
        .put("time", entry.timestamp)
        .put("count", entry.launchCount.coerceAtLeast(1))

    private fun decode(obj: JSONObject): CommunicationEntry = CommunicationEntry(
        kind = CommunicationKind.valueOf(obj.getString("kind")),
        contactId = obj.getLong("id"),
        lookupKey = obj.optString("key"),
        name = obj.optString("name"),
        number = obj.optString("number"),
        timestamp = obj.getLong("time"),
        launchCount = obj.optInt("count", 1).coerceAtLeast(1),
    )

    private companion object {
        // Имена пакетов не содержат запятую, поэтому оно безопасно как разделитель списка.
        const val SEPARATOR = ","
        const val MAX_RECENT_APPS = 100
        const val MAX_COMMUNICATIONS = 100
        const val MAX_QUERIES = 50
        val KEY_RECENT_APPS = stringPreferencesKey("recent_apps")
        val KEY_APP_LAUNCH_COUNTS = stringPreferencesKey("app_launch_counts")
        val KEY_COMMUNICATIONS = stringPreferencesKey("communications")
        val KEY_QUERIES = stringPreferencesKey("queries")
        val KEY_SAVE_HISTORY = booleanPreferencesKey("save_history")
    }
}
