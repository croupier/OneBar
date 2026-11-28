package dev.onebar.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.onebar.app.data.AnalyticsSink
import dev.onebar.app.data.AppRepository
import dev.onebar.app.data.CommunicationEntry
import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.data.ContactEntry
import dev.onebar.app.data.ContactRepository
import dev.onebar.app.data.FavoritesStore
import dev.onebar.app.data.HistoryStore
import dev.onebar.app.data.LaunchableApp
import dev.onebar.app.data.SearchOptions
import dev.onebar.app.data.SuggestRepository
import dev.onebar.app.data.rankApps
import dev.onebar.app.data.rankContacts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ContactRowUi(
    val entry: ContactEntry,
    /** Строка журнала: тип действия и время; подпись типа локализуем в UI. */
    val logKind: CommunicationKind? = null,
    val logTime: String = "",
)

data class SearchState(
    val query: String = "",
    val apps: List<LaunchableApp> = emptyList(),
    val contacts: List<ContactRowUi> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val contactsGranted: Boolean = false,
    val queries: List<String> = emptyList(),
    val historyRequested: Boolean = false,
    val favoritesMode: Boolean = false,
    val favApps: List<String> = emptyList(),
    val favContactKeys: List<String> = emptyList(),
    val searchOptions: SearchOptions = SearchOptions(),
    /** Попап-подсказка при входе в избранное, где ещё ничего нет. */
    val favoritesHint: Boolean = false,
)

