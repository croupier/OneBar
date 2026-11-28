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
    suspend fun suggest(query: String, language: String): List<String>
}

class DefaultSuggestRepository internal constructor(
    private val openConnection: (String) -> HttpURLConnection,
) : SuggestRepository {

    constructor() : this({ url -> URL(url).openConnection() as HttpURLConnection })

    private val cacheLock = Any()
    private var cachedQuery: String? = null
    private var cachedLanguage: String? = null
    private var cachedResult: List<String> = emptyList()

    override suspend fun suggest(query: String, language: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        synchronized(cacheLock) {
            if (trimmed == cachedQuery && language == cachedLanguage) return cachedResult
        }

        val result = fetch(trimmed, language)
        synchronized(cacheLock) {
            cachedQuery = trimmed
            cachedLanguage = language
            cachedResult = result
        }
        return result
    }

    private suspend fun fetch(query: String, language: String): List<String> {
        val url = "$ENDPOINT&q=" + URLEncoder.encode(query, "UTF-8") +
            "&hl=" + URLEncoder.encode(language, "UTF-8")
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
                        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                            val builder = StringBuilder()
                            var total = 0
                            while (total <= MAX_RESPONSE_CHARS) {
                                val buffer = CharArray(minOf(4_096, MAX_RESPONSE_CHARS + 1 - total))
                                val count = reader.read(buffer)
                                if (count < 0) break
                                builder.append(buffer, 0, count)
                                total += count
                            }
                            if (total > MAX_RESPONSE_CHARS) null else builder.toString()
                        }
                        ensureActive()
                        val parsed = body?.let { JSONArray(it).optJSONArray(1) } ?: return@withContext emptyList()
                        (0 until minOf(parsed.length(), MAX_SUGGESTIONS)).map { parsed.getString(it) }
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
        const val ENDPOINT = "https://suggestqueries.google.com/complete/search?client=firefox"
        const val TIMEOUT_MS = 1_500
        const val MAX_RESPONSE_CHARS = 64 * 1024
        const val MAX_SUGGESTIONS = 10
    }
}
