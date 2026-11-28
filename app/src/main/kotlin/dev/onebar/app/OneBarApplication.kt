package dev.onebar.app

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.onebar.app.data.AppearanceStore
import dev.onebar.app.data.DefaultAppRepository
import dev.onebar.app.data.DefaultContactRepository
import dev.onebar.app.data.DefaultHistoryStore
import dev.onebar.app.data.LanguageOption
import dev.onebar.app.data.SearchPreferencesStore
import dev.onebar.app.data.toLocales
import dev.onebar.app.ui.theme.toNightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OneBarApplication : Application() {

    val appearanceStore by lazy { AppearanceStore(this) }
    val searchPreferencesStore by lazy { SearchPreferencesStore(this) }
    val debugHistorySeeder by lazy {
        DebugHistorySeeder(
            this,
            DefaultHistoryStore(this),
            DefaultAppRepository(packageManager),
            DefaultContactRepository(this),
        )
    }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debuggable get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override fun onCreate() {
        super.onCreate()
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (appearanceStore.usesEnglishLanguageDefault) {
            if (currentLocales.isEmpty || currentLocales.get(0)?.language == "en") {
                AppCompatDelegate.setApplicationLocales(LanguageOption.ENGLISH.toLocales())
            } else {
                appearanceStore.completeLanguageSelection()
            }
        } else if (!currentLocales.isEmpty) {
            appearanceStore.completeLanguageSelection()
        } else {
            val systemLanguages = LocaleListCompat.getDefault().toLanguageTags().split(',')
            appearanceStore.initializeLanguage(systemLanguages)?.let {
                AppCompatDelegate.setApplicationLocales(it.toLocales())
            }
        }
        AppCompatDelegate.setDefaultNightMode(appearanceStore.theme.value.toNightMode())
        if (debuggable) applicationScope.launch { debugHistorySeeder.seedIfNeeded() }
    }

    fun seedDebugHistoryContacts() {
        if (debuggable) applicationScope.launch { debugHistorySeeder.seedContactsIfNeeded() }
    }
}
