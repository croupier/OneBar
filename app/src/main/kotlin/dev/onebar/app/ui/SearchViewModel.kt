package dev.onebar.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.onebar.app.data.AppRepository
import dev.onebar.app.data.CommunicationEntry
import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.data.ContactEntry
import dev.onebar.app.data.ContactRepository
import dev.onebar.app.data.FavoritesStore
import dev.onebar.app.data.HistoryStore
import dev.onebar.app.data.LaunchableApp
import dev.onebar.app.data.SuggestRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState(contactsGranted = contacts.hasPermission()))
    val state: StateFlow<SearchState> = _state.asStateFlow()

    @Volatile private var recent: List<LaunchableApp> = emptyList()

    // Строки пустого запроса строятся из журнала и избранного в одном авторе: раньше две
    // корутины писали список напрямую и перебивали друг друга — журнал то появлялся, то нет.
    private val journal = MutableStateFlow<List<CommunicationEntry>>(emptyList())
    private val favorites = MutableStateFlow<List<ContactEntry>>(emptyList())

    private val _menuEntry = MutableStateFlow<ContactEntry?>(null)
    val menuEntry: StateFlow<ContactEntry?> = _menuEntry.asStateFlow()

    private val _menuApp = MutableStateFlow<LaunchableApp?>(null)
    val menuApp: StateFlow<LaunchableApp?> = _menuApp.asStateFlow()

    private var appsJob: Job? = null
    private var contactsJob: Job? = null
    private var suggestionsJob: Job? = null
    private val contactsMutex = Mutex()
    @Volatile private var contactsGeneration: Long = 0L

    init {
        viewModelScope.launch {
            withContext(io) { apps.matching("", 1) }
            history.recentApps.collect { packages ->
                val found = withContext(io) { apps.byPackages(packages) }
                recent = found
                _state.update { if (it.query.isBlank() && !it.favoritesMode) it.copy(apps = recent) else it }
            }
        }
        viewModelScope.launch(io) {
            history.communications.collect { entries -> journal.value = entries }
        }
        viewModelScope.launch {
            history.queries.collect { queries -> _state.update { it.copy(queries = queries) } }
        }
        viewModelScope.launch {
            favoritesStore.apps.collect { packages ->
                _state.update { it.copy(favApps = packages) }
                reloadBlankApps()
            }
        }
        viewModelScope.launch {
            favoritesStore.contacts.collect { keys ->
                _state.update { it.copy(favContactKeys = keys) }
                reloadBlankContacts()
            }
        }
        viewModelScope.launch {
            combine(journal, favorites) { entries, favs -> blankQueryRows(entries, favs) }
                .collect { rows ->
                    _state.update { if (it.query.isBlank() && !it.favoritesMode) it.copy(contacts = rows) else it }
                }
        }
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
        val favoritesMode = _state.value.favoritesMode
        if (trimmed.isEmpty()) {
            if (favoritesMode) reloadBlankApps() else _state.update { it.copy(apps = recent) }
            return
        }
        appsJob = viewModelScope.launch(io) {
            // Избранные участвуют в ранжировании, а не отфильтровываются после: лимит должен
            // отсекать наименее релевантное совпадение, а не совпадение вне избранного.
            val found = apps.matching(
                trimmed,
                LIMIT,
                _state.value.favApps.toSet(),
                favoritesOnly = favoritesMode,
            )
            _state.update {
                if (it.query.trim() == trimmed && it.favoritesMode == favoritesMode) it.copy(apps = found) else it
            }
        }
    }

    /** Избранные приложения при пустом запросе: порядок — как список избранного. */
    private fun reloadBlankApps() {
        val s = _state.value
        if (!s.favoritesMode || s.query.isNotBlank()) return
        appsJob?.cancel()
        appsJob = viewModelScope.launch(io) {
            val fav = apps.byPackages(_state.value.favApps)
            _state.update { if (it.favoritesMode && it.query.isBlank()) it.copy(apps = fav) else it }
        }
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
        onQueryChange(current.query)
    }

    fun onFavoritesHintDismissed() {
        _state.update { it.copy(favoritesHint = false) }
    }

    fun onToggleFavoriteApp(app: LaunchableApp) {
        viewModelScope.launch { favoritesStore.toggleApp(app.packageName) }
    }

    fun onToggleFavoriteContact(entry: ContactEntry) {
        viewModelScope.launch { favoritesStore.toggleContact(entry.lookupKey) }
    }

    fun onAppMenuOpen(app: LaunchableApp) {
        _menuApp.value = app
    }

    fun onAppMenuClose() {
        _menuApp.value = null
    }

    /** Тап по полю: только он, а не автофокус при старте, вызывает историю. */
    fun onHistoryRequested() {
        _state.update { it.copy(historyRequested = true) }
    }

    /** В историю запрос попадает только после явной отправки кнопкой «Поиск» или Enter. */
    fun onQuerySubmitted(text: String) {
        val query = text.trim()
        if (query.isNotEmpty()) viewModelScope.launch { history.pushQuery(query) }
    }

    fun onAppLaunched(app: LaunchableApp) {
        viewModelScope.launch { history.pushRecentApp(app.packageName) }
    }

    fun onCommunication(kind: CommunicationKind, entry: ContactEntry) {
        val number = entry.number ?: return
        viewModelScope.launch {
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
        viewModelScope.launch { history.removeCommunications(contactId) }
    }

    fun onContactsPermissionResult(granted: Boolean) {
        _state.update { it.copy(contactsGranted = granted) }
        if (granted) loadContacts()
    }

    /** Возврат в OneBar сбрасывает кэш приложений: установки и удаления видны сразу. */
    fun onScreenResumed() {
        viewModelScope.launch {
            val snapshot = recent
            val refreshed = withContext(io) {
                apps.invalidate()
                apps.byPackages(snapshot.map { it.packageName })
            }
            // Пока PackageManager работал, журнал мог добавить другое недавнее приложение.
            if (recent == snapshot) recent = refreshed
            _state.update { if (it.query.isBlank() && !it.favoritesMode) it.copy(apps = recent) else it }
            loadApps(_state.value.query.trim())
        }
    }

    private fun loadContacts() {
        contactsJob?.cancel()
        if (!contacts.hasPermission()) {
            contactsGeneration++
            favorites.value = emptyList()
            _state.update { it.copy(contacts = emptyList()) }
            return
        }
        val trimmed = _state.value.query.trim()
        val favoritesMode = _state.value.favoritesMode
        if (trimmed.isEmpty() && favoritesMode) {
            reloadBlankContacts()
            return
        }
        val generation = ++contactsGeneration
        contactsJob = viewModelScope.launch(io) {
            if (trimmed.isNotEmpty()) delay(DEBOUNCE_MS)
            // ContentResolver.query блокирует поток и не реагирует на отмену. Mutex не даёт
            // следующему запросу старовать одновременно, а generation отсекает даже A→B→A.
            contactsMutex.withLock {
                ensureActive()
                if (generation != contactsGeneration) return@withLock
                if (trimmed.isEmpty()) {
                    val found = contacts.search("", LIMIT)
                    if (generation != contactsGeneration) return@withLock
                    favorites.value = found
                    // После выхода из избранного StateFlow может не эмитить: список тот же, а
                    // состояние контактов там осталось фильтрованным. Перезаписываем напрямую.
                    val rows = blankQueryRows(journal.value, found)
                    _state.update {
                        if (generation == contactsGeneration && it.query.isBlank() && !it.favoritesMode) {
                            it.copy(contacts = rows)
                        } else it
                    }
                } else {
                    val rows = contacts.search(
                        trimmed,
                        LIMIT,
                        _state.value.favContactKeys.toSet(),
                        favoritesOnly = favoritesMode,
                    ).map { ContactRowUi(it) }
                    _state.update {
                        if (generation == contactsGeneration &&
                            it.query.trim() == trimmed && it.favoritesMode == favoritesMode
                        ) {
                            it.copy(contacts = rows)
                        } else it
                    }
                }
            }
        }
    }

    /** Избранные контакты при пустом запросе: порядок — как список избранного. */
    private fun reloadBlankContacts() {
        val s = _state.value
        if (!s.favoritesMode || s.query.isNotBlank() || !contacts.hasPermission()) return
        contactsJob?.cancel()
        val generation = ++contactsGeneration
        contactsJob = viewModelScope.launch(io) {
            contactsMutex.withLock {
                ensureActive()
                if (generation != contactsGeneration) return@withLock
                val rows = contacts.byLookupKeys(_state.value.favContactKeys).map { ContactRowUi(it) }
                _state.update {
                    if (generation == contactsGeneration && it.favoritesMode && it.query.isBlank()) {
                        it.copy(contacts = rows)
                    } else it
                }
            }
        }
    }

    /** Журнал и избранные пересекаются по контактам: без distinctBy LazyColumn падает на дубле ключа. */
    private fun blankQueryRows(
        entries: List<CommunicationEntry>,
        favs: List<ContactEntry>,
    ): List<ContactRowUi> =
        (entries.map { logRow(it) } + favs.map { ContactRowUi(it) })
            .distinctBy { it.entry.contactId }

    private fun logRow(entry: CommunicationEntry): ContactRowUi = ContactRowUi(
        entry = ContactEntry(entry.contactId, entry.lookupKey, entry.name, entry.number),
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
        if (trimmed.isEmpty() || favoritesMode) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestionsJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val found = try {
                suggestions.suggest(trimmed).distinct()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            // Ответ мог прийти уже после выхода из запроса или входа в избранное.
            _state.update {
                if (!it.favoritesMode && it.query.trim() == trimmed) it.copy(suggestions = found) else it
            }
        }
    }

    private companion object {
        // С запасом на свёрнутую клавиатуру: два ряда плиток и до девяти строк контактов
        // влезает без прокрутки, лишнее досказывается прокруткой самой панели.
        const val LIMIT = 20
        const val DEBOUNCE_MS = 200L
    }
}
