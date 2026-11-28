package dev.onebar.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.onebar.app.R
import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.data.KeyboardStartup
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
        val node = ui.onRoot()
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

    private fun actionWidth(tag: String, unmerged: Boolean = false): Float {
        val node = if (unmerged) ui.onNodeWithTag(tag, useUnmergedTree = true) else ui.onNodeWithTag(tag)
        return node.fetchSemanticsNode().boundsInRoot.width
    }

    private fun actionHeight(tag: String, unmerged: Boolean = false): Float {
        val node = if (unmerged) ui.onNodeWithTag(tag, useUnmergedTree = true) else ui.onNodeWithTag(tag)
        return node.fetchSemanticsNode().boundsInRoot.height
    }

    private fun nodeBounds(tag: String, useUnmergedTree: Boolean = false): Rect =
        ui.onNodeWithTag(tag, useUnmergedTree = useUnmergedTree).fetchSemanticsNode().boundsInRoot

    private fun show(
        apps: FakeApps = FakeApps(),
        contacts: FakeContacts = FakeContacts(),
        suggestions: FakeSuggestions = FakeSuggestions(),
        history: FakeHistory = FakeHistory(),
        favorites: FakeFavorites = FakeFavorites(),
        keyboardStartup: KeyboardStartup = KeyboardStartup.IMMEDIATE,
        settingsReturnCount: MutableState<Int>? = null,
        onOpenSettings: () -> Unit = {},
        onExit: () -> Unit = {},
        onWebSearch: (WebEngine, String) -> Unit = { _, _ -> },
    ): SearchViewModel {
        val viewModel = SearchViewModel(apps, contacts, suggestions, history, favorites)
        ui.setContent {
            SearchScreen(
                viewModel = viewModel,
                keyboardStartup = keyboardStartup,
                settingsReturnCount = settingsReturnCount?.value ?: 0,
                onOpenSettings = onOpenSettings,
                onExit = onExit,
                onWebSearch = onWebSearch,
                onCallEntry = {},
            )
        }
        return viewModel
    }

    @Test
    fun `режим_показа_сразу_передаёт_фокус_полю_при_старте`() {
        show()

        ui.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `режим_фокуса_не_захватывает_поле_до_тапа`() {
        show(keyboardStartup = KeyboardStartup.ON_FOCUS)
        val field = ui.onNode(hasSetTextAction())

        field.assertIsNotFocused()
        field.performClick()
        field.assertIsFocused()
    }

    @Test
    fun `возврат_из_настроек_в_режиме_сразу_возвращает_фокус`() {
        val settingsReturns = mutableStateOf(0)
        show(settingsReturnCount = settingsReturns)
        val field = ui.onNode(hasSetTextAction())

        field.performImeAction()
        field.assertIsNotFocused()
        ui.runOnUiThread { settingsReturns.value++ }

        field.assertIsFocused()
    }

    @Test
    fun `возврат_из_настроек_в_режиме_фокуса_не_захватывает_поле`() {
        val settingsReturns = mutableStateOf(0)
        show(keyboardStartup = KeyboardStartup.ON_FOCUS, settingsReturnCount = settingsReturns)
        val field = ui.onNode(hasSetTextAction())

        ui.runOnUiThread { settingsReturns.value++ }

        field.assertIsNotFocused()
    }

    @Test
    fun `выход_из_избранного_в_режиме_сразу_возвращает_фокус`() {
        show(
            apps = FakeApps(known = listOf(app("com.a", "Maps"))),
            history = FakeHistory(recentApps = listOf("com.a")),
            favorites = FakeFavorites(apps = listOf("com.a")),
        )
        ui.waitUntilAtLeastOneExists(hasText("Maps"), TIMEOUT)
        val field = ui.onNode(hasSetTextAction())
        val favoritesToggle = ui.onNodeWithContentDescription(str(R.string.favorites))

        favoritesToggle.performClick()
        field.assertIsNotFocused()
        favoritesToggle.performClick()

        field.assertIsFocused()
    }

    @Test
    fun `выход_из_избранного_в_режиме_фокуса_оставляет_поле_без_фокуса`() {
        show(
            apps = FakeApps(known = listOf(app("com.a", "Maps"))),
            history = FakeHistory(recentApps = listOf("com.a")),
            favorites = FakeFavorites(apps = listOf("com.a")),
            keyboardStartup = KeyboardStartup.ON_FOCUS,
        )
        ui.waitUntilAtLeastOneExists(hasText("Maps"), TIMEOUT)
        val field = ui.onNode(hasSetTextAction())
        val favoritesToggle = ui.onNodeWithContentDescription(str(R.string.favorites))

        favoritesToggle.performClick()
        favoritesToggle.performClick()

        field.assertIsNotFocused()
        field.performClick()
        field.assertIsFocused()
    }

    @Test
    fun `назад_из_избранного_возвращает_на_главный_экран`() {
        var exited = false
        show(
            apps = FakeApps(known = listOf(app("com.a", "Maps"))),
            history = FakeHistory(recentApps = listOf("com.a")),
            favorites = FakeFavorites(apps = listOf("com.a")),
            onExit = { exited = true },
        )
        ui.waitUntilAtLeastOneExists(hasText("Maps"), TIMEOUT)
        val field = ui.onNode(hasSetTextAction())

        ui.onNodeWithContentDescription(str(R.string.favorites)).performClick()
        pressBack()

        assertFalse(exited)
        field.assertIsFocused()
    }

    @Test
    fun `два_назад_подряд_завершают_приложение`() {
        var exitCount = 0
        show(onExit = { exitCount++ })
        val field = ui.onNode(hasSetTextAction())
        field.performImeAction()

        pressBack()
        ui.waitUntilAtLeastOneExists(hasText(str(R.string.double_back_exit)), TIMEOUT)
        assertEquals(0, exitCount)

        pressBack()

        assertEquals(1, exitCount)
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
        ui.onNodeWithText(str(R.string.apps_title)).assertExists()
        assertEquals(1, ui.onAllNodesWithText(str(R.string.contacts_title)).fetchSemanticsNodes().size)
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
    fun `свайп_по_главному_экрану_не_открывает_настройки`() {
        var opened = false
        show(onOpenSettings = { opened = true })

        swipeHorizontally(0.4f)

        assertFalse(opened)
        ui.onNodeWithText(str(R.string.search_hint)).assertIsDisplayed()
    }

    @Test
    fun `свайп_в_избранном_не_открывает_настройки`() {
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

        assertFalse(opened)
    }

    @Test
    fun `двухрядная_сетка_показывает_звезду_избранного_приложения`() {
        val apps = (1..8).map { index -> app("com.$index", if (index == 1) "Maps" else "App $index") }
        ui.setContent {
            OneBarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppsGridSection(
                        apps = apps,
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
        ui.onNodeWithText("App 5").assertExists()
        ui.onNodeWithTag("apps-grid").performScrollToNode(hasText("App 5"))
        ui.onNodeWithText("App 5").assertIsDisplayed()
    }

    @Test
    fun `ряды_приложений_ровно_по_четыре_без_частичного_ряда`() {
        val apps = (1..80).map { index -> app("com.$index", "App $index") }
        ui.setContent {
            OneBarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppsGridSection(
                        apps = apps,
                        rows = 2,
                        favoritePackages = emptySet(),
                        onOpen = {},
                        onMenu = {},
                        modifier = Modifier.height(220.dp),
                    )
                }
            }
        }

        ui.onNodeWithTag("apps-row-com.1").onChildren().assertCountEquals(4)
        val gridBounds = nodeBounds("apps-grid")
        val firstIcon = nodeBounds("app-icon-com.1", useUnmergedTree = true)
        val secondIcon = nodeBounds("app-icon-com.2", useUnmergedTree = true)
        val thirdIcon = nodeBounds("app-icon-com.3", useUnmergedTree = true)
        val fourthIcon = nodeBounds("app-icon-com.4", useUnmergedTree = true)
        val density = context.resources.displayMetrics.density
        val iconWidth = AppIconSize.value * density
        assertEquals(iconWidth, firstIcon.width, 2f)
        assertEquals(gridBounds.left + ContactAvatarStart.value * density, firstIcon.left, 2f)
        assertEquals(gridBounds.right - ContactActionEnd.value * density, fourthIcon.right, 2f)
        assertEquals(secondIcon.left - firstIcon.right, thirdIcon.left - secondIcon.right, 2f)
        val expectedGap = (
            gridBounds.width - (ContactAvatarStart + ContactActionEnd).value * density -
                iconWidth * 4
            ) / 3f
        assertEquals(expectedGap, secondIcon.left - firstIcon.right, 2f)
        assertEquals(iconWidth, nodeBounds("app-label-com.1", useUnmergedTree = true).width, 2f)
        val tile = nodeBounds("grid-app-slot-com.1")
        val label = nodeBounds("app-label-com.1", useUnmergedTree = true)
        assertTrue(label.top - firstIcon.bottom <= 4.dp.value * density)
        assertTrue(tile.bottom - label.bottom > firstIcon.top - tile.top)
        ui.onNodeWithTag("apps-row-com.5").assertExists()
        ui.onNodeWithTag("apps-row-com.9").assertDoesNotExist()
        ui.onNodeWithText("App 77").assertDoesNotExist()
    }

    @Test
    fun `ряды_приложений_прокручиваются_вертикально`() {
        val apps = (1..80).map { index -> app("com.$index", "App $index") }
        ui.setContent {
            OneBarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppsGridSection(
                        apps = apps,
                        rows = 20,
                        favoritePackages = emptySet(),
                        onOpen = {},
                        onMenu = {},
                        modifier = Modifier.height(220.dp),
                    )
                }
            }
        }

        ui.onNodeWithTag("apps-grid").performScrollToNode(hasText("App 77"))
        ui.onNodeWithText("App 77").assertIsDisplayed()
        ui.onNodeWithTag("apps-grid").performScrollToNode(hasText("App 1"))
        ui.onNodeWithText("App 1").assertIsDisplayed()
    }

    @Test
    fun `однострочный_список_делает_туда_сюда_без_паузы_и_повторяет_цикл`() {
        val apps = (1..10).map { index -> app("com.$index", "App $index") }
        ui.setContent {
            OneBarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppAutoStrip(
                        apps = apps,
                        favoritePackages = emptySet(),
                        onOpen = {},
                        onMenu = {},
                    )
                }
            }
        }
        ui.waitUntilAtLeastOneExists(hasTestTag("apps-strip"), TIMEOUT)
        val strip = ui.onNodeWithTag("apps-strip")
        val stripBounds = nodeBounds("apps-strip")
        val firstIcon = nodeBounds("app-icon-com.1", useUnmergedTree = true)
        val secondIcon = nodeBounds("app-icon-com.2", useUnmergedTree = true)
        val thirdIcon = nodeBounds("app-icon-com.3", useUnmergedTree = true)
        val fourthIcon = nodeBounds("app-icon-com.4", useUnmergedTree = true)
        val density = context.resources.displayMetrics.density
        val iconWidth = AppIconSize.value * density
        assertEquals(iconWidth, firstIcon.width, 2f)
        assertEquals(stripBounds.left + ContactAvatarStart.value * density, firstIcon.left, 2f)
        assertEquals(stripBounds.right - ContactActionEnd.value * density, fourthIcon.right, 5f)
        assertEquals(secondIcon.left - firstIcon.right, thirdIcon.left - secondIcon.right, 2f)
        val expectedGap = (
            stripBounds.width - (ContactAvatarStart + ContactActionEnd).value * density -
                iconWidth * 4
            ) / 3f
        assertEquals(expectedGap, secondIcon.left - firstIcon.right, 2f)
        ui.mainClock.autoAdvance = false
        try {
            ui.mainClock.advanceTimeByFrame()
            val start = strip.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
            ui.mainClock.advanceTimeBy(3_000)
            ui.mainClock.advanceTimeBy(450)
            val forward = strip.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
            assertTrue("первое смещение не появилось: start=$start forward=$forward", forward > start)

            ui.mainClock.advanceTimeBy(450)
            val back = strip.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
            assertTrue("обратное смещение должно идти сразу: forward=$forward back=$back", back < forward)
            assertEquals(start, back, 2f)

            ui.mainClock.advanceTimeBy(10_000)
            ui.mainClock.advanceTimeBy(450)
            val repeated = strip.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
            assertTrue("повторный цикл не появился после паузы: back=$back repeated=$repeated", repeated > back)
        } finally {
            ui.mainClock.autoAdvance = true
        }
    }

    @Test
    fun `приложения_выровнены_по_аватару_и_телефону_контакта`() {
        val apps = (1..4).map { index -> app("com.$index", "App $index") }
        show(
            apps = FakeApps(known = apps),
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            history = FakeHistory(recentApps = apps.map { it.packageName }),
        )
        ui.waitUntilAtLeastOneExists(hasTestTag("apps-strip"), TIMEOUT)
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)

        val firstApp = nodeBounds("app-icon-com.1", useUnmergedTree = true)
        val fourthApp = nodeBounds("app-icon-com.4", useUnmergedTree = true)
        val avatar = nodeBounds("contact-avatar-1", useUnmergedTree = true)
        val phone = ui.onNodeWithContentDescription(str(R.string.call_action), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(avatar.left, firstApp.left, 2f)
        assertEquals(phone.right, fourthApp.right, 2f)
    }

    @Test
    fun `однострочная_секция_приложений_не_оставляет_пустоты_под_рядом`() {
        val apps = (1..8).map { index -> app("com.$index", "App $index") }
        show(
            apps = FakeApps(known = apps),
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            history = FakeHistory(recentApps = apps.map { it.packageName }),
        )
        ui.waitUntilAtLeastOneExists(hasTestTag("apps-strip"), TIMEOUT)
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)

        val expected = (SectionDividerHeight + AppsRowHeight).value * context.resources.displayMetrics.density
        val appsBounds = nodeBounds("apps-section")
        val contactsBounds = nodeBounds("contacts-section")
        assertEquals(expected, appsBounds.height, 2f)
        assertTrue(contactsBounds.height > appsBounds.height)
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

        ui.onNodeWithText("App 5").assertExists()
        ui.onNodeWithTag("app-favorite-com.1", useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithTag("apps-grid").performScrollToNode(hasText("App 5"))
        ui.onNodeWithText("App 5").assertIsDisplayed()
    }

    @Test
    fun `без_веб_поиска_приложения_и_контакты_делят_высоту_поровну`() {
        val apps = (1..8).map { index -> app("com.$index", "App $index") }
        show(
            apps = FakeApps(known = apps),
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            history = FakeHistory(recentApps = apps.map { it.packageName }),
        )
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)
        ui.onNode(hasSetTextAction()).performImeAction()
        ui.waitUntilAtLeastOneExists(hasTestTag("apps-grid"), TIMEOUT)

        val appsBounds = nodeBounds("apps-section")
        val contactsBounds = nodeBounds("contacts-section")
        assertEquals(appsBounds.height, contactsBounds.height, 24f)
        ui.onNodeWithTag("web-section").assertDoesNotExist()
        ui.onNodeWithText(str(R.string.web_search_title)).assertDoesNotExist()
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

        val smsBox = nodeBounds("swipe-action-sms-contact:1")
        val callBox = nodeBounds("swipe-action-call-contact:1")
        val yandexBox = nodeBounds("swipe-action-yandex-web:maps")
        val googleBox = nodeBounds("swipe-action-google-web:maps")
        assertEquals(googleBox.left - yandexBox.right, callBox.left - smsBox.right, 1f)
        assertEquals(googleBox.right, callBox.right, 1f)

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
    fun `подложка_плитки_смс_заполняет_тайл_при_протяжке`() {
        show(contacts = FakeContacts(known = listOf(contact(1, "Ann"))))
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)
        val tile = "swipe-action-sms-contact:1"
        val plate = "swipe-plate-sms-contact:1"
        val collapsed = actionWidth(tile)

        swipeRow("contact:1", 0.95f, 0.55f)

        ui.waitUntil(TIMEOUT) { actionWidth(tile) > collapsed }
        assertEquals(actionWidth(tile), actionWidth(plate, unmerged = true), 1f)
        assertEquals(actionHeight(tile), actionHeight(plate, unmerged = true), 1f)
        val glyph = ui.onNodeWithContentDescription(str(R.string.sms_action), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(glyph.width, glyph.height, 1f)
    }

    @Test
    fun `свайп_вправо_по_свернутой_строке_ни_чего_не_меняет`() {
        var opened = false
        show(
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            onOpenSettings = { opened = true },
        )
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)
        val collapsed = actionWidth("swipe-action-sms-contact:1")

        swipeRow("contact:1", 0.05f, 0.45f)

        assertFalse(opened)
        assertTrue(abs(actionWidth("swipe-action-sms-contact:1") - collapsed) < 1f)
        ui.onNodeWithText(str(R.string.caption_call)).assertDoesNotExist()
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
    fun `полный_свайп_растягивает_звонок_на_всю_строку_перед_запуском`() {
        val history = FakeHistory()
        show(
            contacts = FakeContacts(known = listOf(contact(1, "Ann"))),
            history = history,
        )
        ui.waitUntilAtLeastOneExists(hasText("Ann"), TIMEOUT)
        val row = ui.onNodeWithTag("swipe-row-contact:1")
        val bounds = row.fetchSemanticsNode().boundsInRoot
        val call = "swipe-action-call-contact:1"

        try {
            row.performTouchInput {
                swipe(
                    start = Offset(width * 0.95f, height / 2f),
                    end = Offset(width * 0.35f, height / 2f),
                    durationMillis = 300,
                )
            }
            ui.mainClock.autoAdvance = false
            ui.mainClock.advanceTimeBy(80)
            val growingWidth = actionWidth(call)
            assertTrue(
                "growing=$growingWidth quarter=${bounds.width * 0.25f} row=${bounds.width}",
                growingWidth >= bounds.width * 0.25f - 1f,
            )
            assertTrue(growingWidth < bounds.width)

            ui.mainClock.advanceTimeBy(160)
            assertEquals(bounds.width, actionWidth(call), 1f)
            assertEquals(bounds.height, actionHeight(call), 1f)
            assertTrue(history.pushedCommunications.isEmpty())
            ui.mainClock.advanceTimeBy(120)
        } finally {
            ui.mainClock.autoAdvance = true
        }

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
        assertEquals(1, ui.onAllNodesWithText(str(R.string.web_search_title)).fetchSemanticsNodes().size)
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
    fun `веб_поиск_сохраняет_запрос_в_историю`() {
        val history = FakeHistory()
        val searches = mutableListOf<Pair<WebEngine, String>>()
        show(
            history = history,
            suggestions = FakeSuggestions(replies = mapOf("коф" to listOf("кофе в зёрнах"))),
            onWebSearch = { engine, text -> searches += engine to text },
        )
        ui.onNode(hasSetTextAction()).performTextInput("коф")
        ui.waitUntilAtLeastOneExists(hasText("кофе в зёрнах"), TIMEOUT)

        ui.onAllNodesWithContentDescription(str(R.string.search_google))
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)

        ui.waitUntil(TIMEOUT) { history.pushedQueries.isNotEmpty() }
        assertEquals(listOf("кофе в зёрнах"), history.pushedQueries)
        assertEquals(listOf(WebEngine.GOOGLE to "кофе в зёрнах"), searches)
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
        ui.onNodeWithTag("history-trigger").performTouchInput { click() }

        ui.waitUntilAtLeastOneExists(hasText("кофе"), TIMEOUT)
    }

    @Test
    fun `тап_по_пустому_полю_показывает_историю_запросов`() {
        val viewModel = show(history = FakeHistory(queries = listOf("кофе", "пицца")))
        ui.waitUntil(TIMEOUT) { viewModel.state.value.queries.size == 2 }

        ui.onNodeWithTag("history-trigger").performTouchInput { click() }

        assertTrue(viewModel.state.value.historyRequested)
        assertEquals(listOf("кофе", "пицца"), viewModel.state.value.suggestions)
        ui.waitUntilAtLeastOneExists(hasText("кофе"), TIMEOUT)
        ui.onNodeWithTag("web-suggestions").performScrollToNode(hasText("пицца"))
        ui.onNodeWithText("пицца").assertIsDisplayed()
        assertEquals(1, ui.onAllNodesWithText(str(R.string.web_search_title)).fetchSemanticsNodes().size)
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
