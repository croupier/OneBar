package dev.onebar.app

import android.content.Context
import dev.onebar.app.data.AppRepository
import dev.onebar.app.data.CommunicationEntry
import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.data.ContactRepository
import dev.onebar.app.data.DefaultAppRepository
import dev.onebar.app.data.DefaultContactRepository
import dev.onebar.app.data.DefaultHistoryStore
import dev.onebar.app.data.HistoryStore
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Только для debug APK: один раз на сборку заменяет журнал реальными записями телефона. */
class DebugHistorySeeder(
    context: Context,
    private val history: HistoryStore,
    private val apps: AppRepository,
    private val contacts: ContactRepository,
    private val buildId: String = context.packageManager
        .getPackageInfo(context.packageName, 0)
        .lastUpdateTime
        .toString(),
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun seedIfNeeded() {
        mutex.withLock { seedIfNeededLocked() }
    }

    suspend fun seedContactsIfNeeded() {
        mutex.withLock {
            if (buildId.isBlank() || !contacts.hasPermission()) return
            if (preferences.getString(KEY_BUILD_ID, null) != buildId) {
                seedIfNeededLocked()
                return
            }
            if (preferences.getBoolean(KEY_CONTACTS_SEEDED, false)) return
            val entries = communicationEntries(System.currentTimeMillis(), Random(buildId.hashCode()))
            history.replaceHistory(
                history.recentApps.first(),
                history.appLaunchCounts.first(),
                entries,
                history.queries.first(),
            )
            preferences.edit().putBoolean(KEY_CONTACTS_SEEDED, true).commit()
        }
    }

    private suspend fun seedIfNeededLocked() {
        if (buildId.isBlank() || preferences.getString(KEY_BUILD_ID, null) == buildId) return
        val now = System.currentTimeMillis()
        val random = Random(buildId.hashCode())
        val appEntries = withContext(Dispatchers.IO) {
            apps.matching("", APP_CANDIDATES).shuffled(random).take(HISTORY_SIZE)
        }
        history.replaceHistory(
            appEntries.map { it.packageName },
            appEntries.mapIndexed { index, app -> app.packageName to 1 + (index * 37 % 60) }.toMap(),
            communicationEntries(now, random),
            emptyList(),
        )
        preferences.edit()
            .putString(KEY_BUILD_ID, buildId)
            .putBoolean(KEY_CONTACTS_SEEDED, contacts.hasPermission())
            .commit()
    }

    private suspend fun communicationEntries(now: Long, random: Random): List<CommunicationEntry> =
        withContext(Dispatchers.IO) {
            contacts.search("", HISTORY_SIZE).mapIndexedNotNull { index, contact ->
                val number = contact.number ?: return@mapIndexedNotNull null
                CommunicationEntry(
                    kind = if (index % 2 == 0) CommunicationKind.CALL else CommunicationKind.SMS,
                    contactId = contact.contactId,
                    lookupKey = contact.lookupKey,
                    name = contact.name,
                    number = number,
                    timestamp = now - index * 47 * 60_000L - random.nextLong(0, 10 * 60_000L),
                    launchCount = 1 + (index * 29 % 55),
                )
            }
        }

    private companion object {
        const val HISTORY_SIZE = 50
        const val APP_CANDIDATES = 500
        const val PREFERENCES_NAME = "debug_history_seed"
        const val KEY_BUILD_ID = "build_id"
        const val KEY_CONTACTS_SEEDED = "contacts_seeded"
    }
}
