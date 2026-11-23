package dev.onebar.app.data

import android.content.Context

/**
 * Мессенджеры, которые OneBar предлагает для контакта.
 *
 * «Есть ли человек в мессенджере» без разрешений не узнать, но приложения с включённой
 * синхронизацией сами пишут в контакты строку профиля ([profileMime]) — её видно с нашим
 * READ_CONTACTS, и по ней мы ставим мессенджер в начало ряда. Ссылку на чат по номеру
 * умеют не все: без неё чат открываем строкой профиля, а если и её нет — запускаем
 * приложение, и поиск идёт там.
 */
enum class Messenger(
    val packageName: String,
    val label: String,
    val profileMime: String? = null,
) {
    WHATSAPP("com.whatsapp", "WhatsApp", "vnd.android.cursor.item/vnd.com.whatsapp.profile"),
    TELEGRAM("org.telegram.messenger", "Telegram", "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile"),
    VIBER("com.viber.voip", "Viber"),
    SIGNAL("org.thoughtcrime.securesms", "Signal"),
    MAX("ru.oneme.app", "Max"),
    VK_MESSENGER("com.vk.im", "VK Мессенджер"),
    VK("com.vkontakte.android", "VK"),
    BIP("com.turkcell.bip", "Bip", "vnd.android.cursor.item/vnd.com.turkcell.bip.profile"),
    IMO("com.imo.android.imoim", "IMO", "vnd.android.cursor.item/vnd.com.imo.android.imoim.im.profile"),
    WECHAT("com.tencent.mm", "WeChat"),
    MEET("com.google.android.apps.tachyon", "Meet"),
    ;

    /** Ссылки хотят E.164: оставляем цифры и плюс, пробелы и скобки они не принимают. */
    fun chatUri(number: String?): String? {
        val digits = number
            ?.filter { it.isDigit() || it == '+' }
            ?.removePrefix("+")
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return when (this) {
            WHATSAPP -> "https://wa.me/$digits"
            TELEGRAM -> "tg://resolve?phone=$digits"
            VIBER -> "viber://chat?number=%2B$digits"
            SIGNAL -> "https://signal.me/#p=+$digits"
            else -> null
        }
    }
}

/**
 * Плитка меню: [label] берём у самого приложения — «VK Мессенджер» и Max обновляют название,
 * а [rowId] — строка профиля контакта в провайдере, по ней мессенджер открывает чат.
 */
data class MessengerTile(val messenger: Messenger, val label: String, val rowId: Long? = null)

class MessengerRepository(private val context: Context) {

    /** Все установленные; те, где контакт точно зарегистрирован, — первыми в ряду. */
    fun tilesFor(entry: ContactEntry): List<MessengerTile> = Messenger.entries
        .filter { installed(it) }
        .map { MessengerTile(it, label(it), entry.messengers[it.packageName]) }
        .sortedByDescending { it.rowId != null }

    fun label(messenger: Messenger): String = runCatching {
        val info = context.packageManager.getApplicationInfo(messenger.packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(messenger.label)

    private fun installed(messenger: Messenger): Boolean = runCatching {
        context.packageManager.getLaunchIntentForPackage(messenger.packageName) != null
    }.getOrDefault(false)
}
