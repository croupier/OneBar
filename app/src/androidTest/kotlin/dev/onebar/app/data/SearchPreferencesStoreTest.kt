package dev.onebar.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchPreferencesStoreTest {

    private lateinit var preferencesName: String

    @Before
    fun createPreferences() {
        preferencesName = "search-preferences-test-${System.nanoTime()}"
    }

    @After
    fun removePreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `все_опции_включены_по_умолчанию`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

        assertEquals(SearchOptions(), SearchPreferencesStore(preferences).options.value)
    }

    @Test
    fun `изменённые_опции_переживают_пересоздание`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val changed = SearchOptions(
            historyFavoritesFirst = false,
            prefix = false,
            contains = true,
            phone = false,
            yo = false,
            translit = false,
            extraFields = false,
            webSuggestions = false,
        )
        SearchPreferencesStore(preferences).setOptions(changed)

        val reopened = SearchPreferencesStore(preferences)
        assertEquals(changed, reopened.options.value)
        reopened.setOptions(SearchOptions())
        assertEquals(SearchOptions(), reopened.options.value)
    }

    @Test
    fun `старый_ключ_приоритета_истории_читается`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        preferences.edit().putBoolean("history_favorites_first", false).commit()

        assertFalse(SearchPreferencesStore(preferences).options.value.historyFavoritesFirst)
        assertTrue(SearchPreferencesStore(preferences).options.value.prefix)
    }
}
