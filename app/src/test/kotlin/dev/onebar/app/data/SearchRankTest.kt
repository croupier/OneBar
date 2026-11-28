package dev.onebar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankTest {

    @Test
    fun `варианты запроса идут в порядке приоритета`() {
        val needles = buildNeedles(" Фек ")
        assertEquals(listOf("фек", "фёк", "fek", "fec"), needles.map { it.text })
        assertEquals(
            listOf(VariantKind.EXACT, VariantKind.YO, VariantKind.TRANSLIT, VariantKind.TRANSLIT),
            needles.map { it.kind },
        )
    }

    @Test
    fun `опции_отключают_номерные_ё_и_транслит_варианты`() {
        val options = SearchOptions(phone = false, yo = false, translit = false)
        assertEquals(listOf("фек"), buildNeedles("Фек", options).map { it.text })
        assertEquals(listOf("89991234567"), buildNeedles("89991234567", options).map { it.text })
    }

    @Test
    fun `пустой запрос не даёт вариантов`() {
        assertTrue(buildNeedles("   ").isEmpty())
    }

    @Test
    fun `выключенные опции начала и вхождения не выключают поиск`() {
        val options = SearchOptions(prefix = false, contains = false)
        val needles = buildNeedles("маг", options)

        assertNotNull(rankOf(needles, listOf(listOf("магнит")), false, options))
        assertEquals(POSITION_INSIDE, rankOf(needles, listOf(listOf("магнит")), false, options)?.position)
        assertNotNull(rankOf(needles, listOf(listOf("сумма магнитофона")), false, options))
        assertNull(rankOf(needles, listOf(listOf("кастрюля")), false, options))
    }

    @Test
    fun `только_совпадение_сначала_не_находит_середину`() {
        val options = SearchOptions(prefix = true, contains = false)
        val needles = buildNeedles("маг", options)

        assertEquals(POSITION_START, rankOf(needles, listOf(listOf("магнит")), false, options)?.position)
        assertNull(rankOf(needles, listOf(listOf("сумма магнитофона")), false, options))
    }

    @Test
    fun `совпадение_сначала_идёт_перед_вхождением`() {
        val needles = buildNeedles("маг")

        assertEquals(POSITION_START, rankOf(needles, listOf(listOf("магнит")), false)?.position)
        assertEquals(POSITION_INSIDE, rankOf(needles, listOf(listOf("сумма магнитофона")), false)?.position)
    }

    @Test
    fun `перестановки е-ё идут от меньшего числа замен к большему`() {
        assertEquals(listOf("ёел", "еёл", "ёёл"), yoVariants("еел"))
    }

    @Test
    fun `кириллица даёт транслит в латиницу и наоборот`() {
        assertEquals(listOf("kod", "cod"), translitVariants("код"))
        assertEquals("мус", translitVariants("mus").first())
        assertTrue(translitVariants("mus").contains("муз"))
    }

    @Test
    fun `смешанное написание транслита не даёт — направление не определить`() {
        assertTrue(translitVariants("код kod").isEmpty())
        assertTrue(translitVariants("7 ").isEmpty())
    }

    @Test
    fun `номерной запрос даёт формы без разделителей и без восьмёрки`() {
        assertEquals("+79991234567", numberDigits("+7 999 123-45-67"))
        assertEquals(listOf("79991234567"), phoneForms("+7 999 123-45-67"))
        assertEquals(listOf("89991234567", "9991234567"), phoneForms("8 (999) 123-45-67"))
    }

    @Test
    fun `короткое и текстовое написание номером не считается`() {
        assertTrue(phoneForms("12").isEmpty())
        assertTrue(phoneForms("магнит 5").isEmpty())
    }

    @Test
    fun `маски номеров ставят разделитель между цифрами`() {
        assertEquals(
            listOf("%8%9%9%9%1%2%3%4%5%6%7%", "%9%9%9%1%2%3%4%5%6%7%"),
            phoneLikePatterns("89991234567"),
        )
    }

    @Test
    fun `номерной запрос не порождает е-ё и транслит`() {
        assertEquals(listOf(VariantKind.EXACT, VariantKind.PHONE), buildNeedles("89991234567").map { it.kind })
    }

    @Test
    fun `апостроф читается как мягкий знак а клавиша е как ё`() {
        assertEquals("обьект", translitVariants("ob'ekt").first())
        assertEquals("ёлка", translitVariants("`lka").first())
    }

    @Test
    fun `дальние комбинации транслита отсекаются по числу замен`() {
        val variants = translitVariants("sus")
        assertEquals(listOf("сус", "суз", "сис", "зус"), variants.take(4))
        assertTrue("зиз" !in variants)
    }

    @Test
    fun `маски для like снимают регистр и заглушают е-ё`() {
        val patterns = likePatterns("Семён")
        assertTrue(patterns.containsAll(listOf("с_м_н", "С_М_Н", "С_м_н")))
        // Латиницу SQLite складывает сам, поэтому для неё достаточно нижнего регистра.
        assertTrue(patterns.containsAll(listOf("semyon", "semen")))
        assertEquals(3, patterns.count { it.any { char -> char.code > 127 } })
    }

    @Test
    fun `знаки подстановки из запроса экранируются`() {
        assertTrue(likePatterns("100%").contains("""100\%"""))
        assertTrue(likePatterns("a_b").contains("""a\_b"""))
    }

    @Test
    fun `группа like-маск не отменяет фильтр перед ней`() {
        val clause = likeClause(listOf("%а%", "%А%", "%a%"), "display_name")
        assertEquals(
            """(display_name LIKE ? ESCAPE '\' OR display_name LIKE ? ESCAPE '\' OR display_name LIKE ? ESCAPE '\')""",
            clause,
        )
    }

    @Test
    fun `число масок для like ограничено`() {
        val patterns = likePatterns("susus")
        assertEquals(12, patterns.size)
        assertEquals("susus", patterns.first())
    }
}
