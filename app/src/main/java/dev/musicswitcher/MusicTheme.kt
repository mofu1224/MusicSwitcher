package dev.musicswitcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightColors = lightColorScheme(
    primary = Color(0xFF191919), onPrimary = Color.White,
    primaryContainer = Color(0xFFF2F2F2), onPrimaryContainer = Color(0xFF191919),
    secondary = Color(0xFF555555), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2F2F2), onSecondaryContainer = Color(0xFF191919),
    tertiary = Color(0xFF555555), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDEDED), onTertiaryContainer = Color(0xFF1A1A1A),
    background = Color.White, onBackground = Color(0xFF191919),
    surface = Color.White, onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF666666),
    surfaceDim = Color(0xFFE0E0E0), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5), surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFEAEAEA),
    inverseSurface = Color(0xFF303030), inverseOnSurface = Color(0xFFFAFAFA), inversePrimary = Color(0xFFEEEEEE),
    outline = Color(0xFF757575), outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFF333333), onError = Color.White,
    errorContainer = Color(0xFFEDEDED), onErrorContainer = Color(0xFF1A1A1A),
)

private val darkColors = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    primaryContainer = Color(0xFF202020), onPrimaryContainer = Color.White,
    secondary = Color(0xFFBBBBBB), onSecondary = Color(0xFF181818),
    secondaryContainer = Color(0xFF202020), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFBBBBBB), onTertiary = Color(0xFF181818),
    tertiaryContainer = Color(0xFF303030), onTertiaryContainer = Color(0xFFEEEEEE),
    background = Color.Black, onBackground = Color.White,
    surface = Color(0xFF191919), onSurface = Color.White,
    surfaceVariant = Color(0xFF303030), onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceDim = Color(0xFF181818), surfaceBright = Color(0xFF383838),
    surfaceContainerLowest = Color(0xFF141414), surfaceContainerLow = Color(0xFF202020),
    surfaceContainer = Color(0xFF202020), surfaceContainerHigh = Color(0xFF272727),
    surfaceContainerHighest = Color(0xFF303030),
    inverseSurface = Color(0xFFEEEEEE), inverseOnSurface = Color(0xFF181818), inversePrimary = Color(0xFF1A1A1A),
    outline = Color(0xFF999999), outlineVariant = Color(0xFF404040),
    error = Color(0xFFEEEEEE), onError = Color(0xFF181818),
    errorContainer = Color(0xFF303030), onErrorContainer = Color(0xFFEEEEEE),
)

private val hand = FontFamily(Font(R.font.yomogi))
private val type = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = hand), displayMedium = displayMedium.copy(fontFamily = hand),
        displaySmall = displaySmall.copy(fontFamily = hand), headlineLarge = headlineLarge.copy(fontFamily = hand),
        headlineMedium = headlineMedium.copy(fontFamily = hand), headlineSmall = headlineSmall.copy(fontFamily = hand),
        titleLarge = titleLarge.copy(fontFamily = hand), titleMedium = titleMedium.copy(fontFamily = hand),
        titleSmall = titleSmall.copy(fontFamily = hand), bodyLarge = bodyLarge.copy(fontFamily = hand),
        bodyMedium = bodyMedium.copy(fontFamily = hand), bodySmall = bodySmall.copy(fontFamily = hand),
        labelLarge = labelLarge.copy(fontFamily = hand), labelMedium = labelMedium.copy(fontFamily = hand),
        labelSmall = labelSmall.copy(fontFamily = hand),
    )
}

@Composable
internal fun MusicTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkMode) darkColors else lightColors, typography = type, content = content)
}
