package ai.jessejohnston.bookmend

import ai.jessejohnston.bookmend.data.CatalogFetcher
import ai.jessejohnston.bookmend.data.CatalogFixtures
import ai.jessejohnston.bookmend.data.RecoveryRepository
import ai.jessejohnston.bookmend.ui.WizardViewModel
import ai.jessejohnston.bookmend.usb.KeepAliveService
import android.app.Notification
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The activity: splash, theme, the wizard, and a Continue On arrival. */
@RunWith(RobolectricTestRunner::class)
class AppShellTest {

    @get:Rule val compose = createEmptyComposeRule()
    private val original = RecoveryRepository.instance
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun offlineCatalog() {
        RecoveryRepository.instance = RecoveryRepository(
            CatalogFetcher { url -> mapOf(RecoveryRepository.GOOGLEBOOK_URL to CatalogFixtures.googlebook)[url] ?: throw IOException("offline") },
            Dispatchers.Unconfined
        )
    }

    @After fun restore() { RecoveryRepository.instance = original }

    @Test
    fun opensOnWelcome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText(context.getString(R.string.welcome_title)).assertExists()
        }
    }

    @Test
    fun aHandedOffChoiceOpensOnTheSameModel() {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(WizardViewModel.KEY_FAMILY, "GOOGLEBOOK")
            .putExtra(WizardViewModel.KEY_IMAGE, "https://dl.google.com/q.zip")
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(5_000) { compose.onAllNodesWithText("HP Googlebook 14").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun aHandoffMidWriteIsIgnored() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        // Pretend the wizard is writing a drive, as RecoveryApp reports it.
        val field = MainActivity::class.java.getDeclaredField("currentRoute").apply { isAccessible = true }
        field.set(activity, ai.jessejohnston.bookmend.ui.Route.Flash)
        controller.newIntent(Intent(context, MainActivity::class.java).putExtra(WizardViewModel.KEY_FAMILY, "GOOGLEBOOK"))
        val wizard = androidx.lifecycle.ViewModelProvider(activity)[WizardViewModel::class.java]
        assertEquals(null, wizard.family.value)
        controller.pause().stop().destroy()
    }

    @Test
    fun aHandoffCanArriveWhileOpen() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.newIntent(Intent(context, MainActivity::class.java).putExtra(WizardViewModel.KEY_FAMILY, "GOOGLEBOOK"))
        val wizard = androidx.lifecycle.ViewModelProvider(controller.get())[WizardViewModel::class.java]
        assertEquals("not busy, so the handoff is taken", ai.jessejohnston.bookmend.data.DeviceFamily.GOOGLEBOOK, wizard.family.value)
        controller.newIntent(Intent(context, MainActivity::class.java))
        controller.pause().stop().destroy()
    }

    @Test
    fun theKeepAliveServiceHoldsTheForeground() {
        KeepAliveService.currentNotification = Notification.Builder(context, "flash").setSmallIcon(R.drawable.ic_notification).build()
        val controller = Robolectric.buildService(KeepAliveService::class.java).create()
        controller.startCommand(0, 1)
        assertEquals(1001, shadowOf(controller.get()).lastForegroundNotificationId)
        assertEquals(null, controller.get().onBind(null))
        KeepAliveService.currentNotification = null
        controller.startCommand(0, 2)
        controller.destroy()
    }
}
