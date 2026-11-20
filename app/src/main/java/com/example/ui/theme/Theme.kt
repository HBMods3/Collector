package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    secondary = SlateTeal,
    onSecondary = Color.White,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = OnSlateContainerLight,
    tertiary = GoldAccent,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainerLight,
    onTertiaryContainer = OnGoldContainerLight,
    background = CreamBackground,
    onBackground = InkDark,
    surface = CreamSurface,
    onSurface = InkDark,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = InkMuted,
    surfaceContainerLow = CreamCardElevated,
    surfaceContainer = Color(0xFFF4F1E8),
    surfaceContainerHigh = Color(0xFFECE8DD),
    outline = OutlineWarm,
    error = CreditCrimson,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Color(0xFF003828),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = SlateLight,
    onSecondary = Color(0xFF0E2922),
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = OnSlateContainerDark,
    tertiary = GoldLight,
    onTertiary = Color(0xFF3B2600),
    tertiaryContainer = GoldContainerDark,
    onTertiaryContainer = OnGoldContainerDark,
    background = DarkBackground,
    onBackground = InkLight,
    surface = DarkSurface,
    onSurface = InkLight,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = InkLightMuted,
    surfaceContainerLow = DarkCardElevated,
    surfaceContainer = Color(0xFF172320),
    surfaceContainerHigh = Color(0xFF1F2E2A),
    outline = OutlineDark,
    error = CreditCrimsonDark,
    onError = Color(0xFF560A04)
)

val SlrShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SlrNoteBuTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = SlrTypography,
        shapes = SlrShapes,
        content = content
    )
}
