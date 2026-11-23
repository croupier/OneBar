package dev.onebar.app.data

import dev.onebar.app.testing.app
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRankingTest {

    private val apps = listOf(
        app("a", "Камера"),
        app("b", "Maps"),
        app("c", "Карта"),
        app("d", "appmaps"),
    )

    @Test
    fun `пустой запрос отдаёт список как есть и в пределах лимита`() {
        assertEquals(listOf("Камера", "Maps", "Карта"), rankApps(apps, "", 3).map { it.label })
        assertEquals(apps.map { it.label }, rankApps(apps, "   ", 10).map { it.label })
    }

    @Test
    fun `совпадение с начала названия идёт раньше совпадения внутри`() {
        val found = rankApps(apps, "map", 10).map { it.label }
        assertEquals(listOf("Maps", "appmaps"), found)
    }

    @Test
    fun `регистр и пробелы по краям запроса не влияют на поиск`() {
        val spaced = rankApps(apps, "  MAPS ", 10).map { it.label }
        assertEquals(rankApps(apps, "map", 10).map { it.label }, spaced)
        assertEquals(listOf("Камера", "Карта"), rankApps(apps, " КА ", 10).map { it.label })
    }

    @Test
    fun `лимит режет сначала менее релевантную корзину`() {
        assertEquals(listOf("Maps"), rankApps(apps, "map", 1).map { it.label })
    }

    @Test
    fun `нет совпадений — пустой результат`() {
        assertTrue(rankApps(apps, "нет такого", 10).isEmpty())
    }
}
