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
    fun `приоритет_истории_включён_по_умолчанию`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

        assertTrue(SearchPreferencesStore(preferences).historyFavoritesFirst.value)
    }

    @Test
    fun `выключенный_приоритет_переживает_пересоздание`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        SearchPreferencesStore(preferences).setHistoryFavoritesFirst(false)

        val reopened = SearchPreferencesStore(preferences)
        assertFalse(reopened.historyFavoritesFirst.value)
        reopened.setHistoryFavoritesFirst(true)
        assertEquals(true, reopened.historyFavoritesFirst.value)
    }
}
