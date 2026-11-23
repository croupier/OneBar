package dev.onebar.app.data

import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock

/**
 * Источник запускаемых приложений. Интерфейс нужен чтобы логика ViewModel
 * проверялась на JVM, без PackageManager и эмулятора.
 */
interface AppRepository {
    fun matching(query: String, limit: Int): List<LaunchableApp>

    fun byPackages(packages: List<String>): List<LaunchableApp>
}

/** Совпадение с начала названия важнее совпадения внутри, поэтому результат собирается двумя корзинами. */
internal fun rankApps(all: List<LaunchableApp>, query: String, limit: Int): List<LaunchableApp> {
    if (query.isBlank()) return all.take(limit)
    val needle = query.trim().lowercase()
    val starts = ArrayList<LaunchableApp>()
    val contains = ArrayList<LaunchableApp>()
    for (app in all) {
        val label = app.label.lowercase()
        when {
            label.startsWith(needle) -> starts += app
            label.contains(needle) -> contains += app
        }
    }
    return (starts + contains).take(limit)
}

class DefaultAppRepository(private val pm: PackageManager) : AppRepository {

    private var cache: List<LaunchableApp> = emptyList()
    private var cachedAt: Long = 0L

    override fun matching(query: String, limit: Int): List<LaunchableApp> = rankApps(all(), query, limit)

    override fun byPackages(packages: List<String>): List<LaunchableApp> {
        val all = all()
        return packages.mapNotNull { pkg -> all.firstOrNull { it.packageName == pkg } }
    }

    private fun all(): List<LaunchableApp> {
        val now = SystemClock.elapsedRealtime()
        if (cache.isEmpty() || now - cachedAt > CACHE_TTL_MS) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            cache = pm.queryIntentActivities(intent, 0)
                .map { info ->
                    LaunchableApp(
                        packageName = info.activityInfo.packageName,
                        label = info.loadLabel(pm).toString(),
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            cachedAt = now
        }
        return cache
    }

    private companion object {
        const val CACHE_TTL_MS = 60_000L
    }
}
