package ai.jessejohnston.bookmend.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Light or dark follows the system. Colors follow the wallpaper (Android 12+),
 * so Bookmend looks at home on whatever phone or Googlebook it runs on; below
 * that, the brand's navy and coral scheme. [dynamicColor] is off in screenshot
 * tests and README captures so they show the brand.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookmendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BookmendDark
        else -> BookmendLight
    }

    // MaterialExpressiveTheme keeps our color scheme but adopts the expressive
    // defaults for everything left null: the springy MotionScheme (read by the
    // wizard transitions and wavy indicators) and the expressive shape family.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
