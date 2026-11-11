package ai.jessejohnston.bookmend.ui

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.data.CatalogFixtures
import ai.jessejohnston.bookmend.data.CatalogParser
import ai.jessejohnston.bookmend.data.DeviceFamily
import ai.jessejohnston.bookmend.ui.screens.ModelBrowserScreen
import ai.jessejohnston.bookmend.ui.screens.WelcomeScreen
import ai.jessejohnston.bookmend.ui.theme.BookmendTheme
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wide windows (a Googlebook, a tablet) get the model list and its versions side by side; dark follows the system. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeScreenTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val options = RoborazziOptions(taskType = RoborazziTaskType.Record)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun aWideWindowShowsListAndVersionsTogether() {
        val images = CatalogParser.chromeOs(CatalogFixtures.chromeOs, false) +
            (1..14).map { CatalogParser.chromeOs(CatalogFixtures.chromeOs, false).first().copy(name = "Chromebook Model $it", url = "https://x/$it.zip", manufacturer = listOf("Asus", "Dell", "HP", "Samsung")[it % 4]) }
        compose.setContent {
            BookmendTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ModelBrowserScreen(DeviceFamily.CHROMEBOOK, CatalogState.Ready(images), null, null, {}, {})
                }
            }
        }
        compose.onAllNodesWithText("Acer Chromebook 514 (CB514-2H, CB514-2HT)")[0].performClick()
        // Both panes at once: the list's search and the detail's versions.
        assertTrue(compose.onAllNodesWithText(context.getString(R.string.models_search)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText(context.getString(R.string.models_channel_recommended)).fetchSemanticsNodes().isNotEmpty())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/wide-models.png", roborazziOptions = options)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-night-xxhdpi")
    fun darkFollowsTheSystem() {
        compose.setContent {
            BookmendTheme(darkTheme = true, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) { WelcomeScreen({}, {}) }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/welcome-dark.png", roborazziOptions = options)
    }
}
