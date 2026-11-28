package dev.onebar.app.data

import android.content.Context
import android.util.Log
import dev.onebar.app.BuildConfig
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

interface AnalyticsSink {
    fun track(event: String)

    object None : AnalyticsSink {
        override fun track(event: String) = Unit
    }
}

class UsageAnalytics(context: Context) : AnalyticsSink {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val apiKey = BuildConfig.APP_METRICA_API_KEY.trim()

    val isConfigured: Boolean = API_KEY_PATTERN.matches(apiKey)

    @Volatile
    var isEnabled: Boolean = false
        private set

    @Volatile
    private var initialized = false

    @Volatile
    private var activationFailed = false

    @Synchronized
    fun initialize() {
        if (!isConfigured || initialized) return
        val consent = preferences.getBoolean(KEY_ENABLED, false)
        val activation = runCatching {
            val config = AppMetricaConfig.newConfigBuilder(apiKey)
                .withDataSendingEnabled(consent)
                .withAdvIdentifiersTracking(false)
                .withLocationTracking(false)
                .withCrashReporting(false)
                .withAnrMonitoring(false)
                .withRevenueAutoTrackingEnabled(false)
                .withSessionsAutoTrackingEnabled(true)
                .withAppOpenTrackingEnabled(false)
                .build()
            AppMetrica.activate(appContext, config)
        }
        activation.onFailure { Log.w(TAG, "AppMetrica activation failed: ${it.javaClass.simpleName}") }
        initialized = true
        activationFailed = activation.isFailure
        isEnabled = consent && !activationFailed
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!isConfigured) {
            isEnabled = false
            return
        }
        initialize()
        if (activationFailed) {
            isEnabled = false
            return
        }
        isEnabled = enabled
        runCatching { AppMetrica.setDataSendingEnabled(enabled) }
            .onFailure { Log.w(TAG, "AppMetrica consent update failed: ${it.javaClass.simpleName}") }
        if (enabled) track("analytics_enabled")
    }

    override fun track(event: String) {
        if (!isConfigured || !initialized || !isEnabled) return
        runCatching { AppMetrica.reportEvent(event) }
            .onFailure { Log.w(TAG, "AppMetrica event failed: ${it.javaClass.simpleName}") }
    }

    private companion object {
        const val TAG = "OneBarAnalytics"
        const val PREFERENCES_NAME = "onebar_analytics"
        const val KEY_ENABLED = "enabled"
        val API_KEY_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )
    }
}
