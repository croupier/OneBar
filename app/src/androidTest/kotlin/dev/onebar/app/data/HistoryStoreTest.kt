package dev.onebar.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Журнал проверяется на настоящем DataStore: кодирование через org.json и JSON-массивы
 * на JVM не посмотреть. Каждый тест берёт свой файл, чтобы не зависеть ни от состояния
 * телефона, ни друг от друга.
 */
@RunWith(AndroidJUnit4::class)
class HistoryStoreTest {

    private lateinit var file: File
    private lateinit var store: HistoryStore

    @Before
    fun createEmptyStore() {
        file = newFile("history")
        store = historyStoreForFile(file)
    }

    @After
    fun removeStoreFile() {
        file.delete()
    }

    @Test
    fun `пустой_журнал_отдаёт_пустые_списки`() = runBlocking {
        assertTrue(store.recentApps.first().isEmpty())
        assertTrue(store.communications.first().isEmpty())
        assertTrue(store.queries.first().isEmpty())
        assertTrue(store.saveHistory.first())
    }

    @Test
    fun `недавние_приложения_идут_от_новых_к_старым_и_без_дублей`() = runBlocking {
        store.pushRecentApp("com.a")
        store.pushRecentApp("com.b")
        store.pushRecentApp("com.a")

        assertEquals(listOf("com.a", "com.b"), store.recentApps.first())
    }

    @Test
    fun `недавних_приложений_храним_не_больше_двенадцати`() = runBlocking {
        (1..15).forEach { store.pushRecentApp("com.pkg$it") }

        val recent = store.recentApps.first()
        assertEquals(12, recent.size)
        assertEquals("com.pkg15", recent.first())
    }

    @Test
    fun `запись_коммуникации_помнит_тип__номер_и_время`() = runBlocking {
        val entry = CommunicationEntry(CommunicationKind.SMS, 7, "lookup-7", "Ann", "+7 000", 1_700_000_000_000)
        store.pushCommunication(entry)

        assertEquals(listOf(entry), store.communications.first())
    }

    @Test
    fun `повторная_коммуникация_того_же_типа_заменяет_прежнюю`() = runBlocking {
        store.pushCommunication(entry(7, CommunicationKind.CALL, 1L))
        store.pushCommunication(entry(7, CommunicationKind.CALL, 2L))

        val kept = store.communications.first().single()
        assertEquals(2L, kept.timestamp)
    }

    @Test
    fun `звонок_и_смс_одного_контакта_живут_рядом`() = runBlocking {
        store.pushCommunication(entry(7, CommunicationKind.CALL, 1L))
        store.pushCommunication(entry(7, CommunicationKind.SMS, 2L))

        assertEquals(2, store.communications.first().size)
    }

    @Test
    fun `удаление_убирает_все_записи_контакта`() = runBlocking {
        store.pushCommunication(entry(7, CommunicationKind.CALL, 1L))
        store.pushCommunication(entry(7, CommunicationKind.SMS, 2L))
        store.pushCommunication(entry(8, CommunicationKind.CALL, 3L))

        store.removeCommunications(7)

        assertEquals(listOf(8L), store.communications.first().map { it.contactId })
    }

    @Test
    fun `журнал_коммуникаций_не_длиннее_двадцати_записей`() = runBlocking {
        (1..25).forEach { store.pushCommunication(entry(it.toLong(), CommunicationKind.CALL, it.toLong())) }

        val kept = store.communications.first()
        assertEquals(20, kept.size)
        assertEquals(25L, kept.first().contactId)
    }

    @Test
    fun `история_запросов_идёт_от_новых_к_старым_без_повторов`() = runBlocking {
        store.pushQuery("кофе")
        store.pushQuery("пицца")
        store.pushQuery("кофе")

        assertEquals(listOf("кофе", "пицца"), store.queries.first())
    }

    @Test
    fun `запрос_с_запятой_и_кавычкой_возвращается_целиком`() = runBlocking {
        val tricky = """кофе, "раф", 200"""
        store.pushQuery(tricky)

        assertEquals(listOf(tricky), store.queries.first())
    }

    @Test
    fun `пробелы_по_краям_запроса_отсекаются__пустой_не_пишется`() = runBlocking {
        store.pushQuery("  молоко  ")
        store.pushQuery("   ")

        assertEquals(listOf("молоко"), store.queries.first())
    }

    @Test
    fun `история_запросов_хранит_не_больше_двадцати`() = runBlocking {
        (1..25).forEach { store.pushQuery("запрос $it") }

        val kept = store.queries.first()
        assertEquals(20, kept.size)
        assertEquals("запрос 25", kept.first())
    }

    @Test
    fun `выключенная_история_скрывает_старое_и_не_пишет_новое`() = runBlocking {
        store.pushQuery("кофе")
        store.setSaveHistory(false)
        store.pushQuery("пицца")

        assertFalse(store.saveHistory.first())
        assertTrue(store.queries.first().isEmpty())

        store.setSaveHistory(true)
        assertEquals(listOf("кофе"), store.queries.first())
    }

    @Test
    fun `очистка_удаляет_только_поисковые_запросы`() = runBlocking {
        val communication = entry(7, CommunicationKind.CALL, 1L)
        store.pushRecentApp("com.a")
        store.pushCommunication(communication)
        store.pushQuery("кофе")
        store.setSaveHistory(false)

        store.clearQueries()
        store.setSaveHistory(true)

        assertTrue(store.queries.first().isEmpty())
        assertEquals(listOf("com.a"), store.recentApps.first())
        assertEquals(listOf(communication), store.communications.first())
        assertTrue(store.saveHistory.first())
    }

    @Test
    fun `битый_json_читается_как_пустой_журнал`() = runBlocking {
        val corrupt = newFile("corrupt-history")
        try {
            // Ключи private у DefaultHistoryStore — здесь те же имена, что хранит приложение.
            // Обёртка берёт тот же экземпляр DataStore: второй на тот же файл DataStore не открывается.
            val raw = PreferenceDataStoreFactory.create(produceFile = { corrupt })
            raw.edit { prefs ->
                prefs[stringPreferencesKey("communications")] = "{ это не массив"
                prefs[stringPreferencesKey("queries")] = "[] лишнее"
            }

            val reopened = DefaultHistoryStore(raw)
            assertTrue(reopened.communications.first().isEmpty())
            assertTrue(reopened.queries.first().isEmpty())
        } finally {
            corrupt.delete()
        }
    }

    private fun entry(id: Long, kind: CommunicationKind, timestamp: Long) = CommunicationEntry(
        kind = kind,
        contactId = id,
        lookupKey = "lookup-$id",
        name = "Contact $id",
        number = "+7000000$id",
        timestamp = timestamp,
    )

    private fun newFile(prefix: String): File =
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "$prefix-${System.nanoTime()}.preferences_pb")
}
