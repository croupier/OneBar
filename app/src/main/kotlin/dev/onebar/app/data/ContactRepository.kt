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

private data class ContactRow(
    val contactId: Long,
    val name: String,
    val lookupKey: String,
    val photoUri: String?,
    val starred: Int,
    val timesContacted: Int,
)

class DefaultContactRepository(context: Context) : ContactRepository {

    private val context = context.applicationContext

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
        if (!hasPermission() || limit <= 0 || (favoritesOnly && favorites.isEmpty())) return emptyList()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            val rows = baseRows(HAS_PHONE_ONLY, null, limit).map(::entryOf)
            return withNumbers(rankContacts(rows, "", limit, favorites, favoritesOnly))
        }

        // LIKE у провайдера — только грубый фильтр: регистр он складывает по ASCII, а разницу
        // е/ё глушим маской «любой символ». Точность и порядок решает ранжировщик в памяти,
        // поэтому кандидатов берём с запасом, иначе лимит отрезал бы лучшие совпадения.
        val patterns = likePatterns(trimmed).map { "%$it%" }
        val cap = candidateLimit(limit)
        val byName = baseRows(
            "$HAS_PHONE_ONLY AND ${likeClause(patterns, Contacts.DISPLAY_NAME)}",
            patterns.toTypedArray(),
            cap,
        )
        val candidates = withPhoneNumbers(byName, trimmed, cap)

        val ranked = rankContacts(candidates, trimmed, limit, favorites, favoritesOnly)
        // Избранное может совпасть только с доп. полем, поэтому этот проход нужен и тогда,
        // когда имя или номер уже заполнили лимит. Иначе такой контакт вообще не попал бы в выдачу.
        val extra = if (ranked.size < limit || favorites.isNotEmpty()) {
            fieldRows(patterns, ranked.map { it.contactId }.toSet(), cap)
        } else {
            emptyList()
        }
        val combined = (candidates + extra).distinctBy { it.contactId }
        return withNumbers(rankContacts(combined, trimmed, limit, favorites, favoritesOnly))
    }

    private val contactRowOrder = compareByDescending<ContactRow> { it.starred }
        .thenByDescending { it.timesContacted }
        .thenBy { it.name }

    /** Ключи сортировки провайдера нужны и после объединения результатов нескольких chunk-ов. */
    @SuppressLint("Range")
    private fun baseRows(selection: String, args: Array<String>?, cap: Int): List<ContactRow> {
        val found = ArrayList<ContactRow>(cap)
        context.contentResolver.query(
            Contacts.CONTENT_URI,
            arrayOf(
                Contacts._ID,
                Contacts.DISPLAY_NAME,
                Contacts.LOOKUP_KEY,
                Contacts.PHOTO_URI,
                Contacts.STARRED,
                Contacts.TIMES_CONTACTED,
            ),
            selection,
            args,
            STARRED_FIRST_SORT,
        )?.use { cursor ->
            while (cursor.moveToNext() && found.size < cap) {
                found += ContactRow(
                    contactId = cursor.getLong(0),
                    name = cursor.getString(1).orEmpty(),
                    lookupKey = cursor.getString(2).orEmpty(),
                    photoUri = cursor.getString(3)?.takeIf { it.isNotBlank() },
                    starred = cursor.getInt(4),
                    timesContacted = cursor.getInt(5),
                )
            }
        }
        return found
    }

    private fun entryOf(row: ContactRow) = ContactEntry(
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
    private fun withPhoneNumbers(rows: List<ContactRow>, query: String, cap: Int): List<ContactEntry> {
        val patterns = phoneLikePatterns(query)
        if (patterns.isEmpty()) return rows.map(::entryOf)
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
        if (ids.isEmpty()) return rows.map(::entryOf)
        val byPhone = ids.chunked(SQLITE_BATCH_SIZE).flatMap { chunk ->
            val selectionById = "${Contacts._ID} IN (${chunk.placeholders()}) AND $HAS_PHONE_ONLY"
            baseRows(selectionById, chunk.map { it.toString() }.toTypedArray(), cap)
        }.sortedWith(contactRowOrder)
        // Строка из телефонного прохода уже содержит название, поэтому просто переставляет
        // найденную по имени запись той же строкой, но с номерами.
        return (rows + byPhone).associateBy { it.contactId }.values
            .sortedWith(contactRowOrder)
            .map { row -> entryOf(row).copy(searchableNumbers = numbers[row.contactId].orEmpty()) }
    }

    /** Номера показанных строк загружаются одним пакетом, а не отдельным запросом на контакт. */
    private fun withNumbers(rows: List<ContactEntry>): List<ContactEntry> {
        if (rows.isEmpty()) return rows
        val phones = phonesOf(rows.map { it.contactId })
        return rows.map { row ->
            val (number, count) = phones[row.contactId] ?: (null to 0)
            row.copy(number = number, phoneCount = count)
        }
    }

    /**
     * Совпадения в доп. полях — одним запросом к Data: в DATA1 лежат и e-mail, и организация,
     * и ник, и заметка, и адрес целиком; у Organization в DATA2 должность, поэтому она участвует
     * в отборе наравне с названием компании.
     */
    @SuppressLint("Range")
    private fun fieldRows(patterns: List<String>, exclude: Set<Long>, cap: Int): List<ContactEntry> {
        val mimes = EXTRA_FIELD_MIMES
        val mimeFilter = "${Data.MIMETYPE} IN (${mimes.placeholders()})"
        val selection = "($mimeFilter AND ${likeClause(patterns, Data.DATA1)}) OR " +
            "(${Data.MIMETYPE} = ? AND ${likeClause(patterns, Data.DATA2)})"
        val args = (mimes + patterns + Organization.CONTENT_ITEM_TYPE + patterns).toTypedArray()
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
        return ids.chunked(SQLITE_BATCH_SIZE).flatMap { chunk ->
            val baseSelection = "${Contacts._ID} IN (${chunk.placeholders()}) AND $HAS_PHONE_ONLY"
            baseRows(baseSelection, chunk.map { it.toString() }.toTypedArray(), cap)
        }.sortedWith(contactRowOrder).map { row ->
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
        val found = HashMap<String, ContactRow>(keys.size)
        keys.chunked(SQLITE_BATCH_SIZE).forEach { chunk ->
            val selection = "${Contacts.LOOKUP_KEY} IN (${chunk.placeholders()}) AND $HAS_PHONE_ONLY"
            baseRows(selection, chunk.toTypedArray(), chunk.size).forEach { row ->
                found[row.lookupKey] = row
            }
        }
        val rows = keys.mapNotNull { key -> found[key]?.let(::entryOf) }
        return withNumbers(rows)
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
            ContactEntry(
                contactId = contactId,
                name = cursor.getString(0).orEmpty(),
                lookupKey = cursor.getString(1).orEmpty(),
                number = null,
                photoUri = cursor.getString(2)?.takeIf { it.isNotBlank() },
                phoneCount = 0,
            )
        } ?: return null
        val ids = listOf(contactId)
        val (number, count) = phonesOf(ids)[contactId] ?: (null to 0)
        return base.copy(
            number = number,
            phoneCount = count,
            email = emailsOf(ids)[contactId],
            messengers = profileRows(ids)[contactId].orEmpty(),
        )
    }

    /**
     * Дефолтным считаем номер, помеченный самим контактом (IS_SUPER_PRIMARY/IS_PRIMARY);
     * если таких пометок нет — мобильный, а иначе первую строку.
     */
    @SuppressLint("Range")
    private fun phonesOf(ids: List<Long>): Map<Long, Pair<String?, Int>> {
        if (ids.isEmpty()) return emptyMap()
        val found = HashMap<Long, PhoneChoice>(ids.size)
        ids.chunked(SQLITE_BATCH_SIZE).forEach { chunk ->
            context.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(
                    Phone.CONTACT_ID,
                    Phone.NUMBER,
                    Phone.IS_SUPER_PRIMARY,
                    Phone.IS_PRIMARY,
                    Phone.TYPE,
                ),
                "${Phone.CONTACT_ID} IN (${chunk.placeholders()}) AND ${Phone.NUMBER} IS NOT NULL",
                chunk.map { it.toString() }.toTypedArray(),
                "${Phone.CONTACT_ID} ASC, ${Phone.TYPE} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val number = cursor.getString(1) ?: continue
                    found.getOrPut(id) { PhoneChoice() }.add(
                        number = number,
                        superPrimary = cursor.getInt(2) == 1,
                        primary = cursor.getInt(3) == 1,
                        type = cursor.getInt(4),
                    )
                }
            }
        }
        return found.mapValues { (_, choice) -> choice.default to choice.count }
    }

    private class PhoneChoice {
        var marked: String? = null
        var mobile: String? = null
        var first: String? = null
        var count: Int = 0
        val default: String?
            get() = marked ?: mobile ?: first

        fun add(number: String, superPrimary: Boolean, primary: Boolean, type: Int) {
            count++
            first = first ?: number
            when {
                superPrimary || primary -> marked = marked ?: number
                type == Phone.TYPE_MOBILE -> mobile = mobile ?: number
            }
        }
    }

    @SuppressLint("Range")
    private fun emailsOf(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val best = HashMap<Long, Pair<Int, String>>(ids.size)
        ids.chunked(SQLITE_BATCH_SIZE).forEach { chunk ->
            context.contentResolver.query(
                Email.CONTENT_URI,
                arrayOf(Email.CONTACT_ID, Email.ADDRESS, Email.IS_SUPER_PRIMARY, Email.IS_PRIMARY, Email.TYPE),
                "${Email.CONTACT_ID} IN (${chunk.placeholders()})",
                chunk.map { it.toString() }.toTypedArray(),
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
        val found = HashMap<Long, MutableMap<String, Long>>(ids.size)
        ids.chunked(SQLITE_BATCH_SIZE).forEach { chunk ->
            val selection = "${Data.CONTACT_ID} IN (${chunk.placeholders()}) AND ${Data.MIMETYPE} IN (${mimes.placeholders()})"
            val args = (chunk.map { it.toString() } + mimes).toTypedArray()
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
        }
        return found
    }

    private fun List<*>.placeholders(): String = joinToString(separator = ",") { "?" }

    internal companion object {
        const val HAS_PHONE_ONLY = "${Contacts.HAS_PHONE_NUMBER} = 1"

        // Избранные первыми, затем по частоте обращения, затем алфавит.
        const val STARRED_FIRST_SORT =
            "${Contacts.STARRED} DESC, ${Contacts.TIMES_CONTACTED} DESC, ${Contacts.DISPLAY_NAME} ASC"

        // Запас для точного ранжирования после грубого LIKE-фильтра провайдера.
        const val MAX_CANDIDATES = 1_000

        // Надёжный запас до классического лимита SQLite в 999 параметров, включая MIME.
        const val SQLITE_BATCH_SIZE = 500

        internal fun candidateLimit(limit: Int): Int =
            if (limit <= 0) 0 else maxOf(limit, MAX_CANDIDATES)

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
