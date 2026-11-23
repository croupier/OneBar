package dev.onebar.app.data

data class LaunchableApp(
    val packageName: String,
    val label: String,
)

data class ContactEntry(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val number: String?,
    /** Сколько телефонов у контакта: при нескольких выбор номера отдаём системному диалеру. */
    val phoneCount: Int = 1,
    /** Основной e-mail: по нему меню предлагает письмо наравне с мессенджерами. */
    val email: String? = null,
    /** Строки профилей мессенджеров: пакет → идентификатор записи в контактах. */
    val messengers: Map<String, Long> = emptyMap(),
)

enum class CommunicationKind {
    CALL,
    SMS,
}

/**
 * Запись журнала OneBar: мы фиксируем, что пользователь открыл набор номера или окно СМС
 * через приложение. Дошёл ли звонок до конца — без разрешений узнать нельзя.
 */
data class CommunicationEntry(
    val kind: CommunicationKind,
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val number: String,
    val timestamp: Long,
)
