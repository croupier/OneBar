package dev.onebar.app.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Heights from the Pixel 8a (411x914 dp): 405 dp under the bar with the keyboard open,
 * 807 dp without it (the IME takes 402 dp).
 */
class PanelRowsTest {
    private val withKeyboard = 405.dp
    private val withoutKeyboard = 807.dp

    @Test
    fun `с клавиатурой плитки в один ряд без внешнего скролла`() {
        val rows = panelRows(withKeyboard, hasApps = true, hasHistory = false, hasSuggestions = false)
        assertEquals(1, rows.appRows)
        assertFalse(rows.scroll)
    }

    @Test
    fun `без клавиатуры плитки в два ряда без внешнего скролла`() {
        val rows = panelRows(withoutKeyboard, hasApps = true, hasHistory = false, hasSuggestions = false)
        assertEquals(2, rows.appRows)
        assertFalse(rows.scroll)
    }

    @Test
    fun `веб подсказки не забирают второй ряд плиток`() {
        val rows = panelRows(withoutKeyboard, hasApps = true, hasHistory = false, hasSuggestions = true)
        assertEquals(2, rows.appRows)
        assertFalse(rows.scroll)
    }

    @Test
    fun `история запросов при клавиатуре включает внешний скролл`() {
        val rows = panelRows(withKeyboard, hasApps = true, hasHistory = true, hasSuggestions = false)
        assertEquals(1, rows.appRows)
        assertTrue(rows.scroll)
    }

    @Test
    fun `без приложений ряд плиток не нужен`() {
        val rows = panelRows(withoutKeyboard, hasApps = false, hasHistory = false, hasSuggestions = false)
        assertEquals(0, rows.appRows)
        assertFalse(rows.scroll)
    }

    @Test
    fun `на низкой высоте включается внешний скролл`() {
        val rows = panelRows(250.dp, hasApps = true, hasHistory = false, hasSuggestions = false)
        assertEquals(1, rows.appRows)
        assertTrue(rows.scroll)
    }

    @Test
    fun `второго ряда нет если после него остаётся меньше пяти строк контактов`() {
        // 500 dp: с историей два ряда плиток оставляют 127 dp под контакты — берём один ряд,
        // в нём блокам хватает места, и внешний скролл не нужен.
        val rows = panelRows(500.dp, hasApps = true, hasHistory = true, hasSuggestions = false)
        assertEquals(1, rows.appRows)
        assertFalse(rows.scroll)
    }
}
