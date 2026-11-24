package dev.onebar.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.onebar.app.R
import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.testing.FakeApps
import dev.onebar.app.testing.FakeContacts
import dev.onebar.app.testing.FakeFavorites
import dev.onebar.app.testing.FakeHistory
import dev.onebar.app.testing.FakeSuggestions
import dev.onebar.app.testing.app
import dev.onebar.app.testing.contact
import dev.onebar.app.testing.log
import dev.onebar.app.ui.theme.OneBarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Экран целиком на фейковых источниках: данные задаёт тест, а не телефон, поэтому проверки
 * не зависят от записной книжки и установленных приложений. Строки интерфейса берутся из
 * ресурсов — тест одинаково проходит в русской и английской локалях.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val ui = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int): String = context.getString(id)

    private fun swipeHorizontally(fraction: Float) {
        val node = ui.onNodeWithTag(NAVIGATION_SWIPE_TAG)
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val start = Offset(bounds.center.x, bounds.center.y)
        val end = Offset(bounds.center.x + bounds.width * fraction, bounds.center.y)
        node.performTouchInput { swipe(start, end, durationMillis = 300) }
        ui.waitForIdle()
    }

    private fun swipeRow(rowKey: String, startFraction: Float, endFraction: Float) {
        val node = ui.onNodeWithTag("swipe-row-$rowKey")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        node.performTouchInput {
            swipe(
                start = Offset(bounds.width * startFraction, bounds.height / 2f),
                end = Offset(bounds.width * endFraction, bounds.height / 2f),
                durationMillis = 300,
            )
        }
        ui.waitForIdle()
    }

    private fun actionWidth(tag: String): Float =
        ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.width

    private fun actionHeight(tag: String): Float =
        ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height

    private fun show(
        apps: FakeApps = FakeApps(),
        contacts: FakeContacts = FakeContacts(),
        suggestions: FakeSuggestions = FakeSuggestions(),
        history: FakeHistory = FakeHistory(),
        favorites: FakeFavorites = FakeFavorites(),
        onOpenSettings: () -> Unit = {},
    ) {
        val viewModel = SearchViewModel(apps, contacts, suggestions, history, favorites)
        ui.setContent { SearchScreen(viewModel = viewModel, onOpenSettings = onOpenSettings) }
    }

    @Test
    fun `плитки_приложений_и_строки_контактов_приходят_из_источников`() {
        show(
            apps = FakeApps(known = listOf(app("com.a", "Maps"), app("com.b", "appmaps"))),
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            history = FakeHistory(recentApps = listOf("com.a", "com.b")),
        )

        ui.waitUntilAtLeastOneExists(hasText("Maps"), TIMEOUT)
        ui.onNodeWithText("appmaps").assertExists()
        ui.onNodeWithText(str(R.string.contacts_title)).assertExists()
        ui.onNodeWithText("Ann").assertExists()
    }

    @Test
    fun `шестеренка_открывает_настройки`() {
        var opened = false
        show(onOpenSettings = { opened = true })

        ui.onNodeWithContentDescription(str(R.string.settings_title)).performClick()

        assertTrue(opened)
    }

    @Test
    fun `свайп_менее_30_процентов_откатывается`() {
        var opened = false
        show(onOpenSettings = { opened = true })

        swipeHorizontally(0.2f)

        assertFalse(opened)
        ui.onNodeWithText(str(R.string.search_hint)).assertIsDisplayed()
    }

    @Test
    fun `свайп_более_30_процентов_открывает_настройки`() {
        var opened = false
        show(onOpenSettings = { opened = true })

        swipeHorizontally(0.4f)

        assertTrue(opened)
    }

    @Test
    fun `свайп_в_избранном_открывает_настройки`() {
        var opened = false
        show(
            apps = FakeApps(known = listOf(app("com.a", "Maps"))),
            history = FakeHistory(recentApps = listOf("com.a")),
            favorites = FakeFavorites(apps = listOf("com.a")),
            onOpenSettings = { opened = true },
        )
        ui.waitUntilAtLeastOneExists(hasText("Maps"), TIMEOUT)
        ui.onNodeWithContentDescription(str(R.string.favorites)).performClick()

        swipeHorizontally(0.4f)

        assertTrue(opened)
    }

    @Test
    fun `двухрядная_сетка_показывает_звезду_избранного_приложения`() {
        val apps = (1..8).map { index -> app("com.$index", if (index == 1) "Maps" else "App $index") }
        ui.setContent {
            OneBarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppsGridSection(
                        apps = apps,
                        columns = 4,
                        rows = 2,
                        favoritePackages = setOf("com.1"),
                        onOpen = {},
                        onMenu = {},
                    )
                }
            }
        }

        ui.waitUntil(TIMEOUT) {
            ui.onAllNodesWithTag("app-favorite-com.1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("app-favorite-com.1", useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithTag("app-favorite-com.2", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `реальный_экран_после_скрытия_клавиатуры_показывает_двухрядную_сетку`() {
        val apps = (1..8).map { index -> app("com.$index", if (index == 1) "Maps" else "App $index") }
        show(
            apps = FakeApps(known = apps),
            history = FakeHistory(recentApps = apps.map { it.packageName }),
            favorites = FakeFavorites(apps = listOf("com.1")),
        )

        ui.waitUntilAtLeastOneExists(hasText("Maps"), TIMEOUT)
        ui.onNode(hasSetTextAction()).performImeAction()
        ui.waitUntilAtLeastOneExists(hasTestTag("apps-grid"), TIMEOUT)

        ui.onNodeWithText("App 5").assertIsDisplayed()
        ui.onNodeWithTag("app-favorite-com.1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `тап_по_звезде_оставляет_только_избранные_приложения`() {
        show(
            apps = FakeApps(known = listOf(app("com.a", "Maps"), app("com.b", "appmaps"))),
            history = FakeHistory(recentApps = listOf("com.a", "com.b")),
            favorites = FakeFavorites(apps = listOf("com.b")),
        )
        ui.waitUntilAtLeastOneExists(hasText("Maps"), TIMEOUT)

        ui.onNodeWithContentDescription(str(R.string.favorites)).performClick()

        ui.waitUntilAtLeastOneExists(hasText("appmaps"), TIMEOUT)
        ui.onNodeWithText("Maps").assertDoesNotExist()
    }

    @Test
    fun `вход_в_пустое_избранное_показывает_подсказку_и_крестик_её_закрывает`() {
        show(
            apps = FakeApps(known = listOf(app("com.a", "Maps"))),
            history = FakeHistory(recentApps = listOf("com.a")),
        )
        ui.waitUntilAtLeastOneExists(hasText("Maps"), TIMEOUT)

        ui.onNodeWithContentDescription(str(R.string.favorites)).performClick()

        ui.onNodeWithTag(TAG_FAVORITES_HINT_CLOSE).performClick()
        ui.waitUntil(TIMEOUT) { ui.onAllNodesWithTag(TAG_FAVORITES_HINT_CLOSE).fetchSemanticsNodes().isEmpty() }

        // Пустое избранное экран не меняет: за подсказкой — тот же список недавних приложений.
        ui.onNodeWithText("Maps").assertIsDisplayed()
    }

    @Test
    fun `в_избранном_веб_подсказки_пропадают`() {
        show(
            apps = FakeApps(known = listOf(app("com.a", "Maps"))),
            suggestions = FakeSuggestions(replies = mapOf("map" to listOf("maps в яндексе"))),
            favorites = FakeFavorites(apps = listOf("com.a")),
        )
        ui.onNode(hasSetTextAction()).performTextInput("map")
        ui.waitUntilAtLeastOneExists(hasText("maps в яндексе"), TIMEOUT)

        ui.onNodeWithContentDescription(str(R.string.favorites)).performClick()

        ui.waitUntil(TIMEOUT) { ui.onAllNodesWithText("maps в яндексе").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("Maps").assertIsDisplayed()
    }

    @Test
    fun `долгое_нажатие_на_контакт_открывает_меню_с_пунктами`() {
        show(contacts = FakeContacts(known = listOf(contact(1, "Ann"))))
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)

        ui.onNodeWithText("Ann").performTouchInput { longClick() }

        ui.waitUntilAtLeastOneExists(hasText(str(R.string.menu_copy)), TIMEOUT)
        ui.onNodeWithText(str(R.string.menu_edit)).assertExists()
        ui.onNodeWithText(str(R.string.menu_fav_add)).assertExists()
    }

    @Test
    fun `долгое_нажатие_на_приложение_открывает_его_меню`() {
        show(
            apps = FakeApps(known = listOf(app("com.a", "Maps"))),
            history = FakeHistory(recentApps = listOf("com.a")),
        )
        ui.waitUntilAtLeastOneExists(hasText("Maps"), TIMEOUT)

        ui.onNodeWithText("Maps").performTouchInput { longClick() }

        ui.waitUntilAtLeastOneExists(hasText(str(R.string.app_menu_info)), TIMEOUT)
        ui.onNodeWithText(str(R.string.app_menu_open)).assertExists()
    }

    @Test
    fun `тап_по_избранному_в_меню_закрывает_меню_и_в_избранном`() {
        show(
            apps = FakeApps(known = listOf(app("com.a", "Maps"), app("com.b", "Notes"))),
            history = FakeHistory(recentApps = listOf("com.a", "com.b")),
            favorites = FakeFavorites(apps = listOf("com.a", "com.b")),
        )
        ui.onNodeWithContentDescription(str(R.string.favorites)).performClick()
        ui.waitUntilAtLeastOneExists(hasText("Maps"), TIMEOUT)

        ui.onNodeWithText("Maps").performTouchInput { longClick() }
        ui.waitUntilAtLeastOneExists(hasText(str(R.string.menu_fav_remove)), TIMEOUT)
        ui.onNodeWithText(str(R.string.menu_fav_remove)).performClick()

        // Меню закрыто, а отмену видно списком: плитка пропала, вторая осталась.
        ui.waitUntil(TIMEOUT) { ui.onAllNodesWithText(str(R.string.menu_fav_remove)).fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("Maps").assertDoesNotExist()
        ui.onNodeWithText("Notes").assertIsDisplayed()
    }

    @Test
    fun `свайп_на_треть_ширины_показывает_подписи_и_не_вызывает_действие`() {
        val history = FakeHistory()
        show(contacts = FakeContacts(known = listOf(contact(1, "Ann"))), history = history)
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)

        ui.onNodeWithText("Ann").performTouchInput {
            val middle = height / 2f
            swipe(
                start = Offset(width * 0.95f, middle),
                end = Offset(width * 0.55f, middle),
            )
        }

        ui.waitUntilAtLeastOneExists(hasText(str(R.string.caption_call)), TIMEOUT)
        assertTrue(history.pushedCommunications.isEmpty())
    }

    @Test
    fun `все_четыре_действия_свайпа_расширяются_одинаково`() {
        show(
            contacts = FakeContacts(known = listOf(contact(1, "Map"))),
            suggestions = FakeSuggestions(replies = mapOf("map" to listOf("maps"))),
        )
        ui.onNode(hasSetTextAction()).performTextInput("map")
        ui.waitUntilAtLeastOneExists(hasText("Map"), TIMEOUT)
        ui.waitUntilAtLeastOneExists(hasText("maps"), TIMEOUT)
        ui.onNode(hasSetTextAction()).performImeAction()
        ui.waitForIdle()

        val collapsedHeight = actionHeight("swipe-action-sms-contact:1")
        assertEquals(collapsedHeight, actionHeight("swipe-action-call-contact:1"), 1f)
        assertEquals(collapsedHeight, actionHeight("swipe-action-yandex-web:maps"), 1f)
        assertEquals(collapsedHeight, actionHeight("swipe-action-google-web:maps"), 1f)

        swipeRow("contact:1", 0.95f, 0.55f)
        val expected = ui.onNodeWithTag("swipe-row-contact:1")
            .fetchSemanticsNode().boundsInRoot.width * 0.25f
        ui.waitUntil(TIMEOUT) { abs(actionWidth("swipe-action-sms-contact:1") - expected) < 1f }
        val sms = actionWidth("swipe-action-sms-contact:1")
        val call = actionWidth("swipe-action-call-contact:1")
        assertEquals(expected, sms, 1f)
        assertEquals(sms, call, 1f)
        ui.onNodeWithText(str(R.string.caption_sms)).assertHeightIsAtLeast(18.dp)
        ui.onNodeWithText(str(R.string.caption_call)).assertHeightIsAtLeast(18.dp)
        val expandedHeight = actionHeight("swipe-action-sms-contact:1")
        assertEquals(expandedHeight, actionHeight("swipe-action-call-contact:1"), 1f)

        swipeRow("web:maps", 0.95f, 0.55f)
        ui.waitUntil(TIMEOUT) { abs(actionWidth("swipe-action-yandex-web:maps") - expected) < 1f }
        val yandex = actionWidth("swipe-action-yandex-web:maps")
        val google = actionWidth("swipe-action-google-web:maps")
        assertEquals(sms, yandex, 1f)
        assertEquals(yandex, google, 1f)
        assertEquals(expandedHeight, actionHeight("swipe-action-yandex-web:maps"), 1f)
        assertEquals(expandedHeight, actionHeight("swipe-action-google-web:maps"), 1f)
        ui.onNodeWithText("Яндекс").assertHeightIsAtLeast(18.dp)
        ui.onNodeWithText("Google").assertHeightIsAtLeast(18.dp)
    }

    @Test
    fun `свайп_по_другой_строке_закрывает_предыдущую`() {
        show(
            contacts = FakeContacts(
                known = listOf(contact(1, "Ann"), contact(2, "Bob")),
            ),
        )
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)
        ui.waitUntilAtLeastOneExists(hasText("Bob"), TIMEOUT)
        val collapsed = actionWidth("swipe-action-sms-contact:1")

        swipeRow("contact:1", 0.95f, 0.55f)
        ui.waitUntil(TIMEOUT) { actionWidth("swipe-action-sms-contact:1") > collapsed }
        swipeRow("contact:2", 0.95f, 0.55f)

        ui.waitUntil(TIMEOUT) {
            abs(actionWidth("swipe-action-sms-contact:1") - collapsed) < 1f &&
                actionWidth("swipe-action-sms-contact:2") > collapsed &&
                ui.onAllNodesWithText(str(R.string.caption_call)).fetchSemanticsNodes().size == 1
        }
        assertEquals(collapsed, actionWidth("swipe-action-sms-contact:1"), 1f)
    }

    @Test
    fun `обратный_свайп_на_30_процентов_закрывает_строку_без_настроек`() {
        var opened = false
        show(
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            onOpenSettings = { opened = true },
        )
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)
        val collapsed = actionWidth("swipe-action-sms-contact:1")
        swipeRow("contact:1", 0.95f, 0.55f)
        ui.waitUntil(TIMEOUT) { actionWidth("swipe-action-sms-contact:1") > collapsed }

        swipeRow("contact:1", 0.05f, 0.40f)

        ui.waitUntil(TIMEOUT) { abs(actionWidth("swipe-action-sms-contact:1") - collapsed) < 1f }
        assertFalse(opened)
        ui.onNodeWithText(str(R.string.caption_call)).assertDoesNotExist()
    }

    @Test
    fun `обратный_свайп_менее_30_процентов_возвращает_раскрытый_вид`() {
        var opened = false
        show(
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            onOpenSettings = { opened = true },
        )
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)
        val collapsed = actionWidth("swipe-action-sms-contact:1")
        swipeRow("contact:1", 0.95f, 0.55f)
        ui.waitUntil(TIMEOUT) { actionWidth("swipe-action-sms-contact:1") > collapsed }
        val expanded = actionWidth("swipe-action-sms-contact:1")

        swipeRow("contact:1", 0.05f, 0.25f)

        ui.waitUntil(TIMEOUT) { abs(actionWidth("swipe-action-sms-contact:1") - expanded) < 1f }
        assertFalse(opened)
        ui.onNodeWithText(str(R.string.caption_call)).assertIsDisplayed()
    }

    @Test
    fun `свайп_вправо_по_свернутой_строке_открывает_настройки`() {
        var opened = false
        show(
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            onOpenSettings = { opened = true },
        )
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)

        swipeRow("contact:1", 0.05f, 0.45f)

        assertTrue(opened)
    }

    @Test
    fun `свайп_за_50_процентов_по_прежнему_вызывает_звонок`() {
        val history = FakeHistory()
        show(
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            history = history,
        )
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)

        swipeRow("contact:1", 0.95f, 0.35f)

        ui.waitUntil(TIMEOUT) { history.pushedCommunications.isNotEmpty() }
        assertEquals(CommunicationKind.CALL, history.pushedCommunications.single().kind)
    }

    @Test
    fun `тап_по_иконке_звонка_пишет_звонок_в_журнал`() {
        val history = FakeHistory()
        show(contacts = FakeContacts(known = listOf(contact(1, "Ann"))), history = history)

        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)
        ui.onNodeWithContentDescription(str(R.string.call_action))
            .performSemanticsAction(SemanticsActions.OnClick)

        ui.waitUntil(TIMEOUT) { history.pushedCommunications.isNotEmpty() }
        assertEquals(CommunicationKind.CALL, history.pushedCommunications.single().kind)
    }

    @Test
    fun `запрос_без_совпадений_показывает_ничего_не_найдено`() {
        show(
            apps = FakeApps(known = listOf(app("com.a", "Maps"))),
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
        )

        ui.onNode(hasSetTextAction()).performTextInput("zzz")

        ui.waitUntilAtLeastOneExists(hasText(str(R.string.nothing_found)), TIMEOUT)
    }

    @Test
    fun `подсказки_приходят_по_набранному_запросу_и_выбираются_тапом`() {
        val suggestions = FakeSuggestions(replies = mapOf("коф" to listOf("кофе в зёрнах")))
        show(suggestions = suggestions)

        ui.onNode(hasSetTextAction()).performTextInput("коф")

        ui.waitUntilAtLeastOneExists(hasText("кофе в зёрнах"), TIMEOUT)
        val suggestion = ui.onNodeWithText("кофе в зёрнах")
        suggestion.assertHasClickAction()
        assertEquals(listOf("коф"), suggestions.asked)

        suggestion.performSemanticsAction(SemanticsActions.OnClick)

        val field = ui.onNode(hasSetTextAction())
        field.assertTextEquals("кофе в зёрнах")
        field.assertIsFocused()
        assertEquals(
            TextRange("кофе в зёрнах".length),
            field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
        )
    }

    @Test
    fun `выбор_совпадающей_подсказки_переводит_курсор_в_конец`() {
        val text = "кофе"
        show(suggestions = FakeSuggestions(replies = mapOf(text to listOf(text))))
        val field = ui.onNode(hasSetTextAction())

        field.performTextInput(text)
        ui.waitUntilAtLeastOneExists(hasText(text) and !hasSetTextAction(), TIMEOUT)
        field.performSemanticsAction(SemanticsActions.SetSelection) { setSelection ->
            setSelection(0, 0, false)
        }
        assertEquals(
            TextRange.Zero,
            field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
        )

        ui.onNode(hasText(text) and !hasSetTextAction()).performSemanticsAction(SemanticsActions.OnClick)

        field.assertIsFocused()
        assertEquals(
            TextRange(text.length),
            field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
        )
    }

    @Test
    fun `крестик_очищает_запрос_и_оставляет_фокус_в_поле`() {
        show()
        val field = ui.onNode(hasSetTextAction())

        field.performTextInput("кофе")
        ui.onNodeWithContentDescription(str(R.string.clear)).performClick()

        field.assertIsFocused()
        assertEquals(
            "",
            field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
        )
        assertEquals(
            TextRange.Zero,
            field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
        )
    }

    @Test
    fun `крестик_не_появляется_для_одних_пробелов`() {
        show()
        val field = ui.onNode(hasSetTextAction())
        val clear = ui.onNodeWithContentDescription(str(R.string.clear))

        clear.assertDoesNotExist()
        field.performTextInput("   ")
        field.assertTextEquals("   ")
        clear.assertDoesNotExist()

        field.performTextInput("кофе")
        clear.assertExists()

        clear.performClick()
        clear.assertDoesNotExist()
    }

    @Test
    fun `пробелы_сохраняются_при_каждом_вводе`() {
        show()
        val field = ui.onNode(hasSetTextAction())

        field.performTextInput(" ")
        field.assertTextEquals(" ")
        assertEquals(
            TextRange(1),
            field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
        )

        field.performTextInput("кофе ")
        field.assertTextEquals(" кофе ")
        assertEquals(
            TextRange(" кофе ".length),
            field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
        )

        field.performTextInput(" ")
        field.assertTextEquals(" кофе  ")
        assertEquals(
            TextRange(" кофе  ".length),
            field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
        )
    }

    @Test
    fun `кнопка_поиска_скрывает_клавиатуру_и_сохраняет_отправленный_запрос`() {
        val history = FakeHistory()
        show(history = history)
        val field = ui.onNode(hasSetTextAction())

        field.performTextInput("  кофе  ")
        assertTrue(history.pushedQueries.isEmpty())

        field.performImeAction()

        field.assertIsNotFocused()
        ui.waitUntil(TIMEOUT) { history.pushedQueries.isNotEmpty() }
        assertEquals(listOf("кофе"), history.pushedQueries)
    }

    @Test
    fun `отправленный_запрос_потом_показывается_в_истории`() {
        val history = FakeHistory()
        show(history = history)
        val field = ui.onNode(hasSetTextAction())

        field.performTextInput("кофе")
        field.performImeAction()
        ui.waitUntil(TIMEOUT) { history.pushedQueries.isNotEmpty() }

        ui.onNodeWithContentDescription(str(R.string.clear)).performClick()
        field.performClick()

        ui.waitUntilAtLeastOneExists(hasText("кофе"), TIMEOUT)
    }

    @Test
    fun `тап_по_пустому_полю_показывает_историю_запросов`() {
        show(history = FakeHistory(queries = listOf("кофе", "пицца")))

        ui.onNode(hasSetTextAction()).performClick()

        ui.waitUntilAtLeastOneExists(hasText("кофе"), TIMEOUT)
        ui.onNodeWithText("пицца").assertExists()
    }

    @Test
    fun `без_права_на_контакты_показывается_баннер_с_кнопкой_разрешить`() {
        show(contacts = FakeContacts(granted = false, known = listOf(contact(1, "Ann"))))

        ui.onNodeWithText(str(R.string.contacts_denied)).assertExists()
        ui.onNodeWithText(str(R.string.allow)).assertExists()
        ui.onNodeWithText("Ann").assertDoesNotExist()
    }

    @Test
    fun `строка_журнала_показывает_действие_и_номер`() {
        show(
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            history = FakeHistory(communications = listOf(log(1, "Ann", CommunicationKind.CALL))),
        )

        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)
        ui.onNodeWithText("+70000000001", substring = true).assertExists()
        ui.onNodeWithText(str(R.string.caption_call), substring = true).assertExists()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
