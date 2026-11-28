package dev.onebar.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceStoreTest {

    private lateinit var preferences: SharedPreferences

    @Before
    fun createEmptyPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = context.getSharedPreferences(
            "appearance-test-${System.nanoTime()}",
            Context.MODE_PRIVATE,
        )
    }

    @After
    fun removePreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `без_выбора_используется_системная_тема`() {
        assertEquals(ThemeOption.SYSTEM, AppearanceStore(preferences).theme.value)
    }

    @Test
    fun `выбранная_тема_сохраняется_и_видна_новому_хранилищу`() {
        val store = AppearanceStore(preferences)

        store.setTheme(ThemeOption.DARK)

        assertEquals(ThemeOption.DARK, store.theme.value)
        assertEquals(ThemeOption.DARK, AppearanceStore(preferences).theme.value)
    }

    @Test
    fun `неизвестное_значение_возвращает_системную_тему`() {
        preferences.edit().putString("theme", "unknown").commit()

        assertEquals(ThemeOption.SYSTEM, AppearanceStore(preferences).theme.value)
    }

    @Test
    fun `без_выбора_клавиатура_показывается_сразу`() {
        assertEquals(KeyboardStartup.IMMEDIATE, AppearanceStore(preferences).keyboardStartup.value)
    }

    @Test
    fun `выбранный_режим_клавиатуры_сохраняется_и_виден_новому_хранилищу`() {
        val store = AppearanceStore(preferences)

        store.setKeyboardStartup(KeyboardStartup.ON_FOCUS)

        assertEquals(KeyboardStartup.ON_FOCUS, store.keyboardStartup.value)
        assertEquals(KeyboardStartup.ON_FOCUS, AppearanceStore(preferences).keyboardStartup.value)
    }

    @Test
    fun `без_выбора_иконки_квадратные`() {
        assertEquals(IconShape.SQUARE, AppearanceStore(preferences).iconShape.value)
    }

    @Test
    fun `выбранная_форма_иконок_сохраняется_и_видна_новому_хранилищу`() {
        val store = AppearanceStore(preferences)

        store.setIconShape(IconShape.CIRCLE)

        assertEquals(IconShape.CIRCLE, store.iconShape.value)
        assertEquals(IconShape.CIRCLE, AppearanceStore(preferences).iconShape.value)
    }

    @Test
    fun `неизвестная_форма_иконок_возвращает_квадрат`() {
        preferences.edit().putString("icon_shape", "unknown").commit()

        assertEquals(IconShape.SQUARE, AppearanceStore(preferences).iconShape.value)
    }

    @Test
    fun `системный_русский_выбирается_без_обязательного_выбора`() {
        val store = AppearanceStore(preferences)

        assertEquals(LanguageOption.RUSSIAN, store.initializeLanguage(listOf("ru-RU")))
        assertFalse(store.requiresLanguageSelection)
        assertFalse(AppearanceStore(preferences).requiresLanguageSelection)
        assertNull(store.initializeLanguage(listOf("en")))
    }

    @Test
    fun `системный_английский_выбирается_без_обязательного_выбора`() {
        val store = AppearanceStore(preferences)

        assertEquals(LanguageOption.ENGLISH, store.initializeLanguage(listOf("en-US")))
        assertFalse(store.requiresLanguageSelection)
    }

    @Test
    fun `поддерживаемый_язык_выбирается_из_системного_списка`() {
        val store = AppearanceStore(preferences)

        assertEquals(LanguageOption.RUSSIAN, store.initializeLanguage(listOf("de-DE", "ru-RU")))
        assertFalse(store.requiresLanguageSelection)
    }

    @Test
    fun `другой_системный_язык_включает_английский_и_оставляет_обязательный_выбор`() {
        val store = AppearanceStore(preferences)

        assertEquals(LanguageOption.ENGLISH, store.initializeLanguage(listOf("de-DE")))
        assertTrue(store.requiresLanguageSelection)
        assertTrue(store.usesEnglishLanguageDefault)
        assertTrue(AppearanceStore(preferences).requiresLanguageSelection)
        assertEquals(LanguageOption.ENGLISH, store.initializeLanguage(listOf("de-DE")))
        assertTrue(store.requiresLanguageSelection)
    }

    @Test
    fun `выбор_языка_завершает_первый_запуск`() {
        val store = AppearanceStore(preferences)
        store.initializeLanguage(listOf("de-DE"))

        store.completeLanguageSelection()

        assertFalse(store.requiresLanguageSelection)
        assertFalse(store.usesEnglishLanguageDefault)
        assertFalse(AppearanceStore(preferences).requiresLanguageSelection)
    }
}
