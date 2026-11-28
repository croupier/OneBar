package dev.onebar.app.data

import dev.onebar.app.testing.contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRankingTest {

    private val rows = listOf(
        contact(1, "Фёкла"),
        contact(2, "Фекла"),
        contact(3, "Иванов"),
        contact(4, "ann"),
    )

    @Test
    fun `отключённые_номера_не_находят_контакт_по_телефону`() {
        val list = listOf(contact(1, "Иван", numbers = listOf("79991234567")))
        assertTrue(rankContacts(list, "+7 999 123-45-67", 10).isNotEmpty())
        assertTrue(rankContacts(list, "+7 999 123-45-67", 10, options = SearchOptions(phone = false)).isEmpty())
    }

    @Test
    fun `отключённые_дополнительные_поля_не_ищут_по_почте`() {
        val list = listOf(contact(1, "Боб", email = "bob@onebar.dev"))
        assertTrue(rankContacts(list, "onebar", 10).isNotEmpty())
        assertTrue(rankContacts(list, "onebar", 10, options = SearchOptions(extraFields = false)).isEmpty())
    }

    @Test
    fun `отключённый_транслит_не_находит_латинское_написание`() {
        assertEquals(listOf("Иванов"), rankContacts(rows, "ivanov", 10).map { it.name })
        assertTrue(rankContacts(rows, "ivanov", 10, options = SearchOptions(translit = false)).isEmpty())
    }

    @Test
    fun `регистр названия не важен с обеих сторон`() {
        assertEquals(listOf("ann"), rankContacts(rows, "ANN", 10).map { it.name })
        assertEquals(listOf("Фекла", "Фёкла"), rankContacts(rows, "фек", 10).map { it.name })
    }

    @Test
    fun `совпадение с начала идёт раньше совпадения внутри`() {
        val list = listOf(contact(1, "Анна Иванова"), contact(2, "Иванова Анна"))
        assertEquals(listOf("Иванова Анна", "Анна Иванова"), rankContacts(list, "иванова", 10).map { it.name })
    }

    @Test
    fun `транслит находит латинское написание русского имени`() {
        assertEquals(listOf("Иванов"), rankContacts(rows, "ivanov", 10).map { it.name })
    }

    @Test
    fun `апостроф в латинском написании читается как мягкий знак`() {
        val list = listOf(contact(1, "Обьектив"), contact(2, "Оба"))
        assertEquals(listOf("Обьектив"), rankContacts(list, "ob'ekt", 10).map { it.name })
    }

    @Test
    fun `избранный контакт впереди более точного совпадения`() {
        val list = listOf(contact(1, "Аня"), contact(2, "Аня Петрова"))
        val found = rankContacts(list, "аня", 10, favorites = setOf("lookup-2"))
        assertEquals(listOf(2L, 1L), found.map { it.contactId })
    }

    @Test
    fun `в режиме избранного остаются только избранные`() {
        val found = rankContacts(rows, "", 10, favorites = setOf("lookup-3"), favoritesOnly = true)
        assertEquals(listOf("Иванов"), found.map { it.name })
    }

    @Test
    fun `должность ник заметка и адрес ищутся после названия`() {
        val list = listOf(
            contact(1, "Сидоров", note = "ветеринар"),
            contact(2, "Ветеринарова клиника"),
            contact(3, "Аня", address = "ул. Рязанская, 4"),
            contact(4, "Пётр", organization = "Стройтрест, прораб"),
            contact(5, "Вася", nickname = "прораб"),
        )
        assertEquals(listOf("Ветеринарова клиника", "Сидоров"), rankContacts(list, "ветеринар", 10).map { it.name })
        assertEquals(listOf("Аня"), rankContacts(list, "рязан", 10).map { it.name })
        // Ник совпал с начала, организация — только внутри строки, поэтому Вася раньше.
        assertEquals(listOf("Вася", "Пётр"), rankContacts(list, "прораб", 10).map { it.name })
    }

    @Test
    fun `номер ищется наравне с именем`() {
        val list = listOf(
            contact(1, "Иван", numbers = listOf("79991234567")),
            contact(2, "Пётр", numbers = listOf("78881112233")),
        )
        assertEquals(listOf("Иван"), rankContacts(list, "+7 999 123-45-67", 10).map { it.name })
        // Восьмёрка не мешает: её форма ищет по номеру без кода страны.
        assertEquals(listOf("Иван"), rankContacts(list, "8 (999) 123-45-67", 10).map { it.name })
    }

    @Test
    fun `совпадение с начала номера идёт раньше вхождения`() {
        val list = listOf(
            contact(1, "Хвост", numbers = listOf("71237999123456")),
            contact(2, "Голова", numbers = listOf("79991234567")),
        )
        assertEquals(listOf("Голова", "Хвост"), rankContacts(list, "+7999123456", 10).map { it.name })
    }

    @Test
    fun `короткий набор цифр по номерам не ищет`() {
        val list = listOf(contact(1, "Боб", numbers = listOf("79991231234")))
        assertTrue(rankContacts(list, "12", 10).isEmpty())
        assertEquals(listOf("Боб"), rankContacts(list, "1234", 10).map { it.name })
    }

    @Test
    fun `e-mail тоже поисковое поле`() {
        val list = listOf(contact(1, "Боб", email = "bob@onebar.dev"))
        assertEquals(listOf("Боб"), rankContacts(list, "onebar", 10).map { it.name })
        assertTrue(rankContacts(list, "нет-такого", 10).isEmpty())
    }

    @Test
    fun `непустой поиск берёт до тысячи кандидатов`() {
        assertEquals(1_000, DefaultContactRepository.candidateLimit(20))
        assertEquals(1_000, DefaultContactRepository.candidateLimit(1))
        assertEquals(0, DefaultContactRepository.candidateLimit(0))
        assertEquals(1_500, DefaultContactRepository.candidateLimit(1_500))
    }

    @Test
    fun `тысяча кандидатов ранжируется и ограничивается двадцатью строками`() {
        val list = (1..1_000).map { contact(it.toLong(), "Contact $it") }
        val found = rankContacts(list, "contact", 20)
        assertEquals(20, found.size)
        assertEquals((1L..20L).toList(), found.map { it.contactId })
    }
}
