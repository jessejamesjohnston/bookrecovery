package ai.jessejohnston.bookmend.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The brand: the icon's navy book, coral thread and paper. The app's own UI
 * follows the wallpaper (dynamic color) where the platform offers it; these
 * colors are the fallback scheme below Android 12, and they stay fixed in the
 * icon, the illustrations and the README.
 */
object Brand {
    val Navy = Color(0xFF1F2A44)
    val NavyDeep = Color(0xFF141C30)
    val Coral = Color(0xFFF06A2B)
    val Paper = Color(0xFFF4ECE1)
    val PaperEdge = Color(0xFFEADBC4)
}

internal val BookmendLight = lightColorScheme(
    primary = Color(0xFF2E3B63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE1F5),
    onPrimaryContainer = Color(0xFF141C33),
    secondary = Color(0xFF9E3D0B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCB),
    onSecondaryContainer = Color(0xFF3A1100),
    tertiary = Color(0xFF3F5F7A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD2E5F7),
    onTertiaryContainer = Color(0xFF0C1E2F),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1B1C21),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1B1C21),
    surfaceVariant = Color(0xFFECE6DC),
    onSurfaceVariant = Color(0xFF4A4740),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F2EB),
    surfaceContainer = Color(0xFFF1ECE4),
    surfaceContainerHigh = Color(0xFFEBE6DD),
    surfaceContainerHighest = Color(0xFFE5E0D7),
    outline = Color(0xFF7B776E),
    outlineVariant = Color(0xFFCCC6BB),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

internal val BookmendDark = darkColorScheme(
    primary = Color(0xFFB8C4F0),
    onPrimary = Color(0xFF1A254A),
    primaryContainer = Color(0xFF303D66),
    onPrimaryContainer = Color(0xFFDCE1F5),
    secondary = Color(0xFFFFB694),
    onSecondary = Color(0xFF571E00),
    secondaryContainer = Color(0xFF7C2E00),
    onSecondaryContainer = Color(0xFFFFDBCB),
    tertiary = Color(0xFFA9C9E4),
    onTertiary = Color(0xFF0F3249),
    tertiaryContainer = Color(0xFF284861),
    onTertiaryContainer = Color(0xFFD2E5F7),
    background = Color(0xFF13141A),
    onBackground = Color(0xFFE4E2E9),
    surface = Color(0xFF13141A),
    onSurface = Color(0xFFE4E2E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceContainerLowest = Color(0xFF0E0F14),
    surfaceContainerLow = Color(0xFF1B1C22),
    surfaceContainer = Color(0xFF1F2026),
    surfaceContainerHigh = Color(0xFF292A31),
    surfaceContainerHighest = Color(0xFF34353C),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
