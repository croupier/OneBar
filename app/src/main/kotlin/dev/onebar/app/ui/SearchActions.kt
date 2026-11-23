package dev.onebar.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Data
import dev.onebar.app.data.ContactEntry
import dev.onebar.app.data.MessengerTile
import java.net.URLEncoder

object SearchActions {

    fun openApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        start(context, intent)
    }

    /**
     * Один номер набираем сами, несколько отдаём системному «Телефону»: в карточке контакта
     * есть подписи («мобильный», «рабочий»), которых у нас нет.
     */
    fun callEntry(context: Context, entry: ContactEntry) {
        val number = entry.number
        if (entry.phoneCount > 1 || number == null) {
            openContact(context, entry)
        } else {
            start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        }
    }

    /** Для СМС у «Телефона» нет выбора номера, поэтому при нескольких тоже карточка контакта. */
    fun smsEntry(context: Context, entry: ContactEntry) {
        val number = entry.number
        if (entry.phoneCount > 1 || number == null) {
            openContact(context, entry)
        } else {
            start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
        }
    }

    fun copyNumber(context: Context, number: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Телефон", number))
    }

    fun editContact(context: Context, entry: ContactEntry) =
        start(context, Intent(Intent.ACTION_EDIT, lookupUri(entry)))

    fun openContact(context: Context, entry: ContactEntry) =
        start(context, Intent(Intent.ACTION_VIEW, lookupUri(entry)))

    /**
     * Порядок такой: ссылка по номеру, потом строка профиля контакта (её мессенджер сам
     * понимает как «открой чат»), потом просто запуск приложения.
     */
    fun openMessenger(context: Context, tile: MessengerTile, number: String?) {
        val messenger = tile.messenger
        val uri = messenger.chatUri(number)
        if (uri != null && tryStart(context, viewIntent(Uri.parse(uri)))) return
        val rowId = tile.rowId
        val mime = messenger.profileMime
        if (rowId != null && mime != null &&
            tryStart(context, viewIntent(ContentUris.withAppendedId(Data.CONTENT_URI, rowId), mime))
        ) {
            return
        }
        context.packageManager.getLaunchIntentForPackage(messenger.packageName)?.let { start(context, it) }
    }

    fun openEmail(context: Context, address: String) =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))

    fun webSearch(context: Context, engine: WebEngine, query: String) {
        if (query.isBlank()) return
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(engine.searchUrl(query.trim()))))
    }

    private fun lookupUri(entry: ContactEntry): Uri =
        ContactsContract.Contacts.getLookupUri(entry.contactId, entry.lookupKey)

    /**
     * NEW_TASK обязателен для ссылки на чат: без флага Android поднимает тёплую задачу
     * мессенджера, а URI до приложения не доходит — открывается прошлый чат.
     * Тип ставим только вместе с данными: Intent.setType() само по себе очищает data.
     */
    private fun viewIntent(data: Uri, type: String? = null): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        if (type == null) intent.data = data else intent.setDataAndType(data, type)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    private fun start(context: Context, intent: Intent) {
        tryStart(context, intent)
    }

    /** false — приложение не принимает интент, пробуем следующий способ. */
    private fun tryStart(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

enum class WebEngine {
    YANDEX,
    GOOGLE,
    ;

    fun searchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return when (this) {
            YANDEX -> "https://yandex.ru/search/?text=$encoded"
            GOOGLE -> "https://www.google.com/search?q=$encoded"
        }
    }
}
