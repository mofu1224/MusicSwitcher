package dev.musicswitcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightColors = lightColorScheme(
    primary = Color(0xFF1A1A1A), onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEDED), onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF555555), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDED), onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF555555), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDEDED), onTertiaryContainer = Color(0xFF1A1A1A),
    background = Color(0xFFFAFAFA), onBackground = Color(0xFF1A1A1A),
    surface = Color.White, onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF666666),
    surfaceDim = Color(0xFFE0E0E0), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5), surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFE7E7E7),
    inverseSurface = Color(0xFF303030), inverseOnSurface = Color(0xFFFAFAFA), inversePrimary = Color(0xFFEEEEEE),
    outline = Color(0xFF757575), outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFF333333), onError = Color.White,
    errorContainer = Color(0xFFEDEDED), onErrorContainer = Color(0xFF1A1A1A),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFEEEEEE), onPrimary = Color(0xFF181818),
    primaryContainer = Color(0xFF303030), onPrimaryContainer = Color(0xFFEEEEEE),
    secondary = Color(0xFFBBBBBB), onSecondary = Color(0xFF181818),
    secondaryContainer = Color(0xFF303030), onSecondaryContainer = Color(0xFFEEEEEE),
    tertiary = Color(0xFFBBBBBB), onTertiary = Color(0xFF181818),
    tertiaryContainer = Color(0xFF303030), onTertiaryContainer = Color(0xFFEEEEEE),
    background = Color(0xFF121212), onBackground = Color(0xFFEEEEEE),
    surface = Color(0xFF222222), onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF303030), onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceDim = Color(0xFF181818), surfaceBright = Color(0xFF383838),
    surfaceContainerLowest = Color(0xFF141414), surfaceContainerLow = Color(0xFF202020),
    surfaceContainer = Color(0xFF272727), surfaceContainerHigh = Color(0xFF303030),
    surfaceContainerHighest = Color(0xFF383838),
    inverseSurface = Color(0xFFEEEEEE), inverseOnSurface = Color(0xFF181818), inversePrimary = Color(0xFF1A1A1A),
    outline = Color(0xFF999999), outlineVariant = Color(0xFF404040),
    error = Color(0xFFEEEEEE), onError = Color(0xFF181818),
    errorContainer = Color(0xFF303030), onErrorContainer = Color(0xFFEEEEEE),
)

@Composable
internal fun MusicTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkMode) darkColors else lightColors, content = content)
}
