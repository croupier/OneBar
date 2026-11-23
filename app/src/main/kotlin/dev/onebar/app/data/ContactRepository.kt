package dev.onebar.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import androidx.core.content.ContextCompat

/**
 * Доступ к провайдеру контактов. Интерфейс позволяет проверить логику ViewModel на JVM,
 * не поднимая ContentResolver.
 */
interface ContactRepository {
    fun hasPermission(): Boolean

    fun search(
        query: String,
        limit: Int,
        favorites: Set<String> = emptySet(),
        favoritesOnly: Boolean = false,
    ): List<ContactEntry>

    fun byLookupKeys(keys: List<String>): List<ContactEntry>

    fun details(contactId: Long): ContactEntry?
}

/**
 * Порядок контактов — те же правила, что у приложений: избранные впереди, затем название и
 * все номера контакта как равнозначные поля (поиск по номеру равен поиску по имени) по вариантам
 * запроса — с начала важнее внутри, затем доп. поля: организация с должностью, ник, заметка,
 * адрес, e-mail. Равные ключи сохраняют порядок провайдера, то есть избранность в телефоне и
 * частоту общения.
 */
internal fun rankContacts(
    all: List<ContactEntry>,
    query: String,
    limit: Int,
    favorites: Set<String> = emptySet(),
    favoritesOnly: Boolean = false,
): List<ContactEntry> {
    if (query.isBlank()) {
        return all.asSequence()
            .filter { !favoritesOnly || it.lookupKey in favorites }
            .take(limit)
            .toList()
    }
    val needles = buildNeedles(query)
    // Номера участвуют в сравнении только для настоящего номера: «12» нашло бы половину
    // телефонной книги, а набирать её ради этого никто не станет.
    val withNumbers = phoneForms(query).isNotEmpty()
    val scored = ArrayList<Pair<Rank, ContactEntry>>(all.size)
    for (entry in all) {
        val favorite = entry.lookupKey in favorites
        if (favoritesOnly && !favorite) continue
        val rank = rankOf(needles, contactFields(entry, withNumbers), favorite) ?: continue
        scored += rank to entry
    }
    return scored.sortedBy { it.first }.map { it.second }.take(limit)
}

private fun contactFields(entry: ContactEntry, withNumbers: Boolean): List<List<String>> = listOf(
    listOf(entry.name.lowercase()) + if (withNumbers) entry.searchableNumbers else emptyList(),
    listOf(entry.organization, entry.nickname, entry.note, entry.address, entry.email.orEmpty())
        .map { it.lowercase() },
)

// Скобки обязательны: иначе OR в следующей LIKE-маске отменит фильтр перед всей группой.
internal fun likeClause(patterns: List<String>, column: String): String =
    patterns.joinToString(separator = " OR ", prefix = "(", postfix = ")") { "$column LIKE ?$LIKE_ESCAPE" }

private const val LIKE_ESCAPE = " ESCAPE '\\'"

private data class BaseContact(
    val contactId: Long,
    val name: String,
    val lookupKey: String,
    val photoUri: String?,
)

class DefaultContactRepository(private val context: Context) : ContactRepository {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("Range")
    override fun search(
        query: String,
        limit: Int,
        favorites: Set<String>,
        favoritesOnly: Boolean,
    ): List<ContactEntry> {
        if (!hasPermission() || limit <= 0) return emptyList()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            val rows = baseRows(HAS_PHONE_ONLY, null, limit).map(::entryOf)
            return enrich(rankContacts(rows, "", limit, favorites, favoritesOnly))
        }

        // LIKE у провайдера — только грубый фильтр: регистр он складывает по ASCII, а разницу
        // е/ё глушим маской «любой символ». Точность и порядок решает ранжировщик в памяти,
        // поэтому кандидатов берём с запасом, иначе лимит отрезал бы лучшие совпадения.
        val patterns = likePatterns(trimmed).map { "%$it%" }
        val byName = baseRows(
            "$HAS_PHONE_ONLY AND ${likeClause(patterns, Contacts.DISPLAY_NAME)}",
            patterns.toTypedArray(),
            limit * CANDIDATE_FACTOR,
        ).map(::entryOf)
        val candidates = withPhoneNumbers(byName, trimmed, limit * CANDIDATE_FACTOR)