class SearchViewModel(
    private val apps: AppRepository,
    private val contacts: ContactRepository,
    private val suggestions: SuggestRepository,
    private val history: HistoryStore,
    private val favoritesStore: FavoritesStore,
    /** Блокирующие обращения к PackageManager и контактам; в тестах — виртуальный диспетчер. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
    searchOptions: StateFlow<SearchOptions> = MutableStateFlow(SearchOptions()),
    private val analytics: AnalyticsSink = AnalyticsSink.None,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState(contactsGranted = contacts.hasPermission()))
    val state: StateFlow<SearchState> = _state.asStateFlow()

    @Volatile private var recentPackages: List<String> = emptyList()
    @Volatile private var appLaunchCounts: Map<String, Int> = emptyMap()
    private val journal = MutableStateFlow<List<CommunicationEntry>>(emptyList())

    private val _menuEntry = MutableStateFlow<ContactEntry?>(null)
    val menuEntry: StateFlow<ContactEntry?> = _menuEntry.asStateFlow()

    private val _menuApp = MutableStateFlow<LaunchableApp?>(null)
    val menuApp: StateFlow<LaunchableApp?> = _menuApp.asStateFlow()

    private var appsJob: Job? = null
    private var contactsJob: Job? = null
    private var suggestionsJob: Job? = null
    @Volatile private var appsGeneration: Long = 0L
    private val contactsMutex = Mutex()
    @Volatile private var contactsGeneration: Long = 0L

    init {
        analytics.track("app_open")
        viewModelScope.launch {
            searchOptions.collect { options ->
                val changed = _state.value.searchOptions != options
                _state.update { it.copy(searchOptions = options) }
                if (changed) {
                    loadApps(_state.value.query.trim())
                    loadContacts()
                    loadSuggestions(_state.value.query.trim())
                }
            }
        }
        viewModelScope.launch {
            history.recentApps.flowOn(io).distinctUntilChanged().collect { packages ->
                recentPackages = packages
                if (_state.value.query.isBlank()) loadApps("")
            }
        }
        viewModelScope.launch {
            history.appLaunchCounts.flowOn(io).distinctUntilChanged().collect { counts ->
                appLaunchCounts = counts
                if (_state.value.query.isBlank()) loadApps("")
            }
        }
        viewModelScope.launch {
            history.communications.flowOn(io).distinctUntilChanged().collect { entries ->
                journal.value = entries
                if (_state.value.query.isBlank()) loadContacts()
            }
        }
        viewModelScope.launch {
            history.queries.flowOn(io).distinctUntilChanged().collect { queries ->
                _state.update { it.copy(queries = queries, suggestions = webRows(queries, it.suggestions, it.query)) }
            }
        }
        viewModelScope.launch {
            favoritesStore.apps.flowOn(io).distinctUntilChanged().collect { packages ->
                _state.update { it.copy(favApps = packages) }
                loadApps(_state.value.query.trim())
            }
        }
        viewModelScope.launch {
            favoritesStore.contacts.flowOn(io).distinctUntilChanged().collect { keys ->
                _state.update { it.copy(favContactKeys = keys) }
                loadContacts()
            }
        }
        loadApps("")
        loadContacts()
    }

    fun onQueryChange(raw: String) {
        val trimmed = raw.trim()
        _state.update { it.copy(query = raw) }
        loadApps(trimmed)
        loadContacts()
        loadSuggestions(trimmed)
    }

    /** Обход PackageManager занимает сотни миллисекунд, поэтому вне главного потока. */
    private fun loadApps(trimmed: String) {
        appsJob?.cancel()
        val generation = ++appsGeneration
        val current = _state.value
        val favoritesMode = current.favoritesMode
        val favorites = current.favApps.toSet()
        val options = current.searchOptions
        val historyFirst = options.historyFavoritesFirst
        val limit = if (trimmed.isEmpty()) BLANK_LIMIT else SEARCH_LIMIT
        appsJob = viewModelScope.launch(io) {
            val candidates = apps.matching(
                trimmed,
                if (trimmed.isEmpty()) BLANK_LIMIT else APP_SEARCH_CANDIDATES,
                favorites,
                favoritesMode,
                options,
            )
            val extraPackages = (if (historyFirst && !favoritesMode) recentPackages else emptyList()) +
                current.favApps
            val extra = apps.byPackages(extraPackages)
            val found = orderApps(
                all = (candidates + extra).distinctBy { it.packageName },
                query = trimmed,
                limit = limit,
                favorites = favorites,
                favoritesMode = favoritesMode,
                historyFirst = historyFirst,
                favoriteOrder = current.favApps,
            )
            _state.update {
                if (generation == appsGeneration && it.query.trim() == trimmed &&
                    it.favoritesMode == favoritesMode && it.searchOptions == options
                ) {
                    it.copy(apps = found)
                } else it
            }
        }
    }

    private fun orderApps(
        all: List<LaunchableApp>,
        query: String,
        limit: Int,
        favorites: Set<String>,
        favoritesMode: Boolean,
        historyFirst: Boolean,
        favoriteOrder: List<String>,
    ): List<LaunchableApp> {
        val matched = if (query.isBlank()) {
            all.asSequence()
                .filter { !favoritesMode || it.packageName in favorites }
                .sortedBy { it.label.lowercase() }
                .toList()
        } else {
            rankApps(all, query, all.size, favorites, favoritesMode, _state.value.searchOptions)
        }
        val historyPosition = recentPackages.withIndex().associate { (index, packageName) -> packageName to index }
        val favoritePosition = favoriteOrder.withIndex().associate { (index, packageName) -> packageName to index }
        val matchedPosition = matched.withIndex().associate { (index, app) -> app.packageName to index }
        val history = if (historyFirst && !favoritesMode) {
            matched.filter { it.packageName in historyPosition }
                .sortedBy { historyPosition[it.packageName] }
        } else {
            emptyList()
        }
        val historyIds = history.mapTo(mutableSetOf()) { it.packageName }
        val favorite = matched.asSequence()
            .filter { it.packageName in favorites && it.packageName !in historyIds }
            .sortedWith(
                compareByDescending<LaunchableApp> { appLaunchCounts[it.packageName] ?: 0 }
                    .thenBy { favoritePosition[it.packageName] ?: Int.MAX_VALUE }
                    .thenBy { matchedPosition[it.packageName] ?: Int.MAX_VALUE },
            )
            .toList()
        val shownIds = historyIds + favorite.mapTo(mutableSetOf()) { it.packageName }
        val rest = matched.filter { it.packageName !in shownIds }
        return (history + favorite + rest).distinctBy { it.packageName }.take(limit)
    }

    /** Тап по звезде: тот же экран, только списки фильтруются по избранному. */
    fun onToggleFavoritesMode() {
        val current = _state.value
        if (!current.favoritesMode && current.favApps.isEmpty() && current.favContactKeys.isEmpty()) {
            // Пустое избранное показывать нечем: остаёмся на основном экране, меняет его только попап.
            _state.update { it.copy(favoritesHint = true) }
            return
        }
        _state.update { it.copy(favoritesMode = !current.favoritesMode) }
        analytics.track(if (current.favoritesMode) "favorites_closed" else "favorites_opened")
        onQueryChange(current.query)
    }

    fun onFavoritesHintDismissed() {
        _state.update { it.copy(favoritesHint = false) }
    }

    fun onToggleFavoriteApp(app: LaunchableApp) {
        val current = _state.value
        val previous = current.favApps
        val packages = if (app.packageName in previous) previous - app.packageName
        else previous + app.packageName
        val favorites = packages.toSet()
        _state.update {
            it.copy(
                favApps = packages,
                apps = orderApps(
                    all = it.apps,
                    query = it.query.trim(),
                    limit = it.apps.size,
                    favorites = favorites,
                    favoritesMode = it.favoritesMode,
                    historyFirst = it.searchOptions.historyFavoritesFirst,
                    favoriteOrder = packages,
                ),
            )
        }
        viewModelScope.launch {
            val saved = runCatching { favoritesStore.toggleApp(app.packageName) }.isSuccess
            if (saved) {
                analytics.track(if (app.packageName in previous) "favorite_app_removed" else "favorite_app_added")
            } else {
                _state.update { it.copy(favApps = previous) }
                loadApps(_state.value.query.trim())
            }
        }
    }

    fun onToggleFavoriteContact(entry: ContactEntry) {
        val current = _state.value
        val previous = current.favContactKeys
        val keys = if (entry.lookupKey in previous) previous - entry.lookupKey
        else previous + entry.lookupKey
        val favorites = keys.toSet()
        _state.update {
            val byId = it.contacts.associateBy { row -> row.entry.contactId }
            val entries = it.contacts.map { row -> row.entry }
            val ordered = if (it.query.isBlank() && it.searchOptions.historyFavoritesFirst && !it.favoritesMode) {
                blankQueryRows(journal.value, entries, keys).map { row -> row.entry }
            } else {
                orderContacts(
                    all = entries,
                    query = it.query.trim(),
                    limit = entries.size,
                    favorites = favorites,
                    favoritesMode = it.favoritesMode,
                    historyFirst = it.searchOptions.historyFavoritesFirst,
                    favoriteOrder = keys,
                )
            }
            it.copy(
                favContactKeys = keys,
                contacts = ordered.map { contact -> byId[contact.contactId] ?: ContactRowUi(contact) },
            )
        }
        viewModelScope.launch {
            val saved = runCatching { favoritesStore.toggleContact(entry.lookupKey) }.isSuccess
            if (saved) {
                analytics.track(if (entry.lookupKey in previous) "favorite_contact_removed" else "favorite_contact_added")
            } else {
                _state.update { it.copy(favContactKeys = previous) }
                loadContacts()
            }
        }
    }

    fun onAppMenuOpen(app: LaunchableApp) {
        _menuApp.value = app
    }

    fun onAppMenuClose() {
        _menuApp.value = null
    }

    /** Тап по полю: только он, а не автофокус при старте, вызывает историю запросов. */
    fun onHistoryRequested() {
        _state.update { it.copy(historyRequested = true) }
        _state.update {
            it.copy(suggestions = webRows(it.queries, it.suggestions, it.query, state = it))
        }
    }

    /** В историю запрос попадает только после явной отправки кнопкой «Поиск» или Enter. */
    fun onQuerySubmitted(text: String) {
        val query = text.trim()
        if (query.isNotEmpty()) {
            analytics.track("search_submitted")
            viewModelScope.launch { runCatching { history.pushQuery(query) } }
        }
    }

    fun onWebSearch(engine: WebEngine) {
        analytics.track(if (engine == WebEngine.YANDEX) "web_search_yandex" else "web_search_google")
    }

    fun onAppLaunched(app: LaunchableApp) {
        analytics.track("app_launched")
        viewModelScope.launch { runCatching { history.pushRecentApp(app.packageName) } }
    }

    fun onCommunication(kind: CommunicationKind, entry: ContactEntry) {
        val number = entry.number ?: return
        analytics.track(if (kind == CommunicationKind.SMS) "contact_sms" else "contact_call")
        viewModelScope.launch {
            val full = withContext(io) {
                runCatching {
                    history.pushCommunication(
                        CommunicationEntry(
                            kind = kind,
                            contactId = entry.contactId,
                            lookupKey = entry.lookupKey,
                            name = entry.name,
                            number = number,
                            timestamp = System.currentTimeMillis(),
                        )
                    )
                }
                contacts.details(entry.contactId)
            }
            if (full != null && _state.value.query.isBlank()) loadContacts()
        }
    }

    /** Пункт «Удалить из истории» активен, только если контакт уже есть в журнале OneBar. */
    fun inJournal(contactId: Long): Boolean = journal.value.any { it.contactId == contactId }

    /**
     * Строка журнала помнит имя и номер, а почту со строками мессенджеров отдаёт только
     * провайдер контактов, поэтому меню дозагружает контакт и открывается дважды: сначала
     * пункты, чуть позже — ряд приложений.
     */
    fun onMenuOpen(entry: ContactEntry) {
        _menuEntry.value = entry
        if (entry.email != null || entry.messengers.isNotEmpty()) return
        viewModelScope.launch(io) {
            val full = contacts.details(entry.contactId) ?: return@launch
            _menuEntry.update { if (it?.contactId == full.contactId) full else it }
        }
    }

    fun onMenuClose() {
        _menuEntry.value = null
    }

    fun onRemoveFromJournal(contactId: Long) {
        viewModelScope.launch { runCatching { history.removeCommunications(contactId) } }
    }

    fun onContactsPermissionResult(granted: Boolean) {
        updateContactsPermission(granted)
    }

    /** Возврат перепроверяет доступ к контактам и сбрасывает кэш установок и удалений. */
    fun onScreenResumed() {
        val contactsGranted = contacts.hasPermission()
        if (contactsGranted != _state.value.contactsGranted) updateContactsPermission(contactsGranted)
        viewModelScope.launch {
            val snapshot = recentPackages
            withContext(io) {
                apps.invalidate()
                apps.byPackages(snapshot)
            }
            loadApps(_state.value.query.trim())
        }
    }

    private fun updateContactsPermission(granted: Boolean) {
        if (granted) {
            _state.update { it.copy(contactsGranted = true) }
            loadContacts()
        } else {
            clearContacts()
        }
    }

    private fun clearContacts() {
        contactsJob?.cancel()
        contactsGeneration++
        _menuEntry.value = null
        _state.update { it.copy(contactsGranted = false, contacts = emptyList()) }
    }

    private fun loadContacts() {
        contactsJob?.cancel()
        if (!contacts.hasPermission()) {
            clearContacts()
            return
        }
        val current = _state.value
        val trimmed = current.query.trim()
        val favoritesMode = current.favoritesMode
        val favorites = current.favContactKeys.toSet()
        val options = current.searchOptions
        val historyFirst = options.historyFavoritesFirst
        val generation = ++contactsGeneration
        contactsJob = viewModelScope.launch(io) {
            if (trimmed.isNotEmpty()) delay(DEBOUNCE_MS)
            // ContentResolver.query блокирует поток и не реагирует на отмену. Mutex не даёт
            // следующему запросу стартовать одновременно, а generation отсекает даже A→B→A.
            contactsMutex.withLock {
                ensureActive()
                if (generation != contactsGeneration) return@withLock
                if (trimmed.isEmpty()) {
                    val base = contacts.search("", BLANK_LIMIT)
                    val entries = journal.value
                    val recent = contacts.byLookupKeys(
                        entries.map { it.lookupKey }.distinct().filter { it.isNotBlank() },
                    )
                    val favorite = contacts.byLookupKeys(current.favContactKeys)
                    if (generation != contactsGeneration) return@withLock
                    val cached = (base + recent + favorite).distinctBy { it.contactId }
                    val rows = if (favoritesMode || !historyFirst) {
                        orderContacts(
                            all = cached,
                            query = "",
                            limit = BLANK_LIMIT,
                            favorites = favorites,
                            favoritesMode = favoritesMode,
                            historyFirst = historyFirst,
                            favoriteOrder = current.favContactKeys,
                        ).map { ContactRowUi(it) }
                    } else {
                        blankQueryRows(entries, cached, current.favContactKeys)
                    }
                    _state.update {
                        if (generation == contactsGeneration && it.query.isBlank() &&
                            it.favoritesMode == favoritesMode && it.searchOptions == options
                        ) {
                            it.copy(contacts = rows)
                        } else it
                    }
                } else {
                    val base = contacts.search(
                        trimmed,
                        CONTACT_SEARCH_CANDIDATES,
                        favorites,
                        favoritesMode,
                        options,
                    )
                    val historyEntries = if (historyFirst && !favoritesMode) {
                        contacts.byLookupKeys(
                            journal.value.map { it.lookupKey }.distinct().filter { it.isNotBlank() },
                        )
                    } else {
                        emptyList()
                    }
                    val favoriteEntries = contacts.byLookupKeys(current.favContactKeys)
                    if (generation != contactsGeneration) return@withLock
                    val ordered = orderContacts(
                        all = (base + historyEntries + favoriteEntries).distinctBy { it.contactId },
                        query = trimmed,
                        limit = SEARCH_LIMIT,
                        favorites = favorites,
                        favoritesMode = favoritesMode,
                        historyFirst = historyFirst,
                        favoriteOrder = current.favContactKeys,
                    )
                    _state.update {
                        if (generation == contactsGeneration &&
                            it.query.trim() == trimmed && it.favoritesMode == favoritesMode &&
                            it.searchOptions == options
                        ) {
                            it.copy(contacts = ordered.map { contact -> contactRow(contact) })
                        } else it
                    }
                }
            }
        }
    }

    private fun orderContacts(
        all: List<ContactEntry>,
        query: String,
        limit: Int,
        favorites: Set<String>,
        favoritesMode: Boolean,
        historyFirst: Boolean,
        favoriteOrder: List<String>,
    ): List<ContactEntry> {
        val matched = if (query.isBlank()) {
            all.asSequence()
                .filter { !favoritesMode || it.lookupKey in favorites }
                .sortedBy { it.name.lowercase() }
                .toList()
        } else {
            rankContacts(all, query, all.size, favorites, favoritesMode, _state.value.searchOptions)
        }
        val latest = journal.value.sortedByDescending { it.timestamp }.distinctBy { it.contactId }
        val historyPosition = latest.withIndex().associate { (index, entry) -> entry.contactId to index }
        val favoritePosition = favoriteOrder.withIndex().associate { (index, key) -> key to index }
        val matchedPosition = matched.withIndex().associate { (index, entry) -> entry.contactId to index }
        val counts = journal.value.groupBy { it.contactId }
            .mapValues { (_, entries) -> entries.sumOf { it.launchCount } }
        val history = if (historyFirst && !favoritesMode) {
            matched.filter { it.contactId in historyPosition }
                .sortedBy { historyPosition[it.contactId] }
        } else {
            emptyList()
        }
        val historyIds = history.mapTo(mutableSetOf()) { it.contactId }
        val favorite = matched.asSequence()
            .filter { it.lookupKey in favorites && it.contactId !in historyIds }
            .sortedWith(
                compareByDescending<ContactEntry> { counts[it.contactId] ?: it.timesContacted }
                    .thenBy { favoritePosition[it.lookupKey] ?: Int.MAX_VALUE }
                    .thenBy { matchedPosition[it.contactId] ?: Int.MAX_VALUE },
            )
            .toList()
        val shownIds = historyIds + favorite.mapTo(mutableSetOf()) { it.contactId }
        val rest = matched.filter { it.contactId !in shownIds }
        return (history + favorite + rest).distinctBy { it.contactId }.take(limit)
    }

    /** История → избранное по частоте → остальное по алфавиту; дубли контакта убираем сразу. */
    private fun blankQueryRows(
        entries: List<CommunicationEntry>,
        all: List<ContactEntry>,
        favoriteOrder: List<String>,
    ): List<ContactRowUi> {
        val latest = entries.sortedByDescending { it.timestamp }.distinctBy { it.contactId }
        val byId = all.associateBy { it.contactId }
        val history = latest.map { logRow(it, byId[it.contactId]) }
        val historyIds = history.mapTo(mutableSetOf()) { it.entry.contactId }
        val favorites = favoriteOrder.toSet()
        val favoritePosition = favoriteOrder.withIndex().associate { (index, key) -> key to index }
        val counts = entries.groupBy { it.contactId }
            .mapValues { (_, logs) -> logs.sumOf { it.launchCount } }
        val favorite = all.asSequence()
            .filter { it.lookupKey in favorites && it.contactId !in historyIds }
            .sortedWith(
                compareByDescending<ContactEntry> { counts[it.contactId] ?: it.timesContacted }
                    .thenBy { favoritePosition[it.lookupKey] ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase() },
            )
            .map { ContactRowUi(it) }
            .toList()
        val shownIds = historyIds + favorite.mapTo(mutableSetOf()) { it.entry.contactId }
        val rest = all.asSequence()
            .filter { it.contactId !in shownIds }
            .sortedBy { it.name.lowercase() }
            .map { ContactRowUi(it) }
            .toList()
        return (history + favorite + rest).distinctBy { it.entry.contactId }.take(BLANK_LIMIT)
    }

    private fun contactRow(contact: ContactEntry): ContactRowUi {
        val entry = journal.value.firstOrNull { it.contactId == contact.contactId } ?: return ContactRowUi(contact)
        return logRow(entry, contact)
    }

    private fun logRow(entry: CommunicationEntry, contact: ContactEntry?): ContactRowUi = ContactRowUi(
        entry = contact?.copy(name = entry.name, number = entry.number)
            ?: ContactEntry(entry.contactId, entry.lookupKey, entry.name, entry.number),
        logKind = entry.kind,
        logTime = formatTime(entry.timestamp),
    )

    private fun formatTime(millis: Long): String {
        val zone = ZoneId.systemDefault()
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        val pattern = if (dateTime.toLocalDate() == LocalDate.now(zone)) "HH:mm" else "dd.MM"
        return DateTimeFormatter.ofPattern(pattern).format(dateTime)
    }

    /** В избранном веб-подсказок нет: у них нет контекстного меню, добавить их нельзя. */
    private fun loadSuggestions(trimmed: String) {
        suggestionsJob?.cancel()
        val favoritesMode = _state.value.favoritesMode
        val options = _state.value.searchOptions
        if (trimmed.length < MIN_SUGGEST_QUERY_LENGTH || !options.webSuggestions || favoritesMode) {
            _state.update { it.copy(suggestions = webRows(it.queries, emptyList(), it.query)) }
            return
        }
        suggestionsJob = viewModelScope.launch {
            _state.update { it.copy(suggestions = webRows(it.queries, emptyList(), it.query)) }
            delay(DEBOUNCE_MS)
            val found = try {
                suggestions.suggest(trimmed, Locale.getDefault().language).distinct()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            // Ответ мог прийти уже после выхода из запроса или входа в избранное.
            _state.update {
                if (!it.favoritesMode && it.query.trim() == trimmed && it.searchOptions == options) {
                    it.copy(suggestions = webRows(it.queries, found, it.query))
                } else it
            }
        }
    }

    private fun webRows(
        queries: List<String>,
        suggestions: List<String>,
        rawQuery: String,
        state: SearchState = _state.value,
    ): List<String> {
        val current = state
        if (current.favoritesMode) return emptyList()
        val trimmed = rawQuery.trim()
        val history = if (!current.searchOptions.historyFavoritesFirst) {
            emptyList()
        } else if (trimmed.isEmpty()) {
            if (current.historyRequested) queries else emptyList()
        } else {
            queries.filter { it.contains(trimmed, ignoreCase = true) }
        }
        return (history + suggestions)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(WEB_LIMIT)
    }

    private companion object {
        const val BLANK_LIMIT = 50
        const val SEARCH_LIMIT = 20
        const val APP_SEARCH_CANDIDATES = 500
        const val CONTACT_SEARCH_CANDIDATES = 200
        const val WEB_LIMIT = 50
        const val MIN_SUGGEST_QUERY_LENGTH = 3
        const val DEBOUNCE_MS = 200L
    }
}
