package dev.onebar.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.onebar.app.R
import dev.onebar.app.data.KeyboardStartup
import dev.onebar.app.data.LanguageOption
import dev.onebar.app.data.ThemeOption
import dev.onebar.app.ui.theme.OneBarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val ui = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun scrollTo(text: String) {
        ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun swipeHorizontally(fraction: Float) {
        val node = ui.onRoot()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val start = Offset(bounds.center.x, bounds.center.y)
        val end = Offset(bounds.center.x + bounds.width * fraction, bounds.center.y)
        node.performTouchInput { swipe(start, end, durationMillis = 300) }
        ui.waitForIdle()
    }

    private fun show(
        onBack: () -> Unit = {},
        initialLanguageSelection: Boolean = false,
        language: LanguageOption = LanguageOption.SYSTEM,
        theme: ThemeOption = ThemeOption.SYSTEM,
        keyboardStartup: KeyboardStartup = KeyboardStartup.IMMEDIATE,
        onLanguageChange: (LanguageOption) -> Unit = {},
        onThemeChange: (ThemeOption) -> Unit = {},
        onKeyboardStartupChange: (KeyboardStartup) -> Unit = {},
        historyFavoritesFirst: Boolean = true,
        onHistoryFavoritesFirstChange: (Boolean) -> Unit = {},
        saveHistory: Boolean = true,
        onSaveHistory: suspend (Boolean) -> Unit = {},
        onClearHistory: suspend () -> Unit = {},
        onSend: (String) -> Unit = {},
    ) {
        ui.setContent {
            var selectedLanguage by remember { mutableStateOf(language) }
            var selectedTheme by remember { mutableStateOf(theme) }
            var selectedKeyboardStartup by remember { mutableStateOf(keyboardStartup) }
            OneBarTheme(theme = selectedTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        onBack = onBack,
                        initialLanguageSelection = initialLanguageSelection,
                        language = selectedLanguage,
                        theme = selectedTheme,
                        keyboardStartup = selectedKeyboardStartup,
                        onLanguageChange = {
                            selectedLanguage = it
                            onLanguageChange(it)
                        },
                        onThemeChange = {
                            selectedTheme = it
                            onThemeChange(it)
                        },
                        onKeyboardStartupChange = {
                            selectedKeyboardStartup = it
                            onKeyboardStartupChange(it)
                        },
                        historyFavoritesFirst = historyFavoritesFirst,
                        onHistoryFavoritesFirstChange = onHistoryFavoritesFirstChange,
                        saveHistory = saveHistory,
                        onSaveHistory = onSaveHistory,
                        onClearHistory = onClearHistory,
                        onSend = onSend,
                    )
                }
            }
        }
    }

    @Test
    fun `экран_показывает_все_разделы_и_системные_значения`() {
        show()

        ui.onNodeWithText(str(R.string.settings_title)).assertExists()
        ui.onNodeWithText(str(R.string.settings_language)).assertExists()
        ui.onNodeWithText(str(R.string.settings_theme)).assertExists()
        ui.onNodeWithText(str(R.string.settings_keyboard_startup)).assertExists()
        scrollTo(str(R.string.settings_search))
        ui.onNodeWithText(str(R.string.settings_search)).assertExists()
        scrollTo(str(R.string.settings_history))
        ui.onNodeWithText(str(R.string.settings_history)).assertExists()
        scrollTo(str(R.string.settings_about))
        ui.onNodeWithText(str(R.string.settings_about)).assertExists()
        scrollTo(str(R.string.settings_feedback))
        ui.onNodeWithText(str(R.string.settings_feedback)).assertExists()
    }

    @Test
    fun `выбор_языка_обновляет_интерфейс_и_передаёт_выбор`() {
        var selected: LanguageOption? = null
        show(onLanguageChange = { selected = it })

        ui.onNode(hasText(str(R.string.settings_language)) and hasClickAction()).performClick()
        ui.onNodeWithText(str(R.string.settings_language_russian)).performClick()

        ui.onNodeWithText(str(R.string.settings_cancel)).assertDoesNotExist()
        ui.onNodeWithText(str(R.string.settings_language_russian)).assertExists()
        assertEquals(LanguageOption.RUSSIAN, selected)
    }

    @Test
    fun `первый_запуск_сразу_открывает_выбор_и_не_закрывается_назад`() {
        var selected: LanguageOption? = null
        show(
            initialLanguageSelection = true,
            language = LanguageOption.ENGLISH,
            onLanguageChange = { selected = it },
        )

        ui.onNodeWithText(str(R.string.settings_language)).assertIsDisplayed()
        ui.onNodeWithText(str(R.string.settings_cancel)).assertDoesNotExist()
        ui.onNodeWithText(str(R.string.settings_title)).assertDoesNotExist()

        pressBack()
        ui.onNodeWithText(str(R.string.settings_language)).assertIsDisplayed()
        assertNull(selected)

        ui.onNodeWithText(str(R.string.settings_language_russian)).performClick()
        assertEquals(LanguageOption.RUSSIAN, selected)
    }

    @Test
    fun `выбор_темы_обновляет_интерфейс_и_передаёт_выбор`() {
        var selected: ThemeOption? = null
        show(onThemeChange = { selected = it })

        ui.onNode(hasText(str(R.string.settings_theme)) and hasClickAction()).performClick()
        ui.onNodeWithText(str(R.string.settings_theme_dark)).performClick()

        ui.onNodeWithText(str(R.string.settings_cancel)).assertDoesNotExist()
        ui.onNodeWithText(str(R.string.settings_theme_dark)).assertExists()
        assertEquals(ThemeOption.DARK, selected)
    }

    @Test
    fun `выбор_режима_клавиатуры_обновляет_интерфейс_и_передаёт_выбор`() {
        var selected: KeyboardStartup? = null
        show(onKeyboardStartupChange = { selected = it })

        ui.onNode(hasText(str(R.string.settings_keyboard_startup)) and hasClickAction()).performClick()
        ui.onNodeWithText(str(R.string.settings_keyboard_startup_on_focus)).performClick()

        ui.onNodeWithText(str(R.string.settings_cancel)).assertDoesNotExist()
        ui.onNodeWithText(str(R.string.settings_keyboard_startup_on_focus)).assertExists()
        assertEquals(KeyboardStartup.ON_FOCUS, selected)
    }

    @Test
    fun `приоритет_истории_и_избранного_сохраняется`() {
        val saved = mutableListOf<Boolean>()
        show(onHistoryFavoritesFirstChange = { saved += it })
        val label = str(R.string.settings_search_favorites)

        scrollTo(label)
        ui.onNodeWithText(label).assertIsOn().performClick()
        ui.onNodeWithText(label).assertIsOff()
        assertTrue(saved.isEmpty())

        scrollTo(str(R.string.settings_save))
        ui.onNodeWithText(str(R.string.settings_save)).performClick()

        assertEquals(listOf(false), saved)
        ui.waitUntilAtLeastOneExists(hasText(str(R.string.settings_saved_message)), 5_000L)
    }

    @Test
    fun `переключатели_поиска_включены_и_сбрасываются_к_дефолту`() {
        show()
        val translit = str(R.string.settings_search_translit)

        scrollTo(translit)
        ui.onNodeWithText(translit).assertIsOn().performClick()
        ui.onNodeWithText(translit).assertIsOff()

        scrollTo(str(R.string.settings_defaults))
        ui.onNodeWithText(str(R.string.settings_defaults)).performClick()
        ui.waitUntilAtLeastOneExists(hasText(str(R.string.settings_defaults_message)), 5_000L)
        scrollTo(translit)
        ui.onNodeWithText(translit).assertIsOn()
    }

    @Test
    fun `сброс_без_изменений_показывает_отсутствие_результата`() {
        show()

        scrollTo(str(R.string.settings_defaults))
        ui.onNodeWithText(str(R.string.settings_defaults)).performClick()

        ui.waitUntilAtLeastOneExists(hasText(str(R.string.settings_no_changes_message)), 5_000L)
    }

    @Test
    fun `уведомление_о_сбросе_исчезает_само`() {
        show()
        val translit = str(R.string.settings_search_translit)
        scrollTo(translit)
        ui.onNodeWithText(translit).performClick()
        scrollTo(str(R.string.settings_defaults))
        ui.onNodeWithText(str(R.string.settings_defaults)).performClick()
        val message = hasText(str(R.string.settings_defaults_message))
        ui.waitUntilAtLeastOneExists(message, 5_000L)

        ui.waitUntilDoesNotExist(message, 10_000L)
    }

    @Test
    fun `иконка_информации_открывает_пояснение_и_закрывается_тапом`() {
        show()
        val label = str(R.string.settings_search_translit)
        val explanation = str(R.string.settings_search_translit_info)

        scrollTo(label)
        ui.onNodeWithContentDescription(str(R.string.settings_info, label)).performClick()
        ui.onNodeWithText(explanation).assertIsDisplayed()

        ui.onNodeWithText(explanation).performClick()
        ui.onNodeWithText(explanation).assertDoesNotExist()
    }

    @Test
    fun `очистка_истории_открывает_подтверждение_и_закрывается_кнопками`() {
        var clearCount = 0
        show(onClearHistory = { clearCount++ })
        val clearButton = str(R.string.settings_history_clear)

        scrollTo(clearButton)
        ui.onNodeWithText(clearButton).performClick()
        ui.onNodeWithText(str(R.string.settings_history_clear_title)).assertExists()
        ui.onNodeWithText(str(R.string.settings_cancel)).performClick()
        ui.onNodeWithText(str(R.string.settings_history_clear_title)).assertDoesNotExist()
        assertEquals(0, clearCount)

        ui.onNodeWithText(clearButton).performClick()
        ui.onNodeWithText(str(R.string.settings_clear)).performClick()
        ui.onNodeWithText(str(R.string.settings_history_clear_title)).assertDoesNotExist()
        assertEquals(1, clearCount)
        ui.waitUntilAtLeastOneExists(hasText(str(R.string.settings_history_cleared_message)), 5_000L)
    }

    @Test
    fun `сохранённая_настройка_показывает_выключенную_историю`() {
        show(saveHistory = false)

        scrollTo(str(R.string.settings_history_save))
        ui.onNodeWithText(str(R.string.settings_history_save)).assertIsOff()
    }

    @Test
    fun `галочка_истории_применяется_только_по_кнопке_сохранить`() {
        val saved = mutableListOf<Boolean>()
        show(onSaveHistory = { saved += it })
        val label = str(R.string.settings_history_save)

        scrollTo(label)
        ui.onNodeWithText(label).assertIsOn().performClick()
        ui.onNodeWithText(label).assertIsOff()
        assertTrue(saved.isEmpty())

        scrollTo(str(R.string.settings_save))
        ui.onNodeWithText(str(R.string.settings_save)).performClick()

        assertEquals(listOf(false), saved)
        ui.waitUntilAtLeastOneExists(hasText(str(R.string.settings_saved_message)), 5_000L)
    }

    @Test
    fun `сохранить_при_выходе_применяет_галочку_истории`() {
        var finished = false
        val saved = mutableListOf<Boolean>()
        show(onBack = { finished = true }, onSaveHistory = { saved += it })
        val label = str(R.string.settings_history_save)

        scrollTo(label)
        ui.onNodeWithText(label).performClick()
        ui.onNodeWithContentDescription(str(R.string.settings_back)).performClick()
        ui.onNodeWithText(str(R.string.settings_unsaved_title)).assertExists()
        ui.onNodeWithText(str(R.string.settings_yes)).performClick()

        assertTrue(finished)
        assertEquals(listOf(false), saved)
    }

    @Test
    fun `поле_обратной_связи_передаёт_текст_при_отправке`() {
        val sent = mutableListOf<String>()
        show(onSend = { sent += it })

        ui.onNode(hasScrollAction()).performScrollToNode(hasSetTextAction())
        ui.onNode(hasSetTextAction()).performTextInput("Нужна помощь с приложением")
        ui.onNode(hasSetTextAction()).assertTextContains("Нужна помощь с приложением")
        scrollTo(str(R.string.settings_feedback_send))
        ui.onNodeWithText(str(R.string.settings_feedback_send)).performClick()

        assertEquals(listOf("Нужна помощь с приложением"), sent)
    }

    @Test
    fun `кнопка_назад_предупреждает_о_несохранённых_галочках`() {
        var finished = false
        show(onBack = { finished = true })
        val translit = str(R.string.settings_search_translit)
        scrollTo(translit)
        ui.onNodeWithText(translit).performClick()

        ui.onNodeWithContentDescription(str(R.string.settings_back)).performClick()
        ui.onNodeWithText(str(R.string.settings_unsaved_title)).assertExists()
        assertFalse(finished)

        ui.onNodeWithText(str(R.string.settings_cancel)).performClick()
        ui.onNodeWithText(str(R.string.settings_unsaved_title)).assertDoesNotExist()
        assertFalse(finished)

        ui.onNodeWithContentDescription(str(R.string.settings_back)).performClick()
        ui.onNodeWithText(str(R.string.settings_no)).performClick()
        assertTrue(finished)
    }

    @Test
    fun `системный_назад_показывает_предупреждение_а_да_завершает_экран`() {
        var finished = false
        show(onBack = { finished = true })
        val translit = str(R.string.settings_search_translit)
        scrollTo(translit)
        ui.onNodeWithText(translit).performClick()

        pressBack()
        ui.waitUntilAtLeastOneExists(hasText(str(R.string.settings_unsaved_title)), 10_000L)
        assertFalse(finished)

        ui.onNodeWithText(str(R.string.settings_yes)).performClick()
        assertTrue(finished)
    }

    @Test
    fun `сохранение_поиска_снимает_предупреждение`() {
        var finished = false
        show(onBack = { finished = true })
        val translit = str(R.string.settings_search_translit)
        scrollTo(translit)
        ui.onNodeWithText(translit).performClick()

        scrollTo(str(R.string.settings_save))
        ui.onNodeWithText(str(R.string.settings_save)).performClick()
        ui.waitUntilAtLeastOneExists(hasText(str(R.string.settings_saved_message)), 5_000L)
        ui.onNodeWithContentDescription(str(R.string.settings_back)).performClick()

        assertTrue(finished)
        ui.onNodeWithText(str(R.string.settings_unsaved_title)).assertDoesNotExist()
    }

    @Test
    fun `сохранение_без_изменений_показывает_отсутствие_результата`() {
        show()

        scrollTo(str(R.string.settings_save))
        ui.onNodeWithText(str(R.string.settings_save)).performClick()

        ui.waitUntilAtLeastOneExists(hasText(str(R.string.settings_no_changes_message)), 5_000L)
    }

    @Test
    fun `кнопка_назад_вызывает_завершение_экрана`() {
        var finished = false
        show(onBack = { finished = true })

        ui.onNodeWithContentDescription(str(R.string.settings_back)).performClick()

        assertTrue(finished)
    }

    @Test
    fun `свайп_назад_не_завершает_экран`() {
        var finished = false
        show(onBack = { finished = true })

        swipeHorizontally(-0.4f)

        assertFalse(finished)
        ui.onNodeWithText(str(R.string.settings_title)).assertIsDisplayed()
    }
}
