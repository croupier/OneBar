package dev.onebar.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.onebar.app.data.AppearanceStore
import dev.onebar.app.data.LanguageOption
import dev.onebar.app.data.toLocales
import dev.onebar.app.ui.theme.toNightMode

class OneBarApplication : Application() {

    val appearanceStore by lazy { AppearanceStore(this) }

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
    }
}
