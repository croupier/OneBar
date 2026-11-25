package dev.onebar.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
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
}
