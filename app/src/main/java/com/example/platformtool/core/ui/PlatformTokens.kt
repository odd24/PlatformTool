package com.example.platformtool.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class PlatformSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 40.dp,
    val touchTarget: Dp = 48.dp,
)

@Immutable
data class PlatformMotion(
    val shortMillis: Int = 150,
    val standardMillis: Int = 200,
    val emphasizedMillis: Int = 250,
)

@Immutable
data class PlatformStatusColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

internal val LocalPlatformSpacing = staticCompositionLocalOf { PlatformSpacing() }
internal val LocalPlatformMotion = staticCompositionLocalOf { PlatformMotion() }
internal val LocalPlatformStatusColors = staticCompositionLocalOf<PlatformStatusColors> {
    error("Platform status colors are only available inside PlatformTheme")
}

object PlatformTokens {
    val spacing: PlatformSpacing
        @androidx.compose.runtime.Composable get() = LocalPlatformSpacing.current

    val motion: PlatformMotion
        @androidx.compose.runtime.Composable get() = LocalPlatformMotion.current

    val statusColors: PlatformStatusColors
        @androidx.compose.runtime.Composable get() = LocalPlatformStatusColors.current
}
