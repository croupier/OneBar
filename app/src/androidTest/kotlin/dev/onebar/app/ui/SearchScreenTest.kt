package dev.onebar.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.semantics.SemanticsActions
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    private fun show(
        apps: FakeApps = FakeApps(),
        contacts: FakeContacts = FakeContacts(),
        suggestions: FakeSuggestions = FakeSuggestions(),
        history: FakeHistory = FakeHistory(),
        favorites: FakeFavorites = FakeFavorites(),
    ) {
        val viewModel = SearchViewModel(apps, contacts, suggestions, history, favorites)
        ui.setContent { SearchScreen(onClose = {}, viewModel = viewModel) }
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

        ui.onNode(hasSetTextAction()).assertTextEquals("кофе в зёрнах")
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
