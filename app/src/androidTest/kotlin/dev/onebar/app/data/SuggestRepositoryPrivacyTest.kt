package dev.onebar.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuggestRepositoryPrivacyTest {

    @Test
    fun `locale_передаётся_в_endpoint_а_ответ_ограничен`() = runBlocking {
        val suggestions = (1..12).map { "suggestion-$it" }
        val body = "[\"\",${suggestions.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}]"
        var opened: JsonConnection? = null
        val repository = DefaultSuggestRepository { url ->
            JsonConnection(body, URL(url)).also { opened = it }
        }

        val result = repository.suggest("кофе", "en")
        assertTrue(opened!!.requestUrl.toString().contains("hl=en"))
        assertEquals(suggestions.take(10), result)
    }

    @Test
    fun `кэш_различает_язык`() = runBlocking {
        var attempts = 0
        val repository = DefaultSuggestRepository {
            attempts++
            JsonConnection("[\"\",[\"maps\"]]")
        }

        repository.suggest("map", "ru")
        repository.suggest("map", "en")
        repository.suggest("map", "en")

        assertEquals(2, attempts)
    }

    @Test
    fun `слишком_большой_ответ_отбрасывается`() = runBlocking {
        val connection = JsonConnection("[\"\",[\"maps\"]]" + " ".repeat(70_000))
        val repository = DefaultSuggestRepository { connection }

        assertTrue(repository.suggest("map", "ru").isEmpty())
    }

    private class JsonConnection(
        private val body: String,
        url: URL = URL("https://example.test"),
    ) : HttpURLConnection(url) {
        val requestUrl: URL get() = getURL()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }
}
