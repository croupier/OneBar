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
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Data
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactRepositoryPhoneTest {

    @Test
    fun пакетная_загрузка_номеров_использует_contact_id() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = ContactProvider()
        val resolver = ContentResolver.wrap(provider)
        val context = ProviderContext(base, resolver)

        val details = requireNotNull(DefaultContactRepository(context).details(CONTACT_ID))

        assertTrue(provider.phoneProjection.orEmpty().contains(Phone.CONTACT_ID))
        assertEquals(PRIMARY_NUMBER, details.number)
        assertEquals(2, details.phoneCount)
    }

    private class ContactProvider : ContentProvider() {
        var phoneProjection: List<String>? = null

        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String? = null

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ) = MatrixCursor(projection.orEmpty()).apply {
            when (uri) {
                ContactsContract.Contacts.CONTENT_URI -> addRow(
                    arrayOf<Any?>("Ann", "lookup-$CONTACT_ID", null),
                )
                Phone.CONTENT_URI -> {
                    phoneProjection = projection?.toList()
                    addRow(arrayOf<Any>(CONTACT_ID, MOBILE_NUMBER, 0, 0, Phone.TYPE_MOBILE))
                    addRow(arrayOf<Any>(CONTACT_ID, PRIMARY_NUMBER, 0, 1, Phone.TYPE_WORK))
                }
                Email.CONTENT_URI, Data.CONTENT_URI -> Unit
            }
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
        const val CONTACT_ID = 42L
        const val MOBILE_NUMBER = "+7 999 123-45-67"
        const val PRIMARY_NUMBER = "+7 495 123-45-67"
    }
}
