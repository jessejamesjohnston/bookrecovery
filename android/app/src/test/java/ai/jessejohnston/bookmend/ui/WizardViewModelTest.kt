package ai.jessejohnston.bookmend.ui

import ai.jessejohnston.bookmend.data.CatalogFetcher
import ai.jessejohnston.bookmend.data.CatalogFixtures
import ai.jessejohnston.bookmend.data.DeviceFamily
import ai.jessejohnston.bookmend.data.RecoveryRepository
import ai.jessejohnston.bookmend.usb.ExpectedDigest
import android.hardware.usb.UsbDevice
import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WizardViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val bodies = mutableMapOf(
        RecoveryRepository.CHROMEOS_URL to CatalogFixtures.chromeOs,
        RecoveryRepository.FLEX_URL to CatalogFixtures.flex,
        RecoveryRepository.GOOGLEBOOK_URL to CatalogFixtures.googlebook,
    )
    private val repo = RecoveryRepository(CatalogFetcher { bodies[it] ?: throw IOException("offline") }, dispatcher)
    private val device: UsbDevice = Shadow.newInstanceOf(UsbDevice::class.java)

    @Before fun main() = Dispatchers.setMain(dispatcher)
    @After fun reset() = Dispatchers.resetMain()

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = WizardViewModel(saved, repo)

    @Test
    fun choosingAFamilyLoadsItsCatalog() {
        val vm = vm()
        assertNull(vm.family.value)
        vm.chooseFamily(DeviceFamily.GOOGLEBOOK)

        assertEquals(DeviceFamily.GOOGLEBOOK, vm.family.value)
        assertEquals(2, (vm.catalog.value as CatalogState.Ready).images.size)
    }

    @Test
    fun anUnreachableCatalogCanBeRetried() {
        bodies.clear()
        val vm = vm()
        vm.chooseFamily(DeviceFamily.GOOGLEBOOK)
        assertEquals(CatalogState.Unavailable, vm.catalog.value)

        bodies[RecoveryRepository.GOOGLEBOOK_URL] = CatalogFixtures.googlebook
        vm.retryCatalog()
        assertTrue(vm.catalog.value is CatalogState.Ready)
    }

    @Test
    fun hardwareIdsResolveToOneSeveralOrNone() {
        val vm = vm().apply { chooseFamily(DeviceFamily.CHROMEBOOK) }
        assertEquals(IdentifyResult.Match("Acer Chromebook 514 (CB514-2H, CB514-2HT)"), vm.identify("spherion x"))
        assertEquals(IdentifyResult.NoMatch("ZZZ"), vm.identify(" ZZZ "))

        val many = vm.catalog.value as CatalogState.Ready
        assertTrue(many.images.isNotEmpty())
    }

    @Test
    fun severalMatchesAreListed() {
        bodies[RecoveryRepository.CHROMEOS_URL] = """
            [{"name":"One","manufacturer":"A","hwidmatch":"^TWIN.*","url":"https://x/1.zip"},
             {"name":"Two","manufacturer":"A","hwidmatch":"^TWIN.*","url":"https://x/2.zip"}]
        """.trimIndent()
        val vm = vm().apply { chooseFamily(DeviceFamily.CHROMEBOOK) }
        assertEquals(IdentifyResult.Several(listOf("One", "Two")), vm.identify("TWIN-A"))
    }

    @Test
    fun aCatalogChoiceBecomesAFlashRequestWithItsChecksum() {
        val vm = vm().apply { chooseFamily(DeviceFamily.GOOGLEBOOK) }
        val acer = (vm.catalog.value as CatalogState.Ready).images.first { it.manufacturer == "Acer" }
        assertNull("no drive yet", vm.flashRequest())

        vm.chooseImage(acer)
        vm.chooseDrive(device)
        val request = vm.flashRequest()!!

        assertEquals(acer.url, request.url)
        assertEquals(ExpectedDigest.md5("b478bb5f4c3ca8512e747897e40f69e3"), request.digest)
        assertEquals(19069493760L, request.imageBytes)
        assertEquals(DeviceFamily.GOOGLEBOOK, request.family)
        assertEquals("Acer Googlebook 14", request.modelName)
    }

    @Test
    fun aLocalFileFlashesWithoutAChecksum() {
        val vm = vm().apply { chooseLocalImage("content://files/image.bin"); chooseDrive(device) }
        val request = vm.flashRequest()!!
        assertEquals("content://files/image.bin", request.url)
        assertNull(request.digest)
        assertNull(request.imageBytes)
    }

    @Test
    fun choicesSurviveProcessDeath() {
        val saved = SavedStateHandle()
        val first = vm(saved).apply { chooseFamily(DeviceFamily.CHROMEBOOK) }
        val pick = (first.catalog.value as CatalogState.Ready).images.first { it.channel == "BETA" }
        first.chooseImage(pick)

        val revived = vm(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        assertEquals(DeviceFamily.CHROMEBOOK, revived.family.value)
        assertEquals(pick, revived.image.value)
    }

    @Test
    fun switchingFamiliesForgetsTheImage() {
        val vm = vm().apply { chooseFamily(DeviceFamily.CHROMEBOOK) }
        vm.chooseImage((vm.catalog.value as CatalogState.Ready).images.first())
        vm.chooseFamily(DeviceFamily.GOOGLEBOOK)
        assertNull(vm.image.value)
    }

    @Test
    fun resetClearsChoicesButKeepsTheFamily() {
        val vm = vm().apply { chooseFamily(DeviceFamily.GOOGLEBOOK) }
        vm.chooseImage((vm.catalog.value as CatalogState.Ready).images.first())
        vm.chooseDrive(device)
        vm.reset()
        assertNull(vm.image.value)
        assertNull(vm.drive.value)
        assertEquals(DeviceFamily.GOOGLEBOOK, vm.family.value)
    }

    @Test
    fun aHandedOffChoiceLandsOnTheSameModel() {
        val sender = vm().apply { chooseFamily(DeviceFamily.GOOGLEBOOK) }
        val hp = (sender.catalog.value as CatalogState.Ready).images.first { it.manufacturer == "HP" }
        sender.chooseImage(hp)
        val extras = sender.handoffExtras()

        val receiver = vm()
        assertTrue(receiver.restoreHandoff(extras.getString(WizardViewModel.KEY_FAMILY), extras.getString(WizardViewModel.KEY_IMAGE)))
        assertEquals(hp, receiver.image.value)
        assertEquals(Route.Models.withQuery("HP Googlebook 14"), receiver.handoffRoute.value)
        receiver.handoffShown()
        assertNull(receiver.handoffRoute.value)
    }

    @Test
    fun aHandoffWithoutAFamilyIsIgnored() {
        val vm = vm()
        assertEquals(false, vm.restoreHandoff(null, null))
        assertEquals(false, vm.restoreHandoff("TOASTER", null))
        assertNull(vm.handoffRoute.value)
    }

    @Test
    fun aFamilyOnlyHandoffOpensTheBrowser() {
        val vm = vm()
        vm.restoreHandoff("CHROMEBOOK", null)
        assertEquals(Route.Models.withQuery(), vm.handoffRoute.value)
    }
}
