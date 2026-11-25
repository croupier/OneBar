package dev.onebar.app.testing

import dev.onebar.app.data.AppRepository
import dev.onebar.app.data.CommunicationEntry
import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.data.ContactEntry
import dev.onebar.app.data.ContactRepository
import dev.onebar.app.data.FavoritesStore
import dev.onebar.app.data.HistoryStore
import dev.onebar.app.data.LaunchableApp
import dev.onebar.app.data.SuggestRepository
import dev.onebar.app.data.rankApps
import dev.onebar.app.data.rankContacts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

fun app(packageName: String, label: String = packageName, description: String = "") =
    LaunchableApp(packageName, label, description)

fun contact(
    id: Long,
    name: String,
    lookupKey: String = "lookup-$id",
    number: String? = "+7000000000$id",
    phoneCount: Int = 1,
    email: String? = null,
    organization: String = "",
    nickname: String = "",
    note: String = "",
    address: String = "",
    numbers: List<String> = emptyList(),
    photoUri: String? = null,
) = ContactEntry(
    contactId = id,
    lookupKey = lookupKey,
    name = name,
    number = number,
    photoUri = photoUri,
    phoneCount = phoneCount,
    email = email,
    organization = organization,
    nickname = nickname,
    note = note,
    address = address,
    searchableNumbers = numbers,
)

fun log(
    id: Long,
    name: String,
    kind: CommunicationKind = CommunicationKind.CALL,
    lookupKey: String = "lookup-$id",
    number: String = "+7000000000$id",
    timestamp: Long = System.currentTimeMillis(),
) = CommunicationEntry(kind, id, lookupKey, name, number, timestamp)

/**
 * Заглушки источников данных. Колбэки onMatch/onSearch вызываются до формирования
 * результата — ими тест блокирует корутину и проверяет гонку отменённых запросов.
 */
class FakeApps(
    var known: List<LaunchableApp> = emptyList(),
    private val onMatch: (String) -> Unit = {},
) : AppRepository {
    val matched: MutableList<String> = mutableListOf()
    var invalidations: Int = 0
        private set

    override fun matching(query: String, limit: Int, favorites: Set<String>, favoritesOnly: Boolean): List<LaunchableApp> {
        onMatch(query)
        matched += query
        return rankApps(known, query, limit, favorites, favoritesOnly)
    }

    override fun byPackages(packages: List<String>): List<LaunchableApp> =
        packages.mapNotNull { pkg -> known.firstOrNull { it.packageName == pkg } }

    override fun invalidate() {
        invalidations++
    }
}

class FakeContacts(
    var granted: Boolean = true,
    var known: List<ContactEntry> = emptyList(),
    var fullDetails: Map<Long, ContactEntry> = emptyMap(),
    private val onSearch: (String) -> Unit = {},
) : ContactRepository {
    val searched: MutableList<String> = mutableListOf()

    override fun hasPermission(): Boolean = granted

    override fun search(
        query: String,
        limit: Int,
        favorites: Set<String>,
        favoritesOnly: Boolean,
    ): List<ContactEntry> {
        if (!granted) return emptyList()
        onSearch(query)
        searched += query
        return rankContacts(known, query, limit, favorites, favoritesOnly)
    }

    override fun byLookupKeys(keys: List<String>): List<ContactEntry> =
        keys.mapNotNull { key -> known.firstOrNull { it.lookupKey == key } }

    override fun details(contactId: Long): ContactEntry? = fullDetails[contactId]
}

class FakeSuggestions(private val replies: Map<String, List<String>> = emptyMap()) : SuggestRepository {
    val asked: MutableList<String> = mutableListOf()

    override suspend fun suggest(query: String): List<String> {
        asked += query
        return replies[query].orEmpty()
    }
}

/** Хранит то же, что DataStore-журнал, но в памяти и сразу отдаёт изменения во flows. */
class FakeHistory(
    recentApps: List<String> = emptyList(),
    communications: List<CommunicationEntry> = emptyList(),
    queries: List<String> = emptyList(),
    saveHistory: Boolean = true,
) : HistoryStore {
    private val _recentApps = MutableStateFlow(recentApps)
    private val _communications = MutableStateFlow(communications)
    private val _queries = MutableStateFlow(queries)
    private val _saveHistory = MutableStateFlow(saveHistory)

    val pushedQueries: MutableList<String> = mutableListOf()
    val pushedApps: MutableList<String> = mutableListOf()
    val pushedCommunications: MutableList<CommunicationEntry> = mutableListOf()
    val journalRemoved: MutableList<Long> = mutableListOf()

    override val recentApps: Flow<List<String>> = _recentApps
    override val communications: Flow<List<CommunicationEntry>> = _communications
    override val queries: Flow<List<String>> = combine(_queries, _saveHistory) { queries, enabled ->
        if (enabled) queries else emptyList()
    }
    override val saveHistory: Flow<Boolean> = _saveHistory

    override suspend fun pushRecentApp(packageName: String) {
        pushedApps += packageName
        _recentApps.value = listOf(packageName) + _recentApps.value.filterNot { it == packageName }
    }

    override suspend fun pushCommunication(entry: CommunicationEntry) {
        pushedCommunications += entry
        _communications.value = listOf(entry) +
            _communications.value.filterNot { it.contactId == entry.contactId && it.kind == entry.kind }
    }

    override suspend fun removeCommunications(contactId: Long) {
        journalRemoved += contactId
        _communications.value = _communications.value.filterNot { it.contactId == contactId }
    }

    override suspend fun pushQuery(text: String) {
        val query = text.trim()
        if (query.isEmpty() || !_saveHistory.value) return
        pushedQueries += query
        _queries.value = listOf(query) + _queries.value.filter { it != query }
    }

    override suspend fun setSaveHistory(enabled: Boolean) {
        _saveHistory.value = enabled
    }

    override suspend fun clearQueries() {
        _queries.value = emptyList()
    }
}

class FakeFavorites(
    apps: List<String> = emptyList(),
    contacts: List<String> = emptyList(),
) : FavoritesStore {
    private val _apps = MutableStateFlow(apps)
    private val _contacts = MutableStateFlow(contacts)

    override val apps: Flow<List<String>> = _apps
    override val contacts: Flow<List<String>> = _contacts

    override suspend fun toggleApp(packageName: String) {
        _apps.value = if (packageName in _apps.value) _apps.value - packageName else _apps.value + packageName
    }

    override suspend fun toggleContact(lookupKey: String) {
        if (lookupKey.isBlank()) return
        _contacts.value = if (lookupKey in _contacts.value) _contacts.value - lookupKey else _contacts.value + lookupKey
    }
}