        val ranked = rankContacts(candidates, trimmed, limit, favorites, favoritesOnly).toMutableList()
        if (!favoritesOnly && ranked.size < limit) {
            val extra = fieldRows(patterns, ranked.map { it.contactId }.toSet(), limit * CANDIDATE_FACTOR)
            ranked += rankContacts(extra, trimmed, limit - ranked.size, favorites, favoritesOnly)
        }
        return enrich(withNumbers(ranked))
    }

    /** id, название, lookup-ключ и фото нужны и ранжировщику, и строке результата. */
    @SuppressLint("Range")
    private fun baseRows(selection: String, args: Array<String>?, cap: Int): List<BaseContact> {
        val found = ArrayList<BaseContact>(cap)
        context.contentResolver.query(
            Contacts.CONTENT_URI,
            arrayOf(
                Contacts._ID,
                Contacts.DISPLAY_NAME,
                Contacts.LOOKUP_KEY,
                Contacts.PHOTO_URI,
            ),
            selection,
            args,
            STARRED_FIRST_SORT,
        )?.use { cursor ->
            while (cursor.moveToNext() && found.size < cap) {
                found += BaseContact(
                    contactId = cursor.getLong(0),
                    name = cursor.getString(1).orEmpty(),
                    lookupKey = cursor.getString(2).orEmpty(),
                    photoUri = cursor.getString(3)?.takeIf { it.isNotBlank() },
                )
            }
        }
        return found
    }

    private fun entryOf(row: BaseContact) = ContactEntry(
        contactId = row.contactId,
        lookupKey = row.lookupKey,
        name = row.name,
        number = null,
        photoUri = row.photoUri,
    )

    /**
     * Поиск по номеру равнозначен поиску по имени, поэтому номера нужны до ранжирования:
     * отдельный запрос к Phone по маскам «цифры через %» — в телефоне номер хранится с
     * пробелами и дефисами, а SQLite их не понимает. NORMALIZED_NUMBER (E.164 от провайдера)
     * берём, когда он есть: там «8» уже превращена в «+7».
     */
    @SuppressLint("Range")
    private fun withPhoneNumbers(rows: List<ContactEntry>, query: String, cap: Int): List<ContactEntry> {
        val patterns = phoneLikePatterns(query)
        if (patterns.isEmpty()) return rows
        val selection = "${likeClause(patterns, Phone.NUMBER)} OR ${likeClause(patterns, Phone.NORMALIZED_NUMBER)}"
        val numbers = HashMap<Long, MutableList<String>>(cap)
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NORMALIZED_NUMBER, Phone.NUMBER),
            selection,
            (patterns + patterns).toTypedArray(),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext() && numbers.size < cap) {
                val stored = cursor.getString(1) ?: cursor.getString(2) ?: continue
                val digits = numberDigits(stored).trimStart('+')
                if (digits.isEmpty()) continue
                numbers.getOrPut(cursor.getLong(0)) { mutableListOf() } += digits
            }
        }
        val ids = numbers.keys.toList()
        if (ids.isEmpty()) return rows
        val selectionById = "${Contacts._ID} IN (${ids.placeholders()}) AND $HAS_PHONE_ONLY"
        val byPhone = baseRows(selectionById, ids.map { it.toString() }.toTypedArray(), cap)
            .map { row -> entryOf(row).copy(searchableNumbers = numbers[row.contactId].orEmpty()) }
        // Строка из телефонного прохода уже содержит название, поэтому просто переставляет
        // найденную по имени запись той же строкой, но с номерами.
        return (rows + byPhone).associateBy { it.contactId }.values.toList()
    }

    /** defaultPhone — отдельный запрос на контакт, поэтому номера догружаем лишь показанным строкам. */
    private fun withNumbers(rows: List<ContactEntry>): List<ContactEntry> = rows.map { row ->
        val (number, count) = defaultPhone(row.contactId)
        row.copy(number = number, phoneCount = count)
    }

    /**
     * Совпадения в доп. полях — одним запросом к Data: в DATA1 лежат и e-mail, и организация,
     * и ник, и заметка, и адрес целиком; у Organization в DATA2 должность, её добавляем к названию
     * компании, чтобы «разработчик» нашёл человека по строке «Иванов, разработчик».
     */
    @SuppressLint("Range")
    private fun fieldRows(patterns: List<String>, exclude: Set<Long>, cap: Int): List<ContactEntry> {
        val mimes = EXTRA_FIELD_MIMES
        val selection = "${Data.MIMETYPE} IN (${mimes.placeholders()}) AND ${likeClause(patterns, Data.DATA1)}"
        val args = (mimes + patterns).toTypedArray()
        val values = HashMap<Long, MutableMap<String, MutableList<String>>>(cap)
        context.contentResolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2),
            selection,
            args,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext() && values.size < cap) {
                val id = cursor.getLong(0)
                if (id in exclude) continue
                val mime = cursor.getString(1)
                val role = if (mime == Organization.CONTENT_ITEM_TYPE) cursor.getString(3).orEmpty() else ""
                val value = listOf(cursor.getString(2).orEmpty(), role).filter { it.isNotBlank() }.joinToString(" ")
                if (value.isBlank()) continue
                values.getOrPut(id) { HashMap() }.getOrPut(mime) { mutableListOf() } += value
            }
        }
        val ids = values.keys.toList()
        if (ids.isEmpty()) return emptyList()
        val baseSelection = "${Contacts._ID} IN (${ids.placeholders()}) AND $HAS_PHONE_ONLY"
        return baseRows(baseSelection, ids.map { it.toString() }.toTypedArray(), cap).map { row ->
            val found = values[row.contactId].orEmpty()
            entryOf(row).copy(
                organization = found[Organization.CONTENT_ITEM_TYPE].joined(),
                nickname = found[Nickname.CONTENT_ITEM_TYPE].joined(),
                note = found[NOTE_MIME].joined(),
                address = found[StructuredPostal.CONTENT_ITEM_TYPE].joined(),
                email = found[Email.CONTENT_ITEM_TYPE].joined(),
            )
        }
    }

    private fun List<String>?.joined(): String = this?.joinToString(" ").orEmpty()

    /**
     * Избранные по lookup-ключам: порядок тот же, что в списке избранного, поэтому
     * сортировка провайдера контактов здесь не подходит.
     */
    @SuppressLint("Range")
    override fun byLookupKeys(keys: List<String>): List<ContactEntry> {
        if (!hasPermission() || keys.isEmpty()) return emptyList()
        val selection = "${Contacts.LOOKUP_KEY} IN (${keys.placeholders()}) AND $HAS_PHONE_ONLY"
        val found = HashMap<String, BaseContact>(keys.size)
        context.contentResolver.query(
            Contacts.CONTENT_URI,
            arrayOf(
                Contacts._ID,
                Contacts.DISPLAY_NAME,
                Contacts.LOOKUP_KEY,
                Contacts.PHOTO_URI,
            ),
            selection,
            keys.toTypedArray(),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(2).orEmpty()
                found[key] = BaseContact(
                    contactId = cursor.getLong(0),
                    name = cursor.getString(1).orEmpty(),
                    lookupKey = key,
                    photoUri = cursor.getString(3)?.takeIf { it.isNotBlank() },
                )
            }
        }
        val rows = keys.mapNotNull { key ->
            val hit = found[key] ?: return@mapNotNull null
            val (number, count) = defaultPhone(hit.contactId)
            ContactEntry(
                contactId = hit.contactId,
                name = hit.name,
                lookupKey = key,
                number = number,
                photoUri = hit.photoUri,
                phoneCount = count,
            )
        }
        return enrich(rows)
    }

    /**
     * Полный контакт по идентификатору: журнал OneBar помнит имя и номер, а почта со
     * строками мессенджеров живут только в провайдере — их запрашивает меню.
     */
    @SuppressLint("Range")
    override fun details(contactId: Long): ContactEntry? {
        if (!hasPermission()) return null
        val base = context.contentResolver.query(
            Contacts.CONTENT_URI,
            arrayOf(
                Contacts.DISPLAY_NAME,
                Contacts.LOOKUP_KEY,
                Contacts.PHOTO_URI,
            ),
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
                photoUri = cursor.getString(2)?.takeIf { it.isNotBlank() },
                phoneCount = count,
            )
        } ?: return null
        val ids = listOf(contactId)
        return base.copy(
            email = emailsOf(ids)[contactId],
            messengers = profileRows(ids)[contactId].orEmpty(),
        )
    }

    /** Почта и профили мессенджеров одним запросом на страницу результатов: нужны только меню. */
    private fun enrich(rows: List<ContactEntry>): List<ContactEntry> {
        val ids = rows.map { it.contactId }
        val emails = emailsOf(ids)
        val profiles = profileRows(ids)
        return rows.map { it.copy(email = emails[it.contactId], messengers = profiles[it.contactId].orEmpty()) }
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

    private companion object {
        const val HAS_PHONE_ONLY = "${Contacts.HAS_PHONE_NUMBER} = 1"

        // Избранные первыми, затем по частоте обращения, затем алфавит.
        const val STARRED_FIRST_SORT =
            "${Contacts.STARRED} DESC, ${Contacts.TIMES_CONTACTED} DESC, ${Contacts.DISPLAY_NAME} ASC"


        // Запас кандидатов: часть отбросят е/ё и регистр, которые LIKE не различает точно.
        const val CANDIDATE_FACTOR = 6

        // ContactsContract.Notes вне публичного API, поэтому mime строкой — как он записан в провайдере.
        const val NOTE_MIME = "vnd.com.android.item/note"

        val EXTRA_FIELD_MIMES = listOf(
            Organization.CONTENT_ITEM_TYPE,
            Nickname.CONTENT_ITEM_TYPE,
            NOTE_MIME,
            StructuredPostal.CONTENT_ITEM_TYPE,
            Email.CONTENT_ITEM_TYPE,
        )
    }
}
