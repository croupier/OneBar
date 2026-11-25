package dev.onebar.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dev.onebar.app.data.AppearanceStore
import dev.onebar.app.ui.theme.toNightMode

class OneBarApplication : Application() {

    val appearanceStore by lazy { AppearanceStore(this) }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(appearanceStore.theme.value.toNightMode())
    }
}
