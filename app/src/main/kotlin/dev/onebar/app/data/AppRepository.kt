package dev.onebar.app.data

import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock

/**
 * Источник запускаемых приложений. Интерфейс нужен чтобы логика ViewModel
 * проверялась на JVM, без PackageManager и эмулятора.
 */
interface AppRepository {
    fun matching(
        query: String,
        limit: Int,
        favorites: Set<String> = emptySet(),
        favoritesOnly: Boolean = false,
    ): List<LaunchableApp>

    fun byPackages(packages: List<String>): List<LaunchableApp>

    fun invalidate()
}

/**
 * Ранжирование приложений: сначала избранные, затем совпадение по названию — варианты запроса
 * по приоритету, с начала названия важнее внутри, — и только потом те же варианты по служебным
 * полям (пакет, описание). Лимит поэтому режет наименее релевантное, а не случайное.
 */
internal fun rankApps(
    all: List<LaunchableApp>,
    query: String,
    limit: Int,
    favorites: Set<String> = emptySet(),
    favoritesOnly: Boolean = false,
): List<LaunchableApp> {
    if (query.isBlank()) {
        return all.asSequence()
            .filter { !favoritesOnly || it.packageName in favorites }
            .take(limit)
            .toList()
    }
    val needles = buildNeedles(query)
    val scored = ArrayList<Pair<Rank, LaunchableApp>>(all.size)
    for (app in all) {
        val favorite = app.packageName in favorites
        if (favoritesOnly && !favorite) continue
        val rank = rankOf(needles, appFields(app), favorite) ?: continue
        scored += rank to app
    }
    return scored.sortedBy { it.first }.map { it.second }.take(limit)
}

/** Название — первое поле; пакет и описание ищем только когда название молчит. */
private fun appFields(app: LaunchableApp): List<List<String>> = listOf(
    listOf(app.label.lowercase()),
    listOf(app.packageName.lowercase(), app.description.lowercase()),
)

class DefaultAppRepository(private val pm: PackageManager) : AppRepository {

    private val lock = Any()
    private var cache: List<LaunchableApp> = emptyList()
    private var cachedAt: Long = 0L

    override fun matching(query: String, limit: Int, favorites: Set<String>, favoritesOnly: Boolean): List<LaunchableApp> =
        rankApps(all(), query, limit, favorites, favoritesOnly)

    override fun byPackages(packages: List<String>): List<LaunchableApp> {
        val byPackage = all().associateBy { it.packageName }
        return packages.mapNotNull { byPackage[it] }
    }

    override fun invalidate() {
        synchronized(lock) {
            cache = emptyList()
            cachedAt = 0L
        }
    }

    private fun all(): List<LaunchableApp> = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        if (cache.isEmpty() || now - cachedAt > CACHE_TTL_MS) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            cache = pm.queryIntentActivities(intent, 0)
                .map { info ->
                    // ApplicationInfo.loadDescription отдаёт null у приложений без описания,
                    // в отличие от ResolveInfo.loadDescription, который подставляет название.
                    val description = info.activityInfo.applicationInfo.loadDescription(pm)?.toString().orEmpty()
                    LaunchableApp(
                        packageName = info.activityInfo.packageName,
                        label = info.loadLabel(pm).toString(),
                        description = description,
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            cachedAt = now
        }
        cache
    }

    private companion object {
        const val CACHE_TTL_MS = 60_000L
    }
}
