package br.ufu.chatapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1E3557),
    onPrimary = Color(0xFFF9F3E8),
    primaryContainer = Color(0xFFC9D7F0),
    onPrimaryContainer = Color(0xFF12233B),
    secondary = Color(0xFFE46F47),
    onSecondary = Color(0xFF25120B),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFFF7F1E7),
    onBackground = Color(0xFF1C2430),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1B2431),
    surfaceVariant = Color(0xFFEAE0D2),
    onSurfaceVariant = Color(0xFF5D5A57),
    outline = Color(0xFFD2C3AF),
    error = Color(0xFFB33A3A)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF0B24D),
    onPrimary = Color(0xFF2A1A0A),
    primaryContainer = Color(0xFF334A71),
    onPrimaryContainer = Color(0xFFE2EBFF),
    secondary = Color(0xFFFF8A5C),
    onSecondary = Color(0xFF32160B),
    tertiary = Color(0xFFFFD67B),
    background = Color(0xFF11151C),
    onBackground = Color(0xFFF6EFE4),
    surface = Color(0xFF1A202A),
    onSurface = Color(0xFFF5EFE6),
    surfaceVariant = Color(0xFF252E3A),
    onSurfaceVariant = Color(0xFFC2B6A7),
    outline = Color(0xFF48566A),
    error = Color(0xFFFFB4AB)
)

@Composable
fun ChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(),
        content = content
    )
}
