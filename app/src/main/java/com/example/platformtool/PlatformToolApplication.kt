package com.example.platformtool

import android.app.Application
import android.os.StrictMode
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PlatformToolApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var themeSettings: ThemeSettings
        private set

    override fun onCreate() {
        super.onCreate()
        themeSettings = ThemeSettings(this, applicationScope)
        DynamicColors.applyToActivitiesIfAvailable(
            this,
            DynamicColorsOptions.Builder()
                .setPrecondition { _, _ -> themeSettings.useDynamicColor.value }
                .build(),
        )
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build(),
        )
    }
}
