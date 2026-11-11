package ai.jessejohnston.bookmend.ui

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.data.CatalogFetcher
import ai.jessejohnston.bookmend.data.CatalogFixtures
import ai.jessejohnston.bookmend.data.CatalogParser
import ai.jessejohnston.bookmend.data.DeviceFamily
import ai.jessejohnston.bookmend.data.RecoveryRepository
import ai.jessejohnston.bookmend.ui.screens.AfterFlashGuide
import ai.jessejohnston.bookmend.ui.screens.DriveCheck
import ai.jessejohnston.bookmend.ui.screens.DriveItem
import ai.jessejohnston.bookmend.ui.screens.DrivePicker
import ai.jessejohnston.bookmend.ui.screens.driveName
import ai.jessejohnston.bookmend.usb.DriveSize
import ai.jessejohnston.bookmend.ui.screens.DriveScreen
import ai.jessejohnston.bookmend.ui.screens.EraseScreen
import ai.jessejohnston.bookmend.ui.screens.FamilyScreen
import ai.jessejohnston.bookmend.ui.screens.FlashScreen
import ai.jessejohnston.bookmend.ui.screens.HoldToConfirmButton
import ai.jessejohnston.bookmend.ui.screens.IdentifyScreen
import ai.jessejohnston.bookmend.ui.screens.LicensesScreen
import ai.jessejohnston.bookmend.ui.screens.ModelBrowserScreen
import ai.jessejohnston.bookmend.ui.screens.VerificationChip
import ai.jessejohnston.bookmend.ui.screens.WelcomeScreen
import ai.jessejohnston.bookmend.ui.theme.BookmendTheme
import ai.jessejohnston.bookmend.usb.ExpectedDigest
import ai.jessejohnston.bookmend.usb.UsbFlasher
import ai.jessejohnston.bookmend.usb.fake.BytesSource
import ai.jessejohnston.bookmend.usb.fake.FakeDriveConnector
import ai.jessejohnston.bookmend.usb.fake.FakeMassStorage
import ai.jessejohnston.bookmend.usb.fake.imageBytes
import ai.jessejohnston.bookmend.usb.fake.sha1Hex
import ai.jessejohnston.bookmend.usb.fake.zipOf
import android.content.Context
import android.hardware.usb.UsbDevice
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow

