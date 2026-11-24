package dev.onebar.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SuggestRepositoryTest {

    @Test
    fun `сетевой сбой не кэшируется и повтор сразу пробует сеть`() = runBlocking {
        var attempts = 0
        val repository = DefaultSuggestRepository {
            attempts++
            throw IOException("offline")
        }

        repeat(2) {
            try {
                repository.suggest("кофе")
                fail("сетевой сбой должен дойти до вызывающей стороны")
            } catch (_: IOException) {
            }
        }

        assertEquals(2, attempts)
    }

    @Test
    fun `отмена корутины закрывает заблокированное соединение`() = runBlocking {
        val connection = BlockingConnection()
        val repository = DefaultSuggestRepository { connection }
        val request = launch(Dispatchers.IO) { repository.suggest("кофе") }

        assertTrue(connection.started.await(5, TimeUnit.SECONDS))
        withTimeout(5_000) { request.cancelAndJoin() }
        assertTrue(connection.disconnected)
    }

    private class BlockingConnection : HttpURLConnection(URL("http://localhost")) {
        val started = CountDownLatch(1)
        private val release = CountDownLatch(1)
        @Volatile var disconnected = false

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
            release.countDown()
        }

        override fun usingProxy(): Boolean = false

        override fun getInputStream(): InputStream {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "соединение не было закрыто" }
            return ByteArrayInputStream(ByteArray(0))
        }
    }
}
