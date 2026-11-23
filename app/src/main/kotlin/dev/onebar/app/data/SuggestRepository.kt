package dev.onebar.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Веб-подсказки; отдельный интерфейс, чтобы ViewModel тестировалась без сети. */
interface SuggestRepository {
    suspend fun suggest(query: String): List<String>
}

class DefaultSuggestRepository : SuggestRepository {

    private var cachedQuery: String? = null
    private var cachedResult: List<String> = emptyList()

    override suspend fun suggest(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed == cachedQuery) return cachedResult

        val result = withContext(Dispatchers.IO) { fetch(trimmed) }
        cachedQuery = trimmed
        cachedResult = result
        return result
    }

    private fun fetch(query: String): List<String> = runCatching {
        val url = "$ENDPOINT&q=" + URLEncoder.encode(query, "UTF-8")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        try {
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val options = JSONArray(body).optJSONArray(1) ?: return@runCatching emptyList()
            (0 until options.length()).map { options.getString(it) }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val ENDPOINT = "https://suggestqueries.google.com/complete/search?client=firefox&hl=ru"
        const val TIMEOUT_MS = 1_500
    }
}
