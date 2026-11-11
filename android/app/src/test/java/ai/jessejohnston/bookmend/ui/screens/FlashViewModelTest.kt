package ai.jessejohnston.bookmend.ui.screens

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.data.DeviceFamily
import ai.jessejohnston.bookmend.ui.FlashRequest
import ai.jessejohnston.bookmend.usb.ExpectedDigest
import ai.jessejohnston.bookmend.usb.ImageSourceOpener
import ai.jessejohnston.bookmend.usb.OpenedSource
import ai.jessejohnston.bookmend.usb.SourceResult
import ai.jessejohnston.bookmend.usb.UsbFlasher
import ai.jessejohnston.bookmend.usb.fake.BytesSource
import ai.jessejohnston.bookmend.usb.fake.FakeDriveConnector
import ai.jessejohnston.bookmend.usb.fake.FakeMassStorage
import ai.jessejohnston.bookmend.usb.fake.imageBytes
import ai.jessejohnston.bookmend.usb.fake.sha1Hex
import ai.jessejohnston.bookmend.usb.fake.zipOf
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.hardware.usb.UsbDevice
import androidx.test.core.app.ApplicationProvider
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow

/** The flash state machine end to end, against a simulated drive. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FlashViewModelTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val device: UsbDevice = Shadow.newInstanceOf(UsbDevice::class.java)
    private val image = imageBytes(200_000)
    private val zip = zipOf("image.bin" to image)

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private fun request(digest: ExpectedDigest? = ExpectedDigest.sha1(sha1Hex(zip))) =
        FlashRequest(device, "https://example.test/r.zip", digest, image.size.toLong(), DeviceFamily.CHROMEBOOK, "Acer 514")

    private fun vm(drive: FakeMassStorage = FakeMassStorage(4096), source: ImageSourceOpener = BytesSource(zip, isZip = true), request: FlashRequest = request()) =
        FlashViewModel(app, request, UsbFlasher(app, FakeDriveConnector(drive), source))

    private fun FlashViewModel.finished(): FlashUiState = runBlocking {
        withTimeout(10_000) { uiState.first { it.isFinished } }
    }

    @Test
    fun aGoodFlashEndsVerifiedAgainstTheCatalog() {
        val vm = vm().apply { start() }
        val state = vm.finished()

        assertFalse(state.hasError)
        assertEquals(UsbFlasher.Verification.AUTHENTIC, state.verification)
        assertEquals(app.getString(R.string.flash_success_verified), state.stepText)
        assertEquals(1f, state.progress)
    }

    @Test
    fun startingTwiceDoesNotStartTwice() {
        val drive = FakeMassStorage(4096)
        val vm = vm(drive).apply { start(); start() }
        vm.finished()
        assertEquals(1, drive.commands.count { it.first == FakeMassStorage.OP_READ_CAPACITY10 })
    }

    @Test
    fun aLocalImageIsWriteVerified() {
        val vm = vm(source = BytesSource(image, isZip = false), request = request(digest = null)).apply { start() }
        assertEquals(UsbFlasher.Verification.WRITE_VERIFIED, vm.finished().verification)
    }

    @Test
    fun aBadReadBackOffersARetryThatCanSucceed() {
        val drive = FakeMassStorage(4096).apply { corruptReadAtLba = 5 }
        val vm = vm(drive).apply { start() }
        val failed = vm.finished()
        assertTrue(failed.hasError)
        assertTrue(failed.canRetry)

        drive.corruptReadAtLba = null
        vm.retryFlash()
        val retried = vm.finished()
        assertFalse(retried.hasError)
        assertEquals(UsbFlasher.Verification.AUTHENTIC, retried.verification)
    }

    @Test
    fun aWriteFailureDoesNotOfferARetry() {
        val vm = vm(FakeMassStorage(4096).apply { failWriteAtLba = 0 }).apply { start() }
        val state = vm.finished()
        assertTrue(state.hasError)
        assertFalse(state.canRetry)
        assertEquals(app.getString(R.string.flash_error, app.getString(R.string.error_hardware_write)), state.stepText)
    }

    @Test
    fun stoppingMidFlashResetsTheDrive() {
        val gate = CountDownLatch(1)
        val drive = FakeMassStorage(65_536)
        val slow = ImageSourceOpener { _, _ ->
            SourceResult.Ok(OpenedSource(GatedStream(imageBytes(2_000_000), gate), 2_000_000, isZip = false))
        }
        val vm = vm(drive, slow, request(digest = null)).apply { start() }
        runBlocking { withTimeout(5_000) { vm.uiState.first { it.stepText == app.getString(R.string.step_writing_usb) } } }

        vm.cancelFlashAndReset()
        gate.countDown()
        val state = vm.finished()

        assertFalse(state.hasError)
        assertEquals(app.getString(R.string.flash_reset_success), state.stepText)
        assertTrue("the head of the drive was zeroed", drive.read(0, 512).all { it == 0.toByte() })
    }

    @Test
    fun aCancelBeforeTheWriteStartsIsNotLost() {
        val drive = FakeMassStorage(4096)
        val flasher = UsbFlasher(app, FakeDriveConnector(drive), BytesSource(image, false))
        flasher.cancel()
        val result = runBlocking { flasher.flashImageToUsb(device, "x.bin", onStep = {}, onProgress = {}) }
        assertEquals(UsbFlasher.Result.Cancelled, result)
        assertFalse(drive.isWritten(0))

        flasher.reset()
        val again = runBlocking { flasher.flashImageToUsb(device, "x.bin", onStep = {}, onProgress = {}) }
        assertEquals(UsbFlasher.Result.Success(UsbFlasher.Verification.WRITE_VERIFIED), again)
    }

    @Test
    fun aFinishWhileAwayPostsANotification() {
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val vm = vm(FakeMassStorage(4096).apply { failWriteAtLba = 0 })
        vm.onBackgroundedChanged(true)
        vm.start()
        vm.finished()
        assertTrue(shadowOf(manager).allNotifications.isNotEmpty())
        vm.onBackgroundedChanged(false)
    }

    /** Serves bytes, but holds the second read until [gate] opens, so a test can act mid-flash. */
    private class GatedStream(private val bytes: ByteArray, private val gate: CountDownLatch) : InputStream() {
        private var pos = 0
        private var reads = 0
        override fun read(): Int = if (pos < bytes.size) bytes[pos++].toInt() and 0xff else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (reads++ == 1) gate.await()
            if (pos >= bytes.size) return -1
            val n = minOf(len, bytes.size - pos, 64 * 1024)
            System.arraycopy(bytes, pos, b, off, n)
            pos += n
            return n
        }
    }
}
