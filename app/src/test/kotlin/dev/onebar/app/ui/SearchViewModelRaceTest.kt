package dev.onebar.app.ui

import dev.onebar.app.testing.FakeApps
import dev.onebar.app.testing.FakeContacts
import dev.onebar.app.testing.FakeFavorites
import dev.onebar.app.testing.FakeHistory
import dev.onebar.app.testing.FakeSuggestions
import dev.onebar.app.testing.contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Отмена корутины не прерывает блокирующий запрос к провайдеру контактов, поэтому
 * старый результат может вернуться позже нового. Здесь поиск действительно идёт
 * на отдельных потоках и действительно блокируется — виртуальное время такую гонку
 * не моделирует.
 */
class SearchViewModelRaceTest {

    @Before
    fun bindMainToRealDispatcher() {
        Dispatchers.setMain(Dispatchers.IO)
    }

    @After
    fun releaseMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `отменённый поиск контактов не перезаписывает новый результат`() = runBlocking {
        val staleStarted = CountDownLatch(1)
        val releaseStale = CountDownLatch(1)
        val staleFinished = CountDownLatch(1)
        val contacts = FakeContacts(
            known = listOf(contact(1, "Ann"), contact(2, "Bob")),
            onSearch = { query ->
                if (query == "a") {
                    staleStarted.countDown()
                    releaseStale.await()
                    staleFinished.countDown()
                }
            },
        )
        val vm = SearchViewModel(FakeApps(), contacts, FakeSuggestions(), FakeHistory(), FakeFavorites())

        vm.onQueryChange("a")
        check(staleStarted.await(5, TimeUnit.SECONDS)) { "первый поиск не стартовал" }

        vm.onQueryChange("bob")
        releaseStale.countDown()
        check(staleFinished.await(5, TimeUnit.SECONDS)) { "первый поиск не завершился" }

        // Ждём, пока отменённый поиск успеет дойти до записи состояния: без защиты он перезаписал бы список.
        Thread.sleep(200)
        val names = vm.state.value.contacts.map { it.entry.name }
        if (names != listOf("Bob")) fail("устаревший результат перезаписал новый: $names")
        assertEquals(listOf("Bob"), names)
    }
}
