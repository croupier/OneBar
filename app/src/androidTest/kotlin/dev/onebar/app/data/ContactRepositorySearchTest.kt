package dev.onebar.app.data

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Data
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactRepositorySearchTest {

    @Test
    fun `должность_находится_даже_если_название_организации_не_совпадает`() {
        val fixture = fixture(
            contacts = listOf(TestContact(1, "Ann")),
            data = listOf(
                TestData(1, Organization.CONTENT_ITEM_TYPE, "Romashka", "Accountant"),
                TestData(1, Email.CONTENT_ITEM_TYPE, "ann@example.com"),
            ),
        )

        val found = fixture.repository.search("accountant", LIMIT)

        assertEquals(listOf(1L), found.map { it.contactId })
        assertEquals("Romashka Accountant", found.single().organization)
        assertTrue(fixture.provider.dataSelections.any { Data.DATA2 in it })
        assertEquals(0, fixture.provider.emailQueries)
        assertEquals(0, fixture.provider.profileQueries)

        val recent = fixture.repository.byLookupKeys(listOf("lookup-1"))
        assertEquals(listOf(1L), recent.map { it.contactId })
        assertEquals(0, fixture.provider.emailQueries)
        assertEquals(0, fixture.provider.profileQueries)

        assertEquals("ann@example.com", fixture.repository.details(1)?.email)
        assertEquals(1, fixture.provider.emailQueries)
        assertEquals(1, fixture.provider.profileQueries)
    }

    @Test
    fun `избранное_из_доп_поля_не_выпадает_когда_имена_заполнили_лимит`() {
        val fixture = mapsFixture(nameMatches = 20)

        val found = fixture.repository.search(
            "maps",
            LIMIT,
            favorites = setOf("lookup-$FAVORITE_ID"),
        )

        assertEquals(listOf(FAVORITE_ID) + (1L..19L).toList(), found.map { it.contactId })
    }

    @Test
    fun `избранное_из_доп_поля_идёт_перед_совпадением_по_имени`() {
        val fixture = mapsFixture(nameMatches = 1)

        val found = fixture.repository.search(
            "maps",
            LIMIT,
            favorites = setOf("lookup-$FAVORITE_ID"),
        )

        assertEquals(listOf(FAVORITE_ID, 1L), found.map { it.contactId })
    }

    @Test
    fun `в_избранном_контакт_из_доп_поля_тоже_находится`() {
        val fixture = mapsFixture(nameMatches = 1)

        val found = fixture.repository.search(
            "maps",
            LIMIT,
            favorites = setOf("lookup-$FAVORITE_ID"),
            favoritesOnly = true,
        )

        assertEquals(listOf(FAVORITE_ID), found.map { it.contactId })
    }

    private fun mapsFixture(nameMatches: Int): Fixture = fixture(
        contacts = (1..nameMatches).map { TestContact(it.toLong(), "Maps ${it.toString().padStart(2, '0')}") } +
            TestContact(FAVORITE_ID, "Navigator"),
        data = listOf(TestData(FAVORITE_ID, Organization.CONTENT_ITEM_TYPE, "Maps team")),
    )

    private fun fixture(contacts: List<TestContact>, data: List<TestData> = emptyList()): Fixture {
        val provider = ContactProvider(contacts, data)
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = ContentResolver.wrap(provider)
        return Fixture(DefaultContactRepository(ProviderContext(base, resolver)), provider)
    }

    private data class Fixture(
        val repository: DefaultContactRepository,
        val provider: ContactProvider,
    )

    private data class TestContact(
        val id: Long,
        val name: String,
        val lookupKey: String = "lookup-$id",
    )

    private data class TestData(
        val contactId: Long,
        val mime: String,
        val data1: String?,
        val data2: String? = null,
    )

    private class ContactProvider(
        private val contacts: List<TestContact>,
        private val data: List<TestData>,
    ) : ContentProvider() {
        var emailQueries = 0
        var profileQueries = 0
        val dataSelections = mutableListOf<String>()

        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String? = null

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): MatrixCursor {
            val cursor = MatrixCursor(projection.orEmpty())
            val args = selectionArgs?.toList().orEmpty()
            when (uri) {
                ContactsContract.Contacts.CONTENT_URI -> {
                    val selected = when {
                        selection.orEmpty().contains(Contacts.DISPLAY_NAME) -> contacts.filter { contact ->
                            args.likeNeedles().any { contact.name.contains(it, ignoreCase = true) }
                        }
                        selection.orEmpty().contains("${Contacts._ID} IN") -> contacts.filter { contact ->
                            contact.id.toString() in args
                        }
                        else -> contacts
                    }
                    selected.forEach { contact ->
                        val row = arrayOfNulls<Any>(projection.orEmpty().size)
                        projection.orEmpty().forEachIndexed { index, column ->
                            row[index] = when (column) {
                                Contacts._ID -> contact.id
                                Contacts.DISPLAY_NAME -> contact.name
                                Contacts.LOOKUP_KEY -> contact.lookupKey
                                Contacts.STARRED, Contacts.TIMES_CONTACTED -> 0
                                else -> null
                            }
                        }
                        cursor.addRow(row)
                    }
                }
                Phone.CONTENT_URI -> {
                    if (projection.orEmpty().contains(Phone.NORMALIZED_NUMBER)) return cursor
                    val ids = args.mapNotNull { it.toLongOrNull() }.toSet()
                    contacts.filter { it.id in ids }.forEach { contact ->
                        cursor.addRow(arrayOf<Any>(contact.id, "+7 999 000-00-00", 0, 0, Phone.TYPE_MOBILE))
                    }
                }
                Email.CONTENT_URI -> {
                    emailQueries++
                    val ids = args.mapNotNull { it.toLongOrNull() }.toSet()
                    contacts.filter { it.id in ids }.forEach { contact ->
                        cursor.addRow(arrayOf<Any>(contact.id, "ann@example.com", 0, 0, Email.TYPE_WORK))
                    }
                }
                Data.CONTENT_URI -> {
                    if (projection.orEmpty().contains(Data.DATA1)) {
                        dataSelections += selection.orEmpty()
                        val mimes = args.filterNot { it.isLikePattern() }.toSet()
                        val needles = args.likeNeedles()
                        val matchesRole = selection.orEmpty().contains(Data.DATA2)
                        data.filter { row ->
                            row.mime in mimes && (
                                row.data1.orEmpty().containsAny(needles) ||
                                    matchesRole && row.mime == Organization.CONTENT_ITEM_TYPE &&
                                    row.data2.orEmpty().containsAny(needles)
                                )
                        }.forEach { row ->
                            cursor.addRow(arrayOf<Any?>(row.contactId, row.mime, row.data1, row.data2))
                        }
                    } else {
                        profileQueries++
                    }
                }
            }
            return cursor
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private class ProviderContext(base: Context, private val resolver: ContentResolver) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getContentResolver(): ContentResolver = resolver

        override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED

        override fun checkPermission(permission: String, pid: Int, uid: Int): Int = PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val LIMIT = 20
        const val FAVORITE_ID = 100L

        fun List<String>.likeNeedles(): List<String> =
            filter { it.isLikePattern() }.map { it.removeSurrounding("%") }.distinct()

        fun String.isLikePattern(): Boolean = startsWith("%") && endsWith("%")

        fun String.containsAny(needles: List<String>): Boolean =
            needles.any { contains(it, ignoreCase = true) }
    }
}
