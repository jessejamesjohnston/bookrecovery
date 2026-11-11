package ai.jessejohnston.bookmend.ui.theme

import ai.jessejohnston.bookmend.R
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

private fun figtree(weight: Int) = Font(
    R.font.figtree,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Figtree (SIL OFL 1.1), one variable file for every weight. */
val Figtree = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700), figtree(800))

/**
 * Material 3's stock scale, Expressive styles included, set in Figtree. Sizes
 * and line heights stay Material's, so the scale keeps working with font
 * scaling and the components' own expectations.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography: Typography = Typography().let { base ->
    fun TextStyle.f() = copy(fontFamily = Figtree)
    Typography(
        displayLarge = base.displayLarge.f(),
        displayMedium = base.displayMedium.f(),
        displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(),
        headlineMedium = base.headlineMedium.f(),
        headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(),
        titleMedium = base.titleMedium.f(),
        titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(),
        labelMedium = base.labelMedium.f(),
        labelSmall = base.labelSmall.f(),
        displayLargeEmphasized = base.displayLargeEmphasized.f(),
        displayMediumEmphasized = base.displayMediumEmphasized.f(),
        displaySmallEmphasized = base.displaySmallEmphasized.f(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.f(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.f(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.f(),
        titleLargeEmphasized = base.titleLargeEmphasized.f(),
        titleMediumEmphasized = base.titleMediumEmphasized.f(),
        titleSmallEmphasized = base.titleSmallEmphasized.f(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.f(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.f(),
        bodySmallEmphasized = base.bodySmallEmphasized.f(),
        labelLargeEmphasized = base.labelLargeEmphasized.f(),
        labelMediumEmphasized = base.labelMediumEmphasized.f(),
        labelSmallEmphasized = base.labelSmallEmphasized.f(),
    )
}
