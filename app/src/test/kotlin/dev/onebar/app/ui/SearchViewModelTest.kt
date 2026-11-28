package dev.onebar.app.ui

import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.testing.FakeApps
import dev.onebar.app.testing.FakeContacts
import dev.onebar.app.testing.FakeFavorites
import dev.onebar.app.testing.FakeHistory
import dev.onebar.app.testing.FakeSuggestions
import dev.onebar.app.testing.MainDispatcherRule
import dev.onebar.app.testing.app
import dev.onebar.app.testing.contact
import dev.onebar.app.testing.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Логика экрана на виртуальном времени: Main и io одного scheduler'а, поэтому дебаунс
 * подсказок и отмена поисков проверяются без реальных пауз.
 */
class SearchViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private fun viewModel(
        apps: FakeApps = FakeApps(),
        contacts: FakeContacts = FakeContacts(),
        suggestions: FakeSuggestions = FakeSuggestions(),
        history: FakeHistory = FakeHistory(),
        favorites: FakeFavorites = FakeFavorites(),
        historyFavoritesFirst: StateFlow<Boolean> = MutableStateFlow(true),
    ) = SearchViewModel(
        apps, contacts, suggestions, history, favorites,
        io = main.dispatcher,
        historyFavoritesFirst = historyFavoritesFirst,
    )

    @Test
    fun `пустой запрос показывает недавние приложения в порядке журнала`() = runTest(main.dispatcher) {
        val vm = viewModel(
            apps = FakeApps(known = listOf(app("com.a", "A"), app("com.b", "B"))),
            history = FakeHistory(recentApps = listOf("com.b", "com.a")),
        )
        advanceUntilIdle()

        assertEquals(listOf("com.b", "com.a"), vm.state.value.apps.map { it.packageName })
    }

    @Test
    fun `пустой запрос приложений история избранное частота алфавит без дублей`() = runTest(main.dispatcher) {
        val apps = FakeApps(
            known = listOf(
                app("com.alpha", "Alpha"),
                app("com.hist.old", "Z Old"),
                app("com.fav.low", "A Fav Low"),
                app("com.zeta", "Zeta"),
                app("com.fav.high", "B Fav High"),
                app("com.hist.new", "Y New"),
            )
        )
        val vm = viewModel(
            apps = apps,
            history = FakeHistory(
                recentApps = listOf("com.hist.new", "com.hist.old", "com.fav.high"),
                appLaunchCounts = mapOf("com.fav.high" to 20, "com.fav.low" to 5),
            ),
            favorites = FakeFavorites(apps = listOf("com.fav.low", "com.fav.high")),
        )
        advanceUntilIdle()

        assertEquals(
            listOf("com.hist.new", "com.hist.old", "com.fav.high", "com.fav.low", "com.alpha", "com.zeta"),
            vm.state.value.apps.map { it.packageName },
        )
    }

    @Test
    fun `пустой запрос контактов история частота избранного алфавит без дублей`() = runTest(main.dispatcher) {
        val vm = viewModel(
            contacts = FakeContacts(
                known = listOf(
                    contact(1, "New"),
                    contact(2, "Old"),
                    contact(3, "Fav Low", timesContacted = 2),
                    contact(4, "Fav High", timesContacted = 10),
                    contact(5, "Alpha"),
                )
            ),
            history = FakeHistory(
                communications = listOf(
                    log(1, "New", CommunicationKind.CALL, timestamp = 200, launchCount = 3),
                    log(1, "New", CommunicationKind.SMS, timestamp = 100, launchCount = 2),
                    log(2, "Old", timestamp = 50),
                )
            ),
            favorites = FakeFavorites(contacts = listOf("lookup-3", "lookup-4")),
        )
        advanceUntilIdle()

        val rows = vm.state.value.contacts
        assertEquals(listOf(1L, 2L, 4L, 3L, 5L), rows.map { it.entry.contactId })
        assertEquals(1, rows.count { it.entry.contactId == 1L })
    }

    @Test
    fun `непустой поиск ставит совпадения истории перед избранным и остальными`() = runTest(main.dispatcher) {
        val vm = viewModel(
            apps = FakeApps(
                known = listOf(
                    app("com.best", "Maps Best"),
                    app("com.history", "Maps History"),
                    app("com.favorite", "Maps Favorite"),
                )
            ),
            history = FakeHistory(recentApps = listOf("com.history")),
            favorites = FakeFavorites(apps = listOf("com.favorite")),
        )
        advanceUntilIdle()

        vm.onQueryChange("maps")
        advanceUntilIdle()

        assertEquals(listOf("com.history", "com.favorite", "com.best"), vm.state.value.apps.map { it.packageName })
    }

    @Test
    fun `выключенный приоритет истории оставляет избранное и релевантность`() = runTest(main.dispatcher) {
        val vm = viewModel(
            apps = FakeApps(
                known = listOf(
                    app("com.best", "Maps Best"),
                    app("com.history", "Maps History"),
                    app("com.favorite", "Maps Favorite"),
                )
            ),
            history = FakeHistory(recentApps = listOf("com.history")),
            favorites = FakeFavorites(apps = listOf("com.favorite")),
            historyFavoritesFirst = MutableStateFlow(false),
        )
        advanceUntilIdle()

        vm.onQueryChange("maps")
        advanceUntilIdle()

        assertEquals(listOf("com.favorite", "com.best", "com.history"), vm.state.value.apps.map { it.packageName })
    }

    @Test
    fun `в самом избранном приложения идут по частоте запуска`() = runTest(main.dispatcher) {
        val vm = viewModel(
            apps = FakeApps(known = listOf(app("com.low", "Low"), app("com.high", "High"))),
            history = FakeHistory(
                appLaunchCounts = mapOf("com.low" to 2, "com.high" to 20),
            ),
            favorites = FakeFavorites(apps = listOf("com.low", "com.high")),
        )
        advanceUntilIdle()

        vm.onToggleFavoritesMode()
        advanceUntilIdle()

        assertEquals(listOf("com.high", "com.low"), vm.state.value.apps.map { it.packageName })
    }

    @Test
    fun `в самом избранном контакты идут по частоте действий`() = runTest(main.dispatcher) {
        val vm = viewModel(
            contacts = FakeContacts(known = listOf(contact(1, "Low"), contact(2, "High"))),
            history = FakeHistory(
                communications = listOf(
                    log(1, "Low", timestamp = 300, launchCount = 2),
                    log(2, "High", timestamp = 200, launchCount = 20),
                )
            ),
            favorites = FakeFavorites(contacts = listOf("lookup-1", "lookup-2")),
        )
        advanceUntilIdle()

        vm.onToggleFavoritesMode()
        advanceUntilIdle()

        assertEquals(listOf("High", "Low"), vm.state.value.contacts.map { it.entry.name })
    }

    @Test
    fun `веб-история идёт первой и не дублирует подсказки`() = runTest(main.dispatcher) {
        val vm = viewModel(
            history = FakeHistory(queries = listOf("Coffee", "Pizza")),
            suggestions = FakeSuggestions(replies = mapOf("cof" to listOf("coffee", "coffee beans"))),
        )

        vm.onQueryChange("cof")
        advanceUntilIdle()

        assertEquals(listOf("Coffee", "coffee beans"), vm.state.value.suggestions)
    }

    @Test
    fun `пустые списки ограничены пятьюдесятью записями`() = runTest(main.dispatcher) {
        val vm = viewModel(
            apps = FakeApps(known = (1..60).map { app("com.$it", "App $it") }),
            contacts = FakeContacts(known = (1..60).map { contact(it.toLong(), "Contact $it") }),
        )
        advanceUntilIdle()

        assertEquals(50, vm.state.value.apps.size)
        assertEquals(50, vm.state.value.contacts.size)
    }

    @Test
    fun `контакт из журнала и из избранного показывается один раз`() = runTest(main.dispatcher) {
        val vm = viewModel(
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            history = FakeHistory(communications = listOf(log(1, "Ann", CommunicationKind.SMS))),
            favorites = FakeFavorites(contacts = listOf("lookup-1")),
        )
        advanceUntilIdle()

        val rows = vm.state.value.contacts
        assertEquals(1, rows.size)
        assertEquals(CommunicationKind.SMS, rows.first().logKind)
    }

    @Test
    fun `фото контакта из журнала не теряется`() = runTest(main.dispatcher) {
        val photoUri = "content://com.android.contacts/contacts/1/photo"
        val vm = viewModel(
            contacts = FakeContacts(known = listOf(contact(1, "Ann", photoUri = photoUri))),
            history = FakeHistory(communications = listOf(log(1, "Ann", CommunicationKind.SMS))),
        )
        advanceUntilIdle()

        assertEquals(photoUri, vm.state.value.contacts.single().entry.photoUri)
    }

    @Test
    fun `набранный запрос ставит избранные вперёд, а в избранном оставляет только избранные`() = runTest(main.dispatcher) {
        val apps = FakeApps(known = listOf(app("com.a", "Maps"), app("com.b", "appmaps")))
        val vm = viewModel(apps = apps, favorites = FakeFavorites(apps = listOf("com.b")))

        vm.onQueryChange("map")
        advanceUntilIdle()
        assertEquals(listOf("com.b", "com.a"), vm.state.value.apps.map { it.packageName })

        vm.onToggleFavoritesMode()
        advanceUntilIdle()
        assertEquals(listOf("com.b"), vm.state.value.apps.map { it.packageName })
    }

    @Test
    fun `в избранном контакт по запросу ищется только среди избранных`() = runTest(main.dispatcher) {
        val vm = viewModel(
            contacts = FakeContacts(known = listOf(contact(1, "Аня"), contact(2, "Анна Петрова"))),
            favorites = FakeFavorites(contacts = listOf("lookup-2")),
        )
        vm.onToggleFavoritesMode()
        vm.onQueryChange("анна")
        advanceUntilIdle()

        assertEquals(listOf("Анна Петрова"), vm.state.value.contacts.map { it.entry.name })
    }

    @Test
    fun `избранное при пустом запросе идёт в порядке списка избранного`() = runTest(main.dispatcher) {
        val vm = viewModel(
            apps = FakeApps(known = listOf(app("com.a", "A"), app("com.b", "B"))),
            favorites = FakeFavorites(apps = listOf("com.b", "com.a")),
        )
        // Избранное читается асинхронно: до тапа по звезде состояние уже должно быть загружено.
        advanceUntilIdle()

        vm.onToggleFavoritesMode()
        advanceUntilIdle()

        assertEquals(listOf("com.b", "com.a"), vm.state.value.apps.map { it.packageName })
    }

    @Test
    fun `выход из избранного возвращает недавние приложения`() = runTest(main.dispatcher) {
        val vm = viewModel(
            apps = FakeApps(known = listOf(app("com.a", "A"), app("com.b", "B"))),
            history = FakeHistory(recentApps = listOf("com.a", "com.b")),
            favorites = FakeFavorites(apps = listOf("com.b")),
        )
        advanceUntilIdle()
        assertEquals(listOf("com.a", "com.b"), vm.state.value.apps.map { it.packageName })

        vm.onToggleFavoritesMode()
        advanceUntilIdle()
        assertEquals(listOf("com.b"), vm.state.value.apps.map { it.packageName })

        vm.onToggleFavoritesMode()
        advanceUntilIdle()
        assertFalse(vm.state.value.favoritesMode)
        assertEquals(listOf("com.a", "com.b"), vm.state.value.apps.map { it.packageName })
    }

    @Test
    fun `вход в пустое избранное показывает подсказку и гаснет по закрытию`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onToggleFavoritesMode()
        assertTrue(vm.state.value.favoritesHint)
        // Показывать нечего, поэтому экран остаётся основным: избранное открываем только с содержимым.
        assertFalse(vm.state.value.favoritesMode)

        vm.onFavoritesHintDismissed()
        assertFalse(vm.state.value.favoritesHint)
    }

    @Test
    fun `в избранном веб-подсказки не приходят, даже если запрос начат до входа`() = runTest(main.dispatcher) {
        val suggestions = FakeSuggestions(replies = mapOf("коф" to listOf("кофе")))
        val vm = viewModel(
            apps = FakeApps(known = listOf(app("com.a", "A"))),
            suggestions = suggestions,
            favorites = FakeFavorites(apps = listOf("com.a")),
        )
        advanceUntilIdle()

        vm.onQueryChange("коф")
        vm.onToggleFavoritesMode()
        advanceTimeBy(300)
        runCurrent()

        assertTrue(vm.state.value.favoritesMode)
        assertTrue(vm.state.value.suggestions.isEmpty())
        assertTrue(suggestions.asked.isEmpty())
    }

    @Test
    fun `вход в непустое избранное подсказку не показывает`() = runTest(main.dispatcher) {
        val vm = viewModel(favorites = FakeFavorites(apps = listOf("com.a")))
        advanceUntilIdle()

        vm.onToggleFavoritesMode()
        assertFalse(vm.state.value.favoritesHint)
    }

    @Test
    fun `история запросов ждёт тапа по полю`() = runTest(main.dispatcher) {
        val vm = viewModel(history = FakeHistory(queries = listOf("кофе", "пицца")))
        advanceUntilIdle()

        assertEquals(listOf("кофе", "пицца"), vm.state.value.queries)
        assertFalse(vm.state.value.historyRequested)

        vm.onHistoryRequested()
        assertTrue(vm.state.value.historyRequested)
        assertEquals(listOf("кофе", "пицца"), vm.state.value.suggestions)
    }

    @Test
    fun `выключенная история скрывает старые запросы и не пишет новые`() = runTest(main.dispatcher) {
        val history = FakeHistory(queries = listOf("кофе"), saveHistory = false)
        val vm = viewModel(history = history)
        advanceUntilIdle()
        assertTrue(vm.state.value.queries.isEmpty())

        vm.onQuerySubmitted("пицца")
        advanceUntilIdle()
        assertTrue(history.pushedQueries.isEmpty())

        history.setSaveHistory(true)
        advanceUntilIdle()
        assertEquals(listOf("кофе"), vm.state.value.queries)
    }

    @Test
    fun `очистка истории сразу убирает запросы с главного экрана`() = runTest(main.dispatcher) {
        val history = FakeHistory(queries = listOf("кофе", "пицца"))
        val vm = viewModel(history = history)
        advanceUntilIdle()
        vm.onHistoryRequested()
        assertEquals(listOf("кофе", "пицца"), vm.state.value.queries)

        history.clearQueries()
        advanceUntilIdle()

        assertTrue(vm.state.value.queries.isEmpty())
    }

    @Test
    fun `подсказки приходят после дебаунса и без дублей`() = runTest(main.dispatcher) {
        val vm = viewModel(
            suggestions = FakeSuggestions(
                replies = mapOf("коф" to listOf("кофе", "кофе молотый", "кофе"))
            )
        )
        vm.onQueryChange("коф")

        advanceTimeBy(199)
        runCurrent()
        assertTrue(vm.state.value.suggestions.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("кофе", "кофе молотый"), vm.state.value.suggestions)
    }

    @Test
    fun `контакты приходят после дебаунса`() = runTest(main.dispatcher) {
        val contacts = FakeContacts(known = listOf(contact(1, "Ann")))
        val vm = viewModel(contacts = contacts)
        advanceUntilIdle()
        contacts.searched.clear()

        vm.onQueryChange("ann")
        advanceTimeBy(199)
        runCurrent()
        assertTrue(contacts.searched.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("ann"), contacts.searched)
        assertEquals(listOf("Ann"), vm.state.value.contacts.map { it.entry.name })
    }

    @Test
    fun `новый запрос отменяет отложенный поиск контактов старого`() = runTest(main.dispatcher) {
        val contacts = FakeContacts(known = listOf(contact(1, "Ann"), contact(2, "Anna")))
        val vm = viewModel(contacts = contacts)
        advanceUntilIdle()
        contacts.searched.clear()

        vm.onQueryChange("an")
        advanceTimeBy(100)
        vm.onQueryChange("ann")
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf("ann"), contacts.searched)
        assertEquals(listOf("Ann", "Anna"), vm.state.value.contacts.map { it.entry.name })
    }

    @Test
    fun `новый запрос отменяет отложенные подсказки старого`() = runTest(main.dispatcher) {
        val suggestions = FakeSuggestions(
            replies = mapOf("коф" to listOf("кофе"), "кофев" to listOf("кофе в зёрнах"))
        )
        val vm = viewModel(suggestions = suggestions)

        vm.onQueryChange("коф")
        advanceTimeBy(100)
        vm.onQueryChange("кофев")
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf("кофев"), suggestions.asked)
        assertEquals(listOf("кофе в зёрнах"), vm.state.value.suggestions)
    }

    @Test
    fun `запуск приложения пишет пакет в недавние, но не запрос в историю`() = runTest(main.dispatcher) {
        val history = FakeHistory()
        val vm = viewModel(history = history)
        vm.onQueryChange("  Maps  ")
        advanceUntilIdle()

        vm.onAppLaunched(app("com.a", "Maps"))
        advanceUntilIdle()

        assertTrue(history.pushedQueries.isEmpty())
        assertEquals(listOf("com.a"), history.pushedApps)
    }

    @Test
    fun `набранный_запрос_попадает_в_историю_только_после_отправки`() = runTest(main.dispatcher) {
        val history = FakeHistory()
        val vm = viewModel(history = history)

        vm.onQueryChange("  Maps  ")
        advanceUntilIdle()
        assertTrue(history.pushedQueries.isEmpty())

        vm.onQuerySubmitted(vm.state.value.query)
        advanceUntilIdle()

        assertEquals(listOf("Maps"), history.pushedQueries)
    }

    @Test
    fun `пустой запрос не попадает в историю`() = runTest(main.dispatcher) {
        val history = FakeHistory()
        val vm = viewModel(history = history)

        vm.onQuerySubmitted("   ")
        advanceUntilIdle()

        assertTrue(history.pushedQueries.isEmpty())
    }

    @Test
    fun `звонок пишется в журнал, контакт без номера - нет`() = runTest(main.dispatcher) {
        val history = FakeHistory()
        val vm = viewModel(history = history)

        vm.onCommunication(CommunicationKind.CALL, contact(1, "Ann"))
        vm.onCommunication(CommunicationKind.SMS, contact(2, "Bob", number = null))
        advanceUntilIdle()

        val recorded = history.pushedCommunications.single()
        assertTrue(history.pushedQueries.isEmpty())
        assertEquals(CommunicationKind.CALL, recorded.kind)
        assertEquals("+70000000001", recorded.number)
    }

    @Test
    fun `удаление из истории убирает звонок и смс одного контакта`() = runTest(main.dispatcher) {
        val history = FakeHistory(
            communications = listOf(
                log(1, "Ann", CommunicationKind.CALL),
                log(1, "Ann", CommunicationKind.SMS),
            )
        )
        val vm = viewModel(history = history)
        advanceUntilIdle()
        assertTrue(vm.inJournal(1))

        vm.onRemoveFromJournal(1)
        advanceUntilIdle()

        assertFalse(vm.inJournal(1))
        assertEquals(listOf(1L), history.journalRemoved)
    }

    @Test
    fun `без права на контакты списка нет, после разрешения появляется`() = runTest(main.dispatcher) {
        val contacts = FakeContacts(granted = false, known = listOf(contact(1, "Ann")))
        val vm = viewModel(contacts = contacts)
        advanceUntilIdle()

        assertFalse(vm.state.value.contactsGranted)
        assertTrue(vm.state.value.contacts.isEmpty())

        contacts.granted = true
        vm.onContactsPermissionResult(true)
        advanceUntilIdle()

        assertTrue(vm.state.value.contactsGranted)
        assertEquals(listOf("Ann"), vm.state.value.contacts.map { it.entry.name })
    }

    @Test
    fun `возврат после отзыва права очищает контакты меню и отложенный поиск`() = runTest(main.dispatcher) {
        val entry = contact(1, "Ann")
        val contacts = FakeContacts(known = listOf(entry))
        val vm = viewModel(contacts = contacts)
        advanceUntilIdle()
        vm.onMenuOpen(entry)
        vm.onQueryChange("ann")

        contacts.granted = false
        vm.onScreenResumed()

        assertFalse(vm.state.value.contactsGranted)
        assertTrue(vm.state.value.contacts.isEmpty())
        assertNull(vm.menuEntry.value)
        advanceUntilIdle()
        assertFalse("ann" in contacts.searched)
        assertTrue(vm.state.value.contacts.isEmpty())
    }

    @Test
    fun `возврат после разрешения в настройках загружает контакты`() = runTest(main.dispatcher) {
        val contacts = FakeContacts(granted = false, known = listOf(contact(1, "Ann")))
        val vm = viewModel(contacts = contacts)
        advanceUntilIdle()

        contacts.granted = true
        vm.onScreenResumed()
        advanceUntilIdle()

        assertTrue(vm.state.value.contactsGranted)
        assertEquals(listOf("Ann"), vm.state.value.contacts.map { it.entry.name })
    }

    @Test
    fun `меню дозагружает почту контакта из журнала`() = runTest(main.dispatcher) {
        val contacts = FakeContacts(
            known = listOf(contact(1, "Ann")),
            fullDetails = mapOf(1L to contact(1, "Ann", email = "ann@example.com").copy(phoneCount = 2)),
        )
        val vm = viewModel(contacts = contacts)
        advanceUntilIdle()

        vm.onMenuOpen(vm.state.value.contacts.first().entry)
        advanceUntilIdle()

        assertEquals("ann@example.com", vm.menuEntry.value?.email)
    }

    @Test
    fun `меню остаётся с исходной строкой если контакт пропал`() = runTest(main.dispatcher) {
        val vm = viewModel(contacts = FakeContacts(known = listOf(contact(1, "Ann"))))
        advanceUntilIdle()

        vm.onMenuOpen(vm.state.value.contacts.first().entry)
        advanceUntilIdle()

        assertEquals(1L, vm.menuEntry.value?.contactId)
        assertNull(vm.menuEntry.value?.email)
    }

    @Test
    fun `тап по звезде в меню приложения переключает избранное`() = runTest(main.dispatcher) {
        val favorites = FakeFavorites()
        val vm = viewModel(favorites = favorites)
        advanceUntilIdle()

        vm.onToggleFavoriteApp(app("com.a", "A"))
        advanceUntilIdle()
        assertEquals(listOf("com.a"), vm.state.value.favApps)
        assertEquals(listOf("com.a"), favorites.apps.first())

        vm.onToggleFavoriteApp(app("com.a", "A"))
        advanceUntilIdle()
        assertTrue(vm.state.value.favApps.isEmpty())
    }

    @Test
    fun `тап по звезде в меню контакта переключает избранное`() = runTest(main.dispatcher) {
        val favorites = FakeFavorites()
        val vm = viewModel(favorites = favorites)

        vm.onToggleFavoriteContact(contact(1, "Ann"))
        advanceUntilIdle()
        assertEquals(listOf("lookup-1"), vm.state.value.favContactKeys)

        vm.onToggleFavoriteContact(contact(1, "Ann"))
        advanceUntilIdle()
        assertTrue(vm.state.value.favContactKeys.isEmpty())
    }

    @Test
    fun `избранное приложение сразу пересчитывается и повторно ищется`() = runTest(main.dispatcher) {
        val apps = FakeApps(known = listOf(app("com.a", "Maps"), app("com.b", "appmaps")))
        val vm = viewModel(apps = apps)
        advanceUntilIdle()
        vm.onQueryChange("map")
        advanceUntilIdle()
        assertEquals(listOf("com.a", "com.b"), vm.state.value.apps.map { it.packageName })
        apps.matched.clear()

        vm.onToggleFavoriteApp(app("com.b", "appmaps"))

        assertEquals(listOf("com.b", "com.a"), vm.state.value.apps.map { it.packageName })
        advanceUntilIdle()
        assertEquals(listOf("map"), apps.matched)
    }

    @Test
    fun `удаление приложения из избранного сразу убирает его из непустого запроса`() = runTest(main.dispatcher) {
        val apps = FakeApps(known = listOf(app("com.a", "Maps"), app("com.b", "appmaps")))
        val vm = viewModel(apps = apps, favorites = FakeFavorites(apps = listOf("com.b")))
        advanceUntilIdle()
        vm.onToggleFavoritesMode()
        vm.onQueryChange("map")
        advanceUntilIdle()
        assertEquals(listOf("com.b"), vm.state.value.apps.map { it.packageName })
        apps.matched.clear()

        vm.onToggleFavoriteApp(app("com.b", "appmaps"))

        assertTrue(vm.state.value.apps.isEmpty())
        advanceUntilIdle()
        assertTrue(vm.state.value.apps.isEmpty())
        assertEquals(listOf("map"), apps.matched)
    }

    @Test
    fun `избранный контакт сразу пересчитывается и повторно ищется`() = runTest(main.dispatcher) {
        val contacts = FakeContacts(known = listOf(contact(1, "Ann"), contact(2, "Anna")))
        val vm = viewModel(contacts = contacts)
        advanceUntilIdle()
        vm.onQueryChange("ann")
        advanceUntilIdle()
        assertEquals(listOf("Ann", "Anna"), vm.state.value.contacts.map { it.entry.name })
        contacts.searched.clear()

        vm.onToggleFavoriteContact(contact(2, "Anna"))

        assertEquals(listOf("Anna", "Ann"), vm.state.value.contacts.map { it.entry.name })
        advanceUntilIdle()
        assertEquals(listOf("ann"), contacts.searched)
    }

    @Test
    fun `удаление контакта из избранного сразу убирает его из непустого запроса`() = runTest(main.dispatcher) {
        val contacts = FakeContacts(known = listOf(contact(1, "Ann"), contact(2, "Anna")))
        val vm = viewModel(contacts = contacts, favorites = FakeFavorites(contacts = listOf("lookup-2")))
        advanceUntilIdle()
        vm.onToggleFavoritesMode()
        vm.onQueryChange("ann")
        advanceUntilIdle()
        assertEquals(listOf("Anna"), vm.state.value.contacts.map { it.entry.name })
        contacts.searched.clear()

        vm.onToggleFavoriteContact(contact(2, "Anna"))

        assertTrue(vm.state.value.contacts.isEmpty())
        advanceUntilIdle()
        assertTrue(vm.state.value.contacts.isEmpty())
        assertEquals(listOf("ann"), contacts.searched)
    }

    @Test
    fun `возврат на экран сбрасывает кэш приложений`() = runTest(main.dispatcher) {
        val apps = FakeApps(known = listOf(app("com.a", "A")))
        val vm = viewModel(apps = apps, history = FakeHistory(recentApps = listOf("com.a")))
        advanceUntilIdle()

        vm.onScreenResumed()
        advanceUntilIdle()

        assertEquals(1, apps.invalidations)
        assertEquals(listOf("com.a"), vm.state.value.apps.map { it.packageName })
    }

    @Test
    fun `меню приложения открывается и закрывается`() = runTest(main.dispatcher) {
        val vm = viewModel(apps = FakeApps(known = listOf(app("com.a", "A"))))
        advanceUntilIdle()

        vm.onAppMenuOpen(app("com.a", "A"))
        assertEquals("com.a", vm.menuApp.value?.packageName)

        vm.onAppMenuClose()
        assertNull(vm.menuApp.value)
    }
}
