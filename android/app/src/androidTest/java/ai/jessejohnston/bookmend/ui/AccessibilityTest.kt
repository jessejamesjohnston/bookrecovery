package ai.jessejohnston.bookmend.ui

import ai.jessejohnston.bookmend.data.CatalogFixtures
import ai.jessejohnston.bookmend.data.CatalogParser
import ai.jessejohnston.bookmend.data.DeviceFamily
import ai.jessejohnston.bookmend.ui.screens.AfterFlashGuide
import ai.jessejohnston.bookmend.ui.screens.DriveCheck
import ai.jessejohnston.bookmend.ui.screens.DriveItem
import ai.jessejohnston.bookmend.ui.screens.DrivePicker
import ai.jessejohnston.bookmend.ui.screens.FamilyScreen
import ai.jessejohnston.bookmend.ui.screens.IdentifyScreen
import ai.jessejohnston.bookmend.ui.screens.LicensesScreen
import ai.jessejohnston.bookmend.ui.screens.ModelBrowserScreen
import ai.jessejohnston.bookmend.ui.screens.WelcomeScreen
import ai.jessejohnston.bookmend.ui.theme.BookmendTheme
import ai.jessejohnston.bookmend.usb.DriveSize
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Google's Accessibility Test Framework over every main screen, on a real
 * Android runtime (it doesn't run under Robolectric), in light, dark and at
 * 200% font: touch targets, contrast, labels. Any error fails. The canary
 * proves the checks are live: a tiny unlabeled button must be caught.
 */
@RunWith(Parameterized::class)
class AccessibilityTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "dark={0} font={1}")
        fun cases() = listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f))
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val chromeOs = CatalogParser.chromeOs(CatalogFixtures.chromeOs, false)
    private val googlebooks = CatalogParser.googlebook(CatalogFixtures.googlebook)

    @Before fun live() = compose.enableAccessibilityChecks()

    private fun check(content: @Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                BookmendTheme(darkTheme = dark, dynamicColor = false) { content() }
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test fun theChecksAreLive() {
        val caught = runCatching { check { Box(Modifier.size(10.dp).background(Color.Gray).clickable {}) } }.isFailure
        assertTrue("a 10 dp unlabeled button must fail the checks", caught)
    }

    @Test fun welcome() = check { WelcomeScreen({}, {}) }
    @Test fun family() = check { FamilyScreen(DeviceFamily.GOOGLEBOOK) {} }
    @Test fun identify() = check { IdentifyScreen(CatalogState.Ready(chromeOs), { IdentifyResult.NoMatch(it) }, {}, {}, {}) }
    @Test fun models() = check { ModelBrowserScreen(DeviceFamily.CHROMEBOOK, CatalogState.Ready(chromeOs), null, null, {}, {}) }
    @Test fun versions() = check { ModelBrowserScreen(DeviceFamily.GOOGLEBOOK, CatalogState.Ready(googlebooks), "Acer Googlebook 14", null, {}, {}) }
    @Test fun drives() = check {
        DrivePicker(
            listOf(
                DriveItem("a", "SanDisk Ultra", DriveCheck.Done(DriveSize.Known(64_000_000_000))),
                DriveItem("b", "Old stick", DriveCheck.Done(DriveSize.Known(16_000_000_000))),
            ),
            19_069_493_760, "Acer Googlebook 14", false, null, false, {}, {}, {}, {}
        )
    }
    @Test fun guide() = check { AfterFlashGuide(DeviceFamily.CHROMEBOOK) }
    @Test fun licenses() = check { LicensesScreen() }
    @Test fun unavailable() = check { ModelBrowserScreen(DeviceFamily.CHROMEBOOK, CatalogState.Unavailable, null, null, {}, {}) }
}