/**
 * Every screen rendered in the brand colors, its states, and the taps that
 * move the wizard on. `./gradlew recordRoborazziDebug` saves the captures
 * (the README's screenshots come from here).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class ScreensTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private val chromeOs = CatalogParser.chromeOs(CatalogFixtures.chromeOs, false)
    private val googlebooks = CatalogParser.googlebook(CatalogFixtures.googlebook)

    private fun show(content: @Composable () -> Unit) =
        compose.setContent {
            BookmendTheme(dynamicColor = false) {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() }
            }
        }

    /**
     * Draws the screen for real (so drawing code runs and counts) and saves it
     * under build/outputs/roborazzi, where the README's screenshots come from.
     */
    private fun capture(name: String) =
        compose.onRoot().captureRoboImage(
            "build/outputs/roborazzi/$name.png",
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record)
        )

    @Test
    fun welcome() {
        var started = false
        var file = false
        show { WelcomeScreen(onStart = { started = true }, onUseFile = { file = true }) }
        compose.onNodeWithText(s(R.string.welcome_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.welcome_need_title)).assertIsDisplayed()
        capture("welcome")
        compose.onNodeWithText(s(R.string.welcome_start)).performClick()
        compose.onNodeWithText(s(R.string.menu_use_file)).performClick()
        assertTrue(started && file)
    }

    @Test
    fun family() {
        var chosen: DeviceFamily? = null
        show { FamilyScreen(selected = DeviceFamily.CHROMEBOOK, onChoose = { chosen = it }) }
        capture("family")
        compose.onNodeWithText(s(R.string.family_googlebook)).performClick()
        assertEquals(DeviceFamily.GOOGLEBOOK, chosen)
    }

    @Test
    fun identifyWhileTheCatalogLoads() {
        show { IdentifyScreen(CatalogState.Loading, { IdentifyResult.NoMatch(it) }, {}, {}, {}) }
        compose.onNode(hasText(s(R.string.identify_field))).performTextInput("SPHERION")
        compose.onNodeWithText(s(R.string.identify_browse)).assertIsDisplayed()
    }

    @Test
    fun identifyFindsAMatch() {
        var found: String? = null
        show {
            IdentifyScreen(CatalogState.Ready(chromeOs), { IdentifyResult.Match("Acer Chromebook 514") }, {}, { found = it }, {})
        }
        compose.onNode(hasText(s(R.string.identify_field))).performTextInput("SPHERION C4B")
        capture("identify")
        compose.onNodeWithText(s(R.string.identify_find)).performClick()
        assertEquals("Acer Chromebook 514", found)
    }

    @Test
    fun identifyExplainsAMiss() {
        show { IdentifyScreen(CatalogState.Ready(chromeOs), { IdentifyResult.NoMatch(it.trim()) }, {}, {}, {}) }
        compose.onNode(hasText(s(R.string.identify_field))).performTextInput("NOPE")
        compose.onNodeWithText(s(R.string.identify_find)).performClick()
        compose.onNodeWithText(s(R.string.identify_no_match, "NOPE")).assertIsDisplayed()
    }

    @Test
    fun identifyListsSeveralMatches() {
        var found: String? = null
        var browsed = false
        show {
            IdentifyScreen(CatalogState.Ready(chromeOs), { IdentifyResult.Several(listOf("One", "Two")) }, {}, { found = it }, { browsed = true })
        }
        compose.onNode(hasText(s(R.string.identify_field))).performTextInput("TWIN")
        compose.onNodeWithText(s(R.string.identify_find)).performClick()
        compose.onNodeWithText(s(R.string.identify_several)).assertIsDisplayed()
        compose.onNodeWithText("Two").performClick()
        compose.onNodeWithText(s(R.string.identify_browse)).performClick()
        assertEquals("Two", found)
        assertTrue(browsed)
    }

    @Test
    fun identifyOffersARetryOffline() {
        var retried = false
        show { IdentifyScreen(CatalogState.Unavailable, { IdentifyResult.NoMatch(it) }, { retried = true }, {}, {}) }
        compose.onNodeWithText(s(R.string.identify_find)).assertIsNotEnabled()
        compose.onNodeWithText(s(R.string.action_try_again)).performClick()
        assertTrue(retried)
    }

    @Test
    fun browserLoadingAndOffline() {
        var retried = false
        val state = androidx.compose.runtime.mutableStateOf<CatalogState>(CatalogState.Loading)
        show { ModelBrowserScreen(DeviceFamily.CHROMEBOOK, state.value, null, null, { retried = true }, {}) }
        compose.onNodeWithText(s(R.string.models_loading)).assertIsDisplayed()
        state.value = CatalogState.Unavailable
        compose.onNodeWithText(s(R.string.models_unavailable_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.action_try_again)).performClick()
        assertTrue(retried)
    }

    @Test
    fun browserPicksAModelAndAVersion() {
        val many = chromeOs + (1..20).map { chromeOs.first().copy(name = "Filler $it", url = "https://x/$it.zip", manufacturer = "Zeta") }
        var chosen: ai.jessejohnston.bookmend.data.CatalogImage? = null
        show { ModelBrowserScreen(DeviceFamily.CHROMEBOOK, CatalogState.Ready(many), null, null, {}, { chosen = it }) }
        compose.onNodeWithText("Acer").assertIsDisplayed()
        capture("models")
        compose.onNode(hasText(s(R.string.models_search))).performTextInput("514")
        compose.onNodeWithText("Acer Chromebook 514 (CB514-2H, CB514-2HT)").performClick()
        compose.onNodeWithText(s(R.string.models_channel_recommended)).assertIsDisplayed()
        capture("versions")
        compose.onNodeWithText(s(R.string.models_channel_beta)).performClick()
        compose.onNodeWithText(s(R.string.models_select_version)).performClick()
        assertEquals("BETA", chosen?.channel)
    }

    @Test
    fun browserFindsNothingForANonsenseSearch() {
        val many = chromeOs + (1..20).map { chromeOs.first().copy(name = "Filler $it", url = "https://x/$it.zip") }
        show { ModelBrowserScreen(DeviceFamily.CHROMEBOOK, CatalogState.Ready(many), null, null, {}, {}) }
        compose.onNode(hasText(s(R.string.models_search))).performTextInput("qqqq")
        compose.onNodeWithText(s(R.string.models_none_found)).assertIsDisplayed()
    }

    @Test
    fun browserOpensAnExactMatchAndShowsGooglebookSizes() {
        show {
            ModelBrowserScreen(DeviceFamily.GOOGLEBOOK, CatalogState.Ready(googlebooks), "HP Googlebook 14", googlebooks.last(), {}, {})
        }
        compose.onNodeWithText(s(R.string.models_size_line, "7.7 GB", "32 GB")).assertIsDisplayed()
    }

    @Test
    fun googlebookListExplainsMissingModels() {
        show { ModelBrowserScreen(DeviceFamily.GOOGLEBOOK, CatalogState.Ready(googlebooks), null, null, {}, {}) }
        compose.onNodeWithText(s(R.string.models_googlebook_note)).assertIsDisplayed()
        capture("googlebooks")
    }

    @Test
    fun driveWaitsForADrive() {
        show { DriveScreen(requiredBytes = 19_069_493_760, targetName = "Acer Googlebook 14", eraseOnly = false, onConfirmed = {}) }
        compose.onNodeWithText(s(R.string.drive_requirement, "32 GB")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.drive_empty_title)).assertIsDisplayed()
        capture("drive")
    }

    @Test
    fun drivesShowWhatTheyAre() {
        val selected = mutableListOf<String>()
        val drives = listOf(
            DriveItem("a", "SanDisk Ultra", DriveCheck.NotChecked),
            DriveItem("b", "Kingston DataTraveler", DriveCheck.Checking),
            DriveItem("c", "Samsung BAR", DriveCheck.Done(DriveSize.Known(64_000_000_000))),
            DriveItem("d", "Old stick", DriveCheck.Done(DriveSize.Known(4_000_000_000))),
            DriveItem("e", "Card reader", DriveCheck.Done(DriveSize.Unknown)),
            DriveItem("f", "Busy drive", DriveCheck.Done(DriveSize.Unusable("Another app is using it."))),
        )
        show {
            DrivePicker(drives, 4_500_000_000, "Acer Chromebook 514", false, null, false, { selected += it }, {}, {}, {})
        }
        for (text in listOf(s(R.string.drive_tap_to_check), s(R.string.drive_checking), "64 GB", s(R.string.drive_too_small, "4 GB"), "Another app is using it.")) {
            assertTrue(text, compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
        capture("drives")
        compose.onAllNodesWithText("SanDisk Ultra")[0].performClick()
        assertEquals(listOf("a"), selected)
    }

    @Test
    fun theEraseSheetNamesTheDriveAndNeedsAHold() {
        var confirmed: String? = null
        var dismissed = 0
        val drive = DriveItem("c", "Samsung BAR", DriveCheck.Done(DriveSize.Known(64_000_000_000)))
        show { DrivePicker(listOf(drive), null, "Acer Chromebook 514", false, "c", false, {}, { confirmed = it }, { dismissed++ }, {}) }
        compose.onNodeWithText(s(R.string.drive_confirm_title, "Samsung BAR (64 GB)")).assertExists()
        compose.onNodeWithText(s(R.string.drive_confirm_body, "Acer Chromebook 514")).assertExists()
        capture("confirm")
        compose.onNode(hasText(s(R.string.drive_confirm_hold))).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(s(R.string.drive_cancel)).performClick()
        assertEquals("c", confirmed)
        assertEquals(1, dismissed)
    }

    @Test
    fun aRefusedPermissionIsExplained() {
        var dismissed = false
        show { DrivePicker(emptyList(), null, null, true, null, true, {}, {}, {}, { dismissed = true }) }
        compose.onNodeWithText(s(R.string.permission_denied_usb)).assertExists()
        compose.onNodeWithText(s(R.string.action_ok)).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun driveNamesComeFromTheDrive() {
        assertEquals("SanDisk Ultra", driveName(" SanDisk ", "Ultra", "USB drive"))
        assertEquals("Kingston", driveName("Kingston", "Kingston", "USB drive"))
        assertEquals("USB drive", driveName(null, "  ", "USB drive"))
    }

    @Test
    fun aGoodEraseSaysTheDriveIsClear() {
        val drive = FakeMassStorage(65_536)
        show { EraseScreen(device, onFinish = {}, flasher = { app -> UsbFlasher(app, FakeDriveConnector(drive), BytesSource(ByteArray(0), false)) }) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(s(R.string.erase_success)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(s(R.string.erase_success_detail)).assertExists()
    }

    @Test
    fun holdToConfirmRunsOnAFullHoldOrForAScreenReader() {
        var confirmed = 0
        show { HoldToConfirmButton("Hold", "Keep holding", "Erase", onConfirm = { confirmed++ }, holdMillis = 600) }
        compose.onNode(hasText("Hold")).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, confirmed)

        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Hold")).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Keep holding").assertIsDisplayed()
        compose.onNodeWithText("Keep holding").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(400)
        assertEquals("let go early: nothing happens", 1, confirmed)

        compose.onNode(hasText("Hold")).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(2, confirmed)
    }

    @Test
    fun guidesUseEachFamilysKeys() {
        show {
            androidx.compose.foundation.layout.Column {
                VerificationChip(UsbFlasher.Verification.AUTHENTIC)
                VerificationChip(UsbFlasher.Verification.WRITE_VERIFIED)
                VerificationChip(UsbFlasher.Verification.SKIPPED)
                AfterFlashGuide(DeviceFamily.GOOGLEBOOK)
                AfterFlashGuide(DeviceFamily.CHROMEBOOK)
            }
        }
        compose.onNodeWithText(s(R.string.flash_verified_chip)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.guide_keys_googlebook)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.guide_keys_chromebook)).assertExists()
    }

    @Test
    fun licensesShowTheirText() {
        show { LicensesScreen() }
        compose.onNodeWithText("T-Rex runner sprites").assertExists()
        compose.onAllNodesWithText(s(R.string.licenses_show))[0].performClick()
        compose.onNodeWithText("TERMS AND CONDITIONS FOR USE", substring = true).assertExists()
        compose.onNodeWithText(s(R.string.licenses_hide)).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.licenses_hide)).assertDoesNotExist()
    }

    @Test
    fun stepsReadAsOnePhrase() {
        show { WizardSteps(current = 2) }
        compose.onNode(hasContentDescription(s(R.string.step_progress_description, 2, 3, s(R.string.step_label_drive)))).assertExists()
    }

    private val device: UsbDevice = Shadow.newInstanceOf(UsbDevice::class.java).also {
        org.robolectric.util.ReflectionHelpers.setField(it, "mName", "/dev/bus/usb/001/002")
    }

    @Test
    fun aFinishedFlashShowsTheGuide() {
        val image = imageBytes(100_000)
        val zip = zipOf("image.bin" to image)
        val request = FlashRequest(device, "https://x/r.zip", ExpectedDigest.sha1(sha1Hex(zip)), image.size.toLong(), DeviceFamily.CHROMEBOOK, "Acer Chromebook 514")
        show {
            FlashScreen(request, onFinish = {}, flasher = { app -> UsbFlasher(app, FakeDriveConnector(FakeMassStorage(4096)), BytesSource(zip, true)) })
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(s(R.string.flash_verified_chip)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(s(R.string.guide_title, s(R.string.guide_device_chromebook))).assertExists()
        capture("ready")
    }

    @Test
    fun aFailedFlashOffersTheWayBack() {
        var home = false
        val request = FlashRequest(device, "https://x/r.zip", null, null, DeviceFamily.CHROMEBOOK, null)
        show {
            FlashScreen(request, onFinish = { home = true }, flasher = { app ->
                UsbFlasher(app, FakeDriveConnector(FakeMassStorage(4096).apply { failWriteAtLba = 0 }), BytesSource(imageBytes(100_000), false))
            })
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(s(R.string.action_back_to_home)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(s(R.string.action_back_to_home)).performClick()
        assertTrue(home)
    }

    @Test
    fun theGameIsThereWhileYouWait() {
        val request = FlashRequest(device, "https://x/r.bin", null, null, DeviceFamily.CHROMEBOOK, null)
        // A download that trickles until the test is done, then drops for good,
        // so no flash outlives the test.
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val stall = object : ai.jessejohnston.bookmend.usb.ImageSourceOpener {
            override fun open(url: String, onStep: (String) -> Unit) = ai.jessejohnston.bookmend.usb.SourceResult.Ok(
                ai.jessejohnston.bookmend.usb.OpenedSource(object : java.io.InputStream() {
                    override fun read(): Int = read(ByteArray(1), 0, 1)
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (done.get()) throw java.io.IOException("test over")
                        Thread.sleep(20); b[off] = 0; return 1
                    }
                }, -1, isZip = false)
            )
        }
        compose.mainClock.autoAdvance = false
        show { FlashScreen(request, onFinish = {}, flasher = { app -> UsbFlasher(app, FakeDriveConnector(FakeMassStorage(4096)), stall) }) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(s(R.string.flash_play_game)).performClick()
        repeat(30) { compose.mainClock.advanceTimeByFrame() }
        capture("flashing")
        compose.onNodeWithText(s(R.string.flash_cancel_and_reset)).performClick()
        val deadline = System.currentTimeMillis() + 10_000
        while (compose.onAllNodesWithText(s(R.string.flash_reset_success)).fetchSemanticsNodes().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            compose.mainClock.advanceTimeByFrame()
        }
        done.set(true)
        compose.onNodeWithText(s(R.string.flash_reset_success)).assertExists()
    }

    @Test
    fun aBadCheckOffersATryAgainThatWorks() {
        val image = imageBytes(100_000)
        val drive = FakeMassStorage(4096).apply { corruptReadAtLba = 3 }
        val request = FlashRequest(device, "https://x/r.bin", null, null, DeviceFamily.CHROMEBOOK, null)
        show { FlashScreen(request, onFinish = {}, flasher = { app -> UsbFlasher(app, FakeDriveConnector(drive), BytesSource(image, false)) }) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(s(R.string.flash_retry)).fetchSemanticsNodes().isNotEmpty() }
        drive.corruptReadAtLba = null
        compose.onNodeWithText(s(R.string.flash_retry)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(s(R.string.flash_checked_chip)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theCheckCanBeSkipped() {
        val image = imageBytes(400_000)
        val drive = FakeMassStorage(4096)
        // Reads crawl, so the check is still running when Skip is tapped.
        val slowReads = object : ai.jessejohnston.bookmend.usb.bot.BulkTransport {
            override fun bulkOut(buffer: ByteArray, length: Int, timeoutMs: Int): Int {
                if (length == 31 && buffer[15].toInt() == FakeMassStorage.OP_READ10) Thread.sleep(40)
                return drive.bulkOut(buffer, length, timeoutMs)
            }
            override fun bulkIn(buffer: ByteArray, length: Int, timeoutMs: Int) = drive.bulkIn(buffer, length, timeoutMs)
        }
        val request = FlashRequest(device, "https://x/r.bin", null, null, DeviceFamily.CHROMEBOOK, null)
        show {
            FlashScreen(request, onFinish = {}, flasher = { app ->
                UsbFlasher(app, { ai.jessejohnston.bookmend.usb.Claim.Ok(ai.jessejohnston.bookmend.usb.bot.BotDevice(slowReads)) {} }, BytesSource(image, false))
            })
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(s(R.string.flash_skip_verification)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(s(R.string.flash_skip_verification)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(s(R.string.flash_success)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun wizardWith(vararg catalogs: Pair<String, String>) = WizardViewModel(
        SavedStateHandle(),
        RecoveryRepository(CatalogFetcher { url -> catalogs.toMap()[url] ?: throw IOException("offline") }, Dispatchers.Unconfined)
    )

    @Test
    fun aChromebookIsFoundByItsHardwareId() {
        val wizard = wizardWith(RecoveryRepository.CHROMEOS_URL to CatalogFixtures.chromeOs)
        show { RecoveryApp(wizard = wizard) }
        compose.onNodeWithText(s(R.string.welcome_start)).performClick()
        compose.onNodeWithText(s(R.string.family_chromebook)).performClick()
        compose.onNode(hasText(s(R.string.identify_field))).performTextInput("SPHERION C4B")
        compose.onNodeWithText(s(R.string.identify_find)).performClick()
        compose.onNodeWithText(s(R.string.models_channel_recommended)).assertExists()
        compose.onNode(hasContentDescription(s(R.string.action_back))).performClick()
        compose.onNode(hasContentDescription(s(R.string.action_back))).performClick()
        compose.onNodeWithText(s(R.string.identify_browse)).performClick()
        compose.onNodeWithText("Lenovo").assertExists()
    }

    @Test
    fun theFlashAndEraseRoutesNeedTheirChoices() {
        val wizard = wizardWith(RecoveryRepository.GOOGLEBOOK_URL to CatalogFixtures.googlebook)
        lateinit var nav: androidx.navigation.NavHostController
        show {
            nav = androidx.navigation.compose.rememberNavController()
            RecoveryApp(wizard = wizard, navController = nav)
        }
        // No drive chosen: both routes step straight back.
        compose.runOnUiThread { nav.navigate(Route.Flash.pattern) }
        compose.waitForIdle()
        compose.runOnUiThread { nav.navigate(Route.EraseFlash.pattern) }
        compose.waitForIdle()
        compose.onNodeWithText(s(R.string.welcome_title)).assertExists()

        // With choices, the flash runs (and fails on this test's empty USB bus), and Close goes home.
        wizard.chooseFamily(DeviceFamily.GOOGLEBOOK)
        wizard.chooseImage((wizard.catalog.value as CatalogState.Ready).images.first())
        wizard.chooseDrive(device)
        compose.runOnUiThread { nav.navigate(Route.Flash.pattern) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(s(R.string.action_back_to_home)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription(s(R.string.action_home))).performClick()
        compose.onNodeWithText(s(R.string.welcome_title)).assertExists()

        wizard.chooseDrive(device)
        compose.runOnUiThread { nav.navigate(Route.EraseFlash.pattern) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(s(R.string.action_back_to_home)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun eraseRendersAndReportsADriveItCannotOpen() {
        show { EraseScreen(device = device, onFinish = {}) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(s(R.string.action_back_to_home)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theWholeWizardWalksToTheDriveStep() {
        val repo = RecoveryRepository(
            CatalogFetcher { url ->
                mapOf(RecoveryRepository.GOOGLEBOOK_URL to CatalogFixtures.googlebook)[url] ?: throw IOException("offline")
            },
            Dispatchers.Unconfined
        )
        val wizard = WizardViewModel(SavedStateHandle(), repo)
        val routes = mutableListOf<Route?>()
        show { RecoveryApp(wizard = wizard, onRouteChanged = { routes += it }) }

        compose.onNodeWithText(s(R.string.welcome_start)).performClick()
        compose.onNodeWithText(s(R.string.family_googlebook)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Acer Googlebook 14").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Acer Googlebook 14").performClick()
        compose.onNodeWithText(s(R.string.models_select_version)).performClick()
        compose.onNodeWithText(s(R.string.drive_title)).assertIsDisplayed()
        compose.onNode(hasContentDescription(s(R.string.action_more))).performClick()
        compose.onNodeWithText(s(R.string.menu_licenses)).performClick()
        compose.onNodeWithText("Figtree").assertExists()
        compose.onNode(hasContentDescription(s(R.string.action_back))).performClick()
        compose.onNode(hasContentDescription(s(R.string.action_more))).performClick()
        compose.onNodeWithText(s(R.string.menu_erase_drive)).performClick()
        compose.waitForIdle()

        val visited = routes.filterNotNull().filterIndexed { i, r -> i == 0 || routes.filterNotNull()[i - 1] != r }
        assertEquals(listOf(Route.Welcome, Route.Family, Route.Models, Route.Drive, Route.Licenses, Route.Drive, Route.EraseDrive), visited)
    }
}
