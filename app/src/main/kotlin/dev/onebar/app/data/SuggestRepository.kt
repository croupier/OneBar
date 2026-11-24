package dev.onebar.app.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Веб-подсказки; отдельный интерфейс, чтобы ViewModel тестировалась без сети. */
interface SuggestRepository {
    suspend fun suggest(query: String): List<String>
}

class DefaultSuggestRepository internal constructor(
    private val openConnection: (String) -> HttpURLConnection,
) : SuggestRepository {

    constructor() : this({ url -> URL(url).openConnection() as HttpURLConnection })

    private val cacheLock = Any()
    private var cachedQuery: String? = null
    private var cachedResult: List<String> = emptyList()

    override suspend fun suggest(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        synchronized(cacheLock) {
            if (trimmed == cachedQuery) return cachedResult
        }

        val result = fetch(trimmed)
        synchronized(cacheLock) {
            cachedQuery = trimmed
            cachedResult = result
        }
        return result
    }

    private suspend fun fetch(query: String): List<String> {
        val url = "$ENDPOINT&q=" + URLEncoder.encode(query, "UTF-8")
        val connection = openConnection(url).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        return try {
            coroutineScope {
                // disconnect() закрывает сокет и будит блокирующее чтение при отмене корутины.
                val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        connection.disconnect()
                    }
                }
                try {
                    withContext(Dispatchers.IO) {
                        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        ensureActive()
                        val options = JSONArray(body).optJSONArray(1) ?: return@withContext emptyList()
                        (0 until options.length()).map { options.getString(it) }
                    }
                } finally {
                    cancellation.cancel()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ENDPOINT = "https://suggestqueries.google.com/complete/search?client=firefox&hl=ru"
        const val TIMEOUT_MS = 1_500
    }
}
