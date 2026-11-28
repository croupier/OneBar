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

    @Test
    fun `точное совпадение идёт раньше перестановки е-ё`() {
        val withYo = listOf(app("a", "Фёкла"), app("b", "Фекла"), app("c", "Флак"))
        assertEquals(listOf("Фекла", "Фёкла"), rankApps(withYo, "фек", 10).map { it.label })
    }

    @Test
    fun `транслит ищет в обе стороны`() {
        val mixed = listOf(app("a", "Код"), app("b", "Погода"), app("c", "Maps"))
        assertEquals(listOf("Код"), rankApps(mixed, "kod", 10).map { it.label })
        assertEquals(listOf("Maps"), rankApps(mixed, "мап", 10).map { it.label })
    }

    @Test
    fun `избранные идут впереди более точных совпадений`() {
        val list = listOf(app("com.best", "Карты"), app("com.fav", "Мои Карты"))
        val found = rankApps(list, "карт", 10, favorites = setOf("com.fav"))
        assertEquals(listOf("com.fav", "com.best"), found.map { it.packageName })
    }

    @Test
    fun `в режиме избранного неизбранные не добираются до лимита`() {
        val list = listOf(app("com.best", "Карты"), app("com.fav", "Мои Карты"), app("com.two", "Картон"))
        val found = rankApps(list, "карт", 2, favorites = setOf("com.fav"), favoritesOnly = true)
        assertEquals(listOf("com.fav"), found.map { it.packageName })
    }

    @Test
    fun `пакет и описание подключаются только когда название молчит`() {
        val list = listOf(
            app("com.x", "Mbank"),
            app("ru.tinkoff.mbank", "Тинькофф"),
            app("com.d", "Калькулятор", description = "Ноты"),
        )
        assertEquals(
            listOf("com.x", "ru.tinkoff.mbank"),
            rankApps(list, "mbank", 10).map { it.packageName },
        )
        assertEquals(listOf("com.d"), rankApps(list, "нот", 10).map { it.packageName })
    }

    @Test
    fun `отключённое_вхождение_оставляет_только_начало_названия`() {
        val found = rankApps(apps, "map", 10, options = SearchOptions(contains = false))
        assertEquals(listOf("Maps"), found.map { it.label })
    }

    @Test
    fun `отключённый_приоритет_начала_не_исключает_совпадения_внутри`() {
        val found = rankApps(apps, "map", 10, options = SearchOptions(prefix = false))
        assertEquals(setOf("Maps", "appmaps"), found.map { it.label }.toSet())
    }

    @Test
    fun `отключённые_дополнительные_поля_не_ищут_по_пакету`() {
        val list = listOf(app("com.example.maps", "Launcher"))
        assertTrue(rankApps(list, "maps", 10).isNotEmpty())
        assertTrue(rankApps(list, "maps", 10, options = SearchOptions(extraFields = false)).isEmpty())
    }
}
