package com.example.platformtool

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.platformtool.core.ui.AppTopBar
import com.example.platformtool.core.ui.EmptyState
import com.example.platformtool.core.ui.PlatformTheme
import com.example.platformtool.core.ui.PlatformTokens
import com.example.platformtool.core.ui.PlatformWindowStrategy
import com.example.platformtool.core.ui.StatusBanner
import com.example.platformtool.core.ui.StatusTone
import com.example.platformtool.core.ui.ToolCard
import com.example.platformtool.core.ui.currentPlatformWindowStrategy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

class MainActivity : ComponentActivity() {
    private val themePreferences by lazy { ThemePreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val useDynamicColor by themePreferences.useDynamicColor.collectAsState(initial = true)
            val scope = rememberCoroutineScope()
            PlatformTheme(useDynamicColor = useDynamicColor) {
                HomeScreen(
                    engineeringFeatures = BuildConfig.ENGINEERING_FEATURES,
                    useDynamicColor = useDynamicColor,
                    onUseDynamicColorChanged = { enabled ->
                        scope.launch { themePreferences.setUseDynamicColor(enabled) }
                    },
                    onOpenTool = { destination -> startActivity(Intent(this, destination)) },
                )
            }
        }
    }
}

private class ThemePreferences(private val context: Context) {
    private val dynamicColorKey = booleanPreferencesKey("use_dynamic_color")
    val useDynamicColor = context.themeDataStore.data.map { preferences ->
        preferences[dynamicColorKey] ?: true
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.themeDataStore.edit { preferences -> preferences[dynamicColorKey] = enabled }
    }
}

private data class HomeTool(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val destination: Class<out Activity>,
    val engineeringOnly: Boolean = false,
)

private val homeTools = listOf(
    HomeTool(R.string.tool_audio_title, R.string.tool_audio_description, AudioPlayerActivity::class.java),
    HomeTool(R.string.tool_video_title, R.string.tool_video_description, VideoLibraryActivity::class.java),
    HomeTool(R.string.tool_sensor_title, R.string.tool_sensor_description, SensorActivity::class.java),
    HomeTool(
        R.string.tool_serial_title,
        R.string.tool_serial_description,
        SerialPortActivity::class.java,
        engineeringOnly = true,
    ),
    HomeTool(
        R.string.tool_log_title,
        R.string.tool_log_description,
        RootLogActivity::class.java,
        engineeringOnly = true,
    ),
    HomeTool(R.string.tool_fps_title, R.string.tool_fps_description, FpsTestActivity::class.java),
    HomeTool(R.string.quick_tools_title, R.string.quick_tools_description, QuickToolsActivity::class.java),
    HomeTool(R.string.tool_battery_title, R.string.tool_battery_description, BatteryInfoActivity::class.java),
    HomeTool(
        R.string.tool_console_title,
        R.string.tool_console_description,
        ConsoleActivity::class.java,
        engineeringOnly = true,
    ),
)

@Composable
private fun HomeScreen(
    engineeringFeatures: Boolean,
    useDynamicColor: Boolean,
    onUseDynamicColorChanged: (Boolean) -> Unit,
    onOpenTool: (Class<out Activity>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showThemeDialog by remember { mutableStateOf(false) }
    val strategy = currentPlatformWindowStrategy()
    val visibleTools = homeTools.filter { tool ->
        (!tool.engineeringOnly || engineeringFeatures) && tool.matches(query)
    }
    val columns = when (strategy) {
        PlatformWindowStrategy.COMPACT -> GridCells.Fixed(1)
        PlatformWindowStrategy.MEDIUM,
        PlatformWindowStrategy.EXPANDED,
        -> GridCells.Fixed(2)
    }
    val pagePadding = if (strategy == PlatformWindowStrategy.COMPACT) {
        PlatformTokens.spacing.md
    } else {
        PlatformTokens.spacing.lg
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    TextButton(onClick = { showThemeDialog = true }) {
                        Text(stringResource(R.string.theme_action))
                    }
                },
            )
        },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val contentWidth = minOf(maxWidth, 1200.dp)
            LazyVerticalGrid(
                columns = columns,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .width(contentWidth),
                contentPadding = PaddingValues(pagePadding),
                horizontalArrangement = Arrangement.spacedBy(PlatformTokens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(PlatformTokens.spacing.md),
            ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.home_heading),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.home_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                StatusBanner(
                    title = stringResource(
                        if (engineeringFeatures) R.string.home_engineering_status
                        else R.string.home_standard_status,
                    ),
                    message = stringResource(R.string.home_status_supporting),
                    tone = StatusTone.INFO,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.home_search_label)) },
                    singleLine = true,
                )
            }
            if (visibleTools.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        actionLabel = stringResource(R.string.home_clear_search),
                        onAction = { query = "" },
                    )
                }
            } else {
                items(visibleTools) { tool ->
                    val title = stringResource(tool.title)
                    ToolCard(
                        title = title,
                        description = stringResource(tool.description),
                        onClick = { onOpenTool(tool.destination) },
                        modifier = Modifier.fillMaxWidth(),
                        badge = if (tool.engineeringOnly) stringResource(R.string.home_engineering_badge) else null,
                        icon = { Text(title.take(1), style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
            }
        }
    }

    if (showThemeDialog) {
        ThemeChoiceDialog(
            useDynamicColor = useDynamicColor,
            onUseDynamicColorChanged = {
                onUseDynamicColorChanged(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }
}

@Composable
private fun ThemeChoiceDialog(
    useDynamicColor: Boolean,
    onUseDynamicColorChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_dialog_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                ThemeChoiceRow(
                    label = stringResource(R.string.theme_dynamic),
                    selected = useDynamicColor,
                    onClick = { onUseDynamicColorChanged(true) },
                )
                ThemeChoiceRow(
                    label = stringResource(R.string.theme_fixed),
                    selected = !useDynamicColor,
                    onClick = { onUseDynamicColorChanged(false) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun ThemeChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HomeTool.matches(query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) return true
    return stringResource(title).contains(normalized, ignoreCase = true) ||
        stringResource(description).contains(normalized, ignoreCase = true)
}

@Preview(name = "Home · Compact", widthDp = 320, heightDp = 720, showBackground = true)
@Preview(name = "Home · Wide", widthDp = 840, heightDp = 600, showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PlatformTheme(useDynamicColor = false) {
        HomeScreen(
            engineeringFeatures = true,
            useDynamicColor = false,
            onUseDynamicColorChanged = {},
            onOpenTool = {},
        )
    }
}
