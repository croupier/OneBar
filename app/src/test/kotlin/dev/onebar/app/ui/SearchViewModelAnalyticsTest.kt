package dev.onebar.app.ui

import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.testing.FakeApps
import dev.onebar.app.testing.FakeContacts
import dev.onebar.app.testing.FakeFavorites
import dev.onebar.app.testing.FakeHistory
import dev.onebar.app.testing.FakeSuggestions
import dev.onebar.app.testing.MainDispatcherRule
import dev.onebar.app.testing.RecordingAnalytics
import dev.onebar.app.testing.app
import dev.onebar.app.testing.contact
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelAnalyticsTest {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `аналитика получает только обобщённые имена событий`() = runTest(main.dispatcher) {
        val analytics = RecordingAnalytics()
        val launchable = app("com.private.package", "Private App")
        val contact = contact(1, "Private Name", number = "+70001234567")
        val viewModel = SearchViewModel(
            apps = FakeApps(known = listOf(launchable)),
            contacts = FakeContacts(known = listOf(contact)),
            suggestions = FakeSuggestions(),
            history = FakeHistory(),
            favoritesStore = FakeFavorites(),
            io = main.dispatcher,
            analytics = analytics,
        )
        advanceUntilIdle()

        viewModel.onQuerySubmitted("private query")
        viewModel.onAppLaunched(launchable)
        viewModel.onCommunication(CommunicationKind.SMS, contact)
        viewModel.onWebSearch(WebEngine.YANDEX)
        advanceUntilIdle()

        assertEquals(
            listOf("app_open", "search_submitted", "app_launched", "contact_sms", "web_search_yandex"),
            analytics.events,
        )
    }
}
