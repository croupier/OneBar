package dev.onebar.app.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Heights from the Pixel 8a (411x914 dp): 405 dp with the keyboard, 807 dp without it. */
class PanelRowsTest {
    private val withKeyboard = 405.dp
    private val withoutKeyboard = 807.dp

    @Test
    fun `с клавиатурой приложения занимают один ряд и веб-список скрыт`() {
        val rows = panelRows(withKeyboard, hasApps = true, hasSuggestions = false)
        assertEquals(1, rows.appRows)
        assertEquals(0, rows.webRows)
    }

    @Test
    fun `без клавиатуры приложения занимают три ряда`() {
        val rows = panelRows(withoutKeyboard, hasApps = true, hasSuggestions = false)
        assertEquals(3, rows.appRows)
        assertEquals(0, rows.webRows)
    }

    @Test
    fun `веб-подсказки оставляют приложениям больше одного ряда`() {
        val rows = panelRows(withoutKeyboard, hasApps = true, hasSuggestions = true)
        assertTrue(rows.appRows > 1)
        assertEquals(3, rows.webRows)
    }

    @Test
    fun `с клавиатурой веб-подсказки занимают одну строку`() {
        val rows = panelRows(withKeyboard, hasApps = true, hasSuggestions = true)
        assertEquals(1, rows.appRows)
        assertEquals(1, rows.webRows)
    }

    @Test
    fun `без приложений ряд плиток не нужен`() {
        val rows = panelRows(withoutKeyboard, hasApps = false, hasSuggestions = false)
        assertEquals(0, rows.appRows)
    }

    @Test
    fun `на низкой высоте сохраняется один доступный ряд`() {
        val rows = panelRows(250.dp, hasApps = true, hasSuggestions = false)
        assertEquals(1, rows.appRows)
    }
}
