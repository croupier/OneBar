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
    fun `с клавиатурой плитки в один ряд и четыре контакта без внешнего скролла`() {
        val rows = panelRows(withKeyboard, hasApps = true, hasHistory = false, hasSuggestions = false)
        assertEquals(1, rows.appRows)
        assertEquals(4, rows.contactRows)
        assertFalse(rows.scroll)
    }

    @Test
    fun `без клавиатуры плитки в два ряда и контакты на весь остаток`() {
        val rows = panelRows(withoutKeyboard, hasApps = true, hasHistory = false, hasSuggestions = false)
        assertEquals(2, rows.appRows)
        assertEquals(9, rows.contactRows)
        assertFalse(rows.scroll)
    }

    @Test
    fun `веб подсказки забирают высоту контактов но не рядов плиток`() {
        val rows = panelRows(withoutKeyboard, hasApps = true, hasHistory = false, hasSuggestions = true)
        assertEquals(2, rows.appRows)
        assertEquals(6, rows.contactRows)
        assertFalse(rows.scroll)
    }

    @Test
    fun `история запросов при клавиатуре теснит контакты до минимума и включает скролл`() {
        val rows = panelRows(withKeyboard, hasApps = true, hasHistory = true, hasSuggestions = false)
        assertEquals(1, rows.appRows)
        assertEquals(3, rows.contactRows)
        assertTrue(rows.scroll)
    }

    @Test
    fun `без приложений вся высота уходит контактам`() {
        val rows = panelRows(withoutKeyboard, hasApps = false, hasHistory = false, hasSuggestions = false)
        assertEquals(0, rows.appRows)
        assertEquals(12, rows.contactRows)
        assertFalse(rows.scroll)
    }

    @Test
    fun `на низкой высоте минимум контактов всё равно виден и появляется скролл`() {
        val rows = panelRows(250.dp, hasApps = true, hasHistory = false, hasSuggestions = false)
        assertEquals(1, rows.appRows)
        assertEquals(3, rows.contactRows)
        assertTrue(rows.scroll)
    }

    @Test
    fun `второго ряда нет если после него остаётся меньше пяти строк контактов`() {
        // 500 dp: с историей два ряда плиток оставляют 127 dp под контакты — берём один ряд,
        // в нём блокам хватает места, и внешний скролл не нужен.
        val rows = panelRows(500.dp, hasApps = true, hasHistory = true, hasSuggestions = false)
        assertEquals(1, rows.appRows)
        assertEquals(3, rows.contactRows)
        assertFalse(rows.scroll)
    }
}
