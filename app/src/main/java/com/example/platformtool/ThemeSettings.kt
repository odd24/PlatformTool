package com.example.platformtool

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

class ThemeSettings(private val context: Context, scope: CoroutineScope) {
    private val dynamicColorKey = booleanPreferencesKey("use_dynamic_color")

    val useDynamicColor = context.themeDataStore.data
        .map { preferences -> preferences[dynamicColorKey] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.themeDataStore.edit { preferences -> preferences[dynamicColorKey] = enabled }
    }
}
