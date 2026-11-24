package dev.onebar.app.ui

import androidx.lifecycle.viewModelScope
import dev.onebar.app.data.AppRepository
import dev.onebar.app.data.LaunchableApp
import dev.onebar.app.data.rankApps
import dev.onebar.app.testing.FakeApps
import dev.onebar.app.testing.FakeContacts
import dev.onebar.app.testing.FakeFavorites
import dev.onebar.app.testing.FakeHistory
import dev.onebar.app.testing.FakeSuggestions
import dev.onebar.app.testing.app
import dev.onebar.app.testing.contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Отмена корутины не прерывает блокирующий запрос к провайдеру контактов. Здесь поиск
 * действительно идёт на отдельных потоках и блокируется — виртуальное время такую гонку
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
    fun `отменённый поиск не перезаписывает новый и не выполняется одновременно с ним`() = runBlocking {
        val staleStarted = CountDownLatch(1)
        val releaseStale = CountDownLatch(1)
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val contacts = FakeContacts(
            known = listOf(contact(1, "Ann"), contact(2, "Bob")),
            onSearch = { query ->
                val running = active.incrementAndGet()
                maxActive.accumulateAndGet(running, ::maxOf)
                try {
                    if (query == "a") {
                        staleStarted.countDown()
                        check(releaseStale.await(5, TimeUnit.SECONDS)) { "поиск не был освобождён" }
                    }
                } finally {
                    active.decrementAndGet()
                }
            },
        )
        val vm = SearchViewModel(FakeApps(), contacts, FakeSuggestions(), FakeHistory(), FakeFavorites())

        vm.onQueryChange("a")
        assertTrue("первый поиск не стартовал", staleStarted.await(5, TimeUnit.SECONDS))

        vm.onQueryChange("bob")
        releaseStale.countDown()

        withTimeout(5_000) {
            vm.state.first { it.contacts.map { row -> row.entry.name } == listOf("Bob") }
        }
        assertEquals(1, maxActive.get())
    }

    @Test
    fun `обновление после возврата не теряет новое недавнее приложение`() = runBlocking {
        val apps = BlockingRefreshApps(listOf(app("com.a", "A"), app("com.b", "B")))
        val vm = SearchViewModel(apps, FakeContacts(), FakeSuggestions(), FakeHistory(recentApps = listOf("com.a")), FakeFavorites())
        withTimeout(5_000) { vm.state.first { it.apps.map { app -> app.label } == listOf("A") } }

        val children = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()
        apps.blockNext.set(true)
        vm.onScreenResumed()
        val resume = vm.viewModelScope.coroutineContext[Job]!!.children.first { it !in children }
        try {
            assertTrue("обновление кэша не стартовало", apps.refreshStarted.await(5, TimeUnit.SECONDS))
            vm.onAppLaunched(app("com.b", "B"))
            withTimeout(5_000) {
                vm.state.first { it.apps.map { app -> app.packageName } == listOf("com.b", "com.a") }
            }
        } finally {
            apps.releaseRefresh.countDown()
        }
        withTimeout(5_000) { resume.join() }

        assertEquals(listOf("com.b", "com.a"), vm.state.value.apps.map { it.packageName })
    }

    @Test
    fun `устаревший поиск приложений не перезаписывает новое избранное`() = runBlocking {
        val apps = BlockingMatchApps(listOf(app("com.a", "Maps"), app("com.b", "appmaps")))
        val vm = SearchViewModel(apps, FakeContacts(), FakeSuggestions(), FakeHistory(), FakeFavorites())
        try {
            vm.onQueryChange("map")
            withTimeout(5_000) {
                vm.state.first { it.apps.map { app -> app.packageName } == listOf("com.a", "com.b") }
            }
            val children = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()

            apps.blockNext.set(true)
            vm.onQueryChange("map")
            val stale = vm.viewModelScope.coroutineContext[Job]!!.children.first { it !in children }
            assertTrue("первый поиск приложений не стартовал", apps.staleStarted.await(5, TimeUnit.SECONDS))

            vm.onToggleFavoriteApp(app("com.b", "appmaps"))
            assertEquals(listOf("com.b", "com.a"), vm.state.value.apps.map { it.packageName })
            assertTrue("повторный поиск приложений не завершился", apps.freshFinished.await(5, TimeUnit.SECONDS))

            apps.releaseStale.countDown()
            withTimeout(5_000) { stale.join() }

            assertEquals(listOf("com.b", "com.a"), vm.state.value.apps.map { it.packageName })
        } finally {
            apps.releaseStale.countDown()
            vm.viewModelScope.cancel()
        }
    }

    private class BlockingMatchApps(private val known: List<LaunchableApp>) : AppRepository {
        val staleStarted = CountDownLatch(1)
        val freshFinished = CountDownLatch(1)
        val releaseStale = CountDownLatch(1)
        val blockNext = AtomicBoolean(false)

        override fun matching(
            query: String,
            limit: Int,
            favorites: Set<String>,
            favoritesOnly: Boolean,
        ): List<LaunchableApp> {
            if (query == "map" && favorites.isEmpty() && blockNext.compareAndSet(true, false)) {
                staleStarted.countDown()
                check(releaseStale.await(5, TimeUnit.SECONDS)) { "поиск приложений не был освобождён" }
            }
            if (query == "map" && favorites.isNotEmpty()) freshFinished.countDown()
            return rankApps(known, query, limit, favorites, favoritesOnly)
        }

        override fun byPackages(packages: List<String>): List<LaunchableApp> =
            packages.mapNotNull { packageName -> known.firstOrNull { it.packageName == packageName } }

        override fun invalidate() = Unit
    }

    private class BlockingRefreshApps(private val known: List<LaunchableApp>) : AppRepository {
        val blockNext = AtomicBoolean(false)
        val refreshStarted = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)

        override fun matching(query: String, limit: Int, favorites: Set<String>, favoritesOnly: Boolean) =
            rankApps(known, query, limit, favorites, favoritesOnly)

        override fun byPackages(packages: List<String>): List<LaunchableApp> {
            val refreshed = if (blockNext.compareAndSet(true, false)) {
                refreshStarted.countDown()
                check(releaseRefresh.await(5, TimeUnit.SECONDS)) { "обновление кэша не было освобождено" }
                true
            } else false
            return packages.mapNotNull { packageName ->
                known.firstOrNull { it.packageName == packageName }
                    ?.let { if (refreshed) it.copy(label = "${it.label} refreshed") else it }
            }
        }

        override fun invalidate() = Unit
    }
}
