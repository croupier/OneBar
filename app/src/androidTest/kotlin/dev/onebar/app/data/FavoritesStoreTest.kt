package dev.onebar.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Избранное — тот же доступ к DataStore, что у журнала: свои файлы, чтобы тесты не мешали друг другу. */
@RunWith(AndroidJUnit4::class)
class FavoritesStoreTest {

    private lateinit var file: File
    private lateinit var store: FavoritesStore

    @Before
    fun createEmptyStore() {
        file = newFile("favorites")
        store = favoritesStoreForFile(file)
    }

    @After
    fun removeStoreFile() {
        file.delete()
    }

    @Test
    fun `пустое_избранное_отдаёт_пустые_списки`() = runBlocking {
        assertTrue(store.apps.first().isEmpty())
        assertTrue(store.contacts.first().isEmpty())
    }

    @Test
    fun `переключение_приложения_добавляет_его_и_убирает_повторным_тапом`() = runBlocking {
        store.toggleApp("com.a")
        assertEquals(listOf("com.a"), store.apps.first())

        store.toggleApp("com.a")
        assertTrue(store.apps.first().isEmpty())
    }

    @Test
    fun `порядок_избранных_приложений_—_порядок_добавления`() = runBlocking {
        store.toggleApp("com.b")
        store.toggleApp("com.a")
        store.toggleApp("com.c")

        assertEquals(listOf("com.b", "com.a", "com.c"), store.apps.first())
    }

    @Test
    fun `убранное_из_середины_приложение_не_сдвигает_остальные`() = runBlocking {
        store.toggleApp("com.b")
        store.toggleApp("com.a")
        store.toggleApp("com.c")
        store.toggleApp("com.a")

        assertEquals(listOf("com.b", "com.c"), store.apps.first())
    }

    @Test
    fun `lookupKey_с_запятой_и_кавычкой_возвращается_целиком`() = runBlocking {
        val tricky = """contacts:1,2,"x" """
        store.toggleContact(tricky.trim())

        assertEquals(listOf("contacts:1,2,\"x\""), store.contacts.first())
    }

    @Test
    fun `пустой_lookupKey_в_избранное_не_попадает`() = runBlocking {
        store.toggleContact("")
        store.toggleContact("   ")

        assertTrue(store.contacts.first().isEmpty())
    }

    @Test
    fun `избранные_приложения_и_контакты_хранятся_раздельно`() = runBlocking {
        store.toggleApp("com.a")
        store.toggleContact("lookup-1")

        assertEquals(listOf("com.a"), store.apps.first())
        assertEquals(listOf("lookup-1"), store.contacts.first())
    }

    @Test
    fun `битый_json_контактов_читается_как_пустой_список`() = runBlocking {
        val corrupt = newFile("corrupt-favorites")
        try {
            // Ключ private у DefaultFavoritesStore — имя то же, что в приложении.
            // Обёртка берёт тот же экземпляр DataStore: второй на тот же файл не открываем.
            val raw = PreferenceDataStoreFactory.create(produceFile = { corrupt })
            raw.edit { prefs -> prefs[stringPreferencesKey("contacts")] = "{ это не массив" }

            assertTrue(DefaultFavoritesStore(raw).contacts.first().isEmpty())
        } finally {
            corrupt.delete()
        }
    }

    @Test
    fun `повреждённый_protobuf_отдаёт_пустое_избранное`() = runBlocking {
        val corrupted = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw CorruptionException("test") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                transform(emptyPreferences())
        }
        val recovered = DefaultFavoritesStore(corrupted)

        assertTrue(recovered.apps.first().isEmpty())
        assertTrue(recovered.contacts.first().isEmpty())
    }

    private fun newFile(prefix: String): File =
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "$prefix-${System.nanoTime()}.preferences_pb")
}
