package com.example.platformtool.core.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class PlatformWindowStrategy {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Composable
fun currentPlatformWindowStrategy(): PlatformWindowStrategy {
    // Reading adaptive info makes posture and window changes observable by callers. Width remains
    // expressed in dp here so the product's approved 600/840 breakpoints stay explicit.
    currentWindowAdaptiveInfo()
    return when (LocalConfiguration.current.screenWidthDp) {
        in 0..599 -> PlatformWindowStrategy.COMPACT
        in 600..839 -> PlatformWindowStrategy.MEDIUM
        else -> PlatformWindowStrategy.EXPANDED
    }
}
