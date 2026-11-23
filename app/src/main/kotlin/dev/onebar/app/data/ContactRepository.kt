package dev.onebar.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import androidx.core.content.ContextCompat

class ContactRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("Range")
    fun search(query: String, limit: Int): List<ContactEntry> {
        if (!hasPermission() || limit <= 0) return emptyList()

        val needle = query.trim()
        val selection = if (needle.isEmpty()) {
            "$HAS_PHONE_ONLY"
        } else {
            "$HAS_PHONE_ONLY AND ${Contacts.DISPLAY_NAME} LIKE ?"
        }
        val args = if (needle.isEmpty()) null else arrayOf("%$needle%")

        val found = ArrayList<Pair<Long, Pair<String, String>>>(limit)
        context.contentResolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.DISPLAY_NAME, Contacts.LOOKUP_KEY),
            selection,
            args,
            STARRED_FIRST_SORT,
        )?.use { cursor ->
            while (cursor.moveToNext() && found.size < limit) {
                found += cursor.getLong(0) to (cursor.getString(1) to cursor.getString(2))
            }
        }

        val rows = found.map { (id, names) ->
            val (number, count) = defaultPhone(id)
            ContactEntry(
                contactId = id,
                name = names.first,
                lookupKey = names.second,
                number = number,
                phoneCount = count,
            )
        }
        val emails = emailsOf(rows.map { it.contactId })
        val profiles = profileRows(rows.map { it.contactId })
        return rows.map {
            it.copy(email = emails[it.contactId], messengers = profiles[it.contactId].orEmpty())
        }
    }

    /**
     * Дефолтным считаем номер, помеченный самим контактом (IS_SUPER_PRIMARY/IS_PRIMARY);
     * если таких пометок нет — мобильный, а иначе первую строку.
     */
    @SuppressLint("Range")
    private fun defaultPhone(contactId: Long): Pair<String?, Int> {
        var marked: String? = null
        var mobile: String? = null
        var first: String? = null
        var count = 0
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.NUMBER, Phone.IS_SUPER_PRIMARY, Phone.IS_PRIMARY, Phone.TYPE),
            "${Phone.CONTACT_ID} = ? AND ${Phone.NUMBER} IS NOT NULL",
            arrayOf(contactId.toString()),
            "${Phone.TYPE} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(0) ?: continue
                count++
                first = first ?: number
                val superPrimary = cursor.getInt(1) == 1
                val primary = cursor.getInt(2) == 1
                when {
                    superPrimary || primary -> marked = marked ?: number
                    cursor.getInt(3) == Phone.TYPE_MOBILE -> mobile = mobile ?: number
                }
            }
        }
        return (marked ?: mobile ?: first) to count
    }

    /**
     * Один запрос адресов на страницу результатов: почта нужна только меню контакта,
     * и только у тех, кого сейчас показываем.
     */
    @SuppressLint("Range")
    private fun emailsOf(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val best = HashMap<Long, Pair<Int, String>>(ids.size)
        context.contentResolver.query(
            Email.CONTENT_URI,
            arrayOf(Email.CONTACT_ID, Email.ADDRESS, Email.IS_SUPER_PRIMARY, Email.IS_PRIMARY, Email.TYPE),
            "${Email.CONTACT_ID} IN (${ids.placeholders()})",
            ids.map { it.toString() }.toTypedArray(),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val rank = when {
                    cursor.getInt(2) == 1 || cursor.getInt(3) == 1 -> 0
                    cursor.getInt(4) == Email.TYPE_HOME -> 1
                    cursor.getInt(4) == Email.TYPE_WORK -> 2
                    else -> 3
                }
                val id = cursor.getLong(0)
                val current = best[id]
                if (current == null || rank < current.first) best[id] = rank to address
            }
        }
        return best.mapValues { (_, ranked) -> ranked.second }
    }

    /**
     * Строки профилей мессенджеров теми же контактами одним запросом: они и признак того,
     * что человек зарегистрирован, и адрес, по которому мессенджер открывает чат.
     */
    @SuppressLint("Range")
    private fun profileRows(ids: List<Long>): Map<Long, Map<String, Long>> {
        val mimes = Messenger.entries.mapNotNull { it.profileMime }
        if (ids.isEmpty() || mimes.isEmpty()) return emptyMap()
        val selection = "${Data.CONTACT_ID} IN (${ids.placeholders()}) AND ${Data.MIMETYPE} IN (${mimes.placeholders()})"
        val args = (ids.map { it.toString() } + mimes).toTypedArray()
        val found = HashMap<Long, MutableMap<String, Long>>(ids.size)
        context.contentResolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.CONTACT_ID, Data.MIMETYPE),
            selection,
            args,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val packageName = Messenger.entries
                    .firstOrNull { it.profileMime == cursor.getString(2) }
                    ?.packageName
                    ?: continue
                found.getOrPut(cursor.getLong(1)) { HashMap() }[packageName] = cursor.getLong(0)
            }
        }
        return found
    }

    private fun List<*>.placeholders(): String = joinToString(separator = ",") { "?" }

    /**
     * Полный контакт по идентификатору: журнал OneBar помнит имя и номер, а почта со
     * строками мессенджеров живут только в провайдере — их запрашивает меню.
     */
    @SuppressLint("Range")
    fun details(contactId: Long): ContactEntry? {
        if (!hasPermission()) return null
        val base = context.contentResolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts.DISPLAY_NAME, Contacts.LOOKUP_KEY),
            "${Contacts._ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val (number, count) = defaultPhone(contactId)
            ContactEntry(
                contactId = contactId,
                name = cursor.getString(0).orEmpty(),
                lookupKey = cursor.getString(1).orEmpty(),
                number = number,
                phoneCount = count,
            )
        } ?: return null
        val ids = listOf(contactId)
        return base.copy(
            email = emailsOf(ids)[contactId],
            messengers = profileRows(ids)[contactId].orEmpty(),
        )
    }

    private companion object {
        const val HAS_PHONE_ONLY = "${Contacts.HAS_PHONE_NUMBER} = 1"

        // Избранные первыми, затем по частоте обращения, затем алфавит.
        const val STARRED_FIRST_SORT =
            "${Contacts.STARRED} DESC, ${Contacts.TIMES_CONTACTED} DESC, ${Contacts.DISPLAY_NAME} ASC"
    }
}
