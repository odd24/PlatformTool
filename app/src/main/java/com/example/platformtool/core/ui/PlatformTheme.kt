package com.example.platformtool.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F4CB7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3E0FF),
    onPrimaryContainer = Color(0xFF17105C),
    secondary = Color(0xFF5F5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5E1F9),
    onSecondaryContainer = Color(0xFF1C1A2C),
    tertiary = Color(0xFF7B5265),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E6),
    onTertiaryContainer = Color(0xFF301121),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE5E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF3F0F7),
    inversePrimary = Color(0xFFC4C0FF),
    surfaceTint = Color(0xFF4F4CB7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC4C0FF),
    onPrimary = Color(0xFF201B85),
    primaryContainer = Color(0xFF37339D),
    onPrimaryContainer = Color(0xFFE3E0FF),
    secondary = Color(0xFFC9C5DC),
    onSecondary = Color(0xFF302E42),
    secondaryContainer = Color(0xFF474559),
    onSecondaryContainer = Color(0xFFE5E1F9),
    tertiary = Color(0xFFEAB8CC),
    onTertiary = Color(0xFF482536),
    tertiaryContainer = Color(0xFF613B4D),
    onTertiaryContainer = Color(0xFFFFD8E6),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF928F99),
    outlineVariant = Color(0xFF47464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE5E1E9),
    inverseOnSurface = Color(0xFF303036),
    inversePrimary = Color(0xFF4F4CB7),
    surfaceTint = Color(0xFFC4C0FF),
)

private val LightStatusColors = PlatformStatusColors(
    success = Color(0xFF176B45),
    onSuccess = Color.White,
    successContainer = Color(0xFFA5F2C4),
    onSuccessContainer = Color(0xFF002112),
    warning = Color(0xFF7A5900),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFDEA1),
    onWarningContainer = Color(0xFF261900),
    info = Color(0xFF3C6090),
    onInfo = Color.White,
    infoContainer = Color(0xFFD4E3FF),
    onInfoContainer = Color(0xFF001C3A),
)

private val DarkStatusColors = PlatformStatusColors(
    success = Color(0xFF89D6A9),
    onSuccess = Color(0xFF003822),
    successContainer = Color(0xFF005232),
    onSuccessContainer = Color(0xFFA5F2C4),
    warning = Color(0xFFF4BF48),
    onWarning = Color(0xFF402D00),
    warningContainer = Color(0xFF5C4300),
    onWarningContainer = Color(0xFFFFDEA1),
    info = Color(0xFFA6C8FF),
    onInfo = Color(0xFF00315F),
    infoContainer = Color(0xFF224876),
    onInfoContainer = Color(0xFFD4E3FF),
)

private val PlatformTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
)

private val PlatformShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun PlatformTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalPlatformSpacing provides PlatformSpacing(),
        LocalPlatformMotion provides PlatformMotion(),
        LocalPlatformStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = PlatformTypography,
            shapes = PlatformShapes,
            content = content,
        )
    }
}
