package ai.jessejohnston.bookmend.usb

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.usb.fake.BytesSource
import ai.jessejohnston.bookmend.usb.fake.FakeDriveConnector
import ai.jessejohnston.bookmend.usb.fake.FakeMassStorage
import ai.jessejohnston.bookmend.usb.fake.imageBytes
import ai.jessejohnston.bookmend.usb.fake.sha1Hex
import ai.jessejohnston.bookmend.usb.fake.zipOf
import android.content.Context
import android.hardware.usb.UsbDevice
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow

/**
 * The whole flash path, end to end, against a simulated drive: download (or
 * unzip), write, authenticity check and read-back. The bar is the one that
 * matters for a tool that erases drives: the bytes on the drive are the image,
 * and every failure stops with the message the person will actually read.
 */
@RunWith(RobolectricTestRunner::class)
class UsbFlasherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val device: UsbDevice = Shadow.newInstanceOf(UsbDevice::class.java)

    /** Not a multiple of 512, and longer than one 64 KB write, so padding and chunking both run. */
    private val image = imageBytes(3 * 65536 + 700)

    private fun drive(sectors: Long = 4096) = FakeMassStorage(totalSectors = sectors)

    private suspend fun flash(
        drive: FakeMassStorage,
        source: BytesSource,
        expectedSha1: String? = null,
        onStep: (UsbFlasher, String) -> Unit = { _, _ -> }
    ): Pair<UsbFlasher.Result, FakeDriveConnector> {
        val connector = FakeDriveConnector(drive)
        val flasher = UsbFlasher(context, connector, source)
        val result = flasher.flashImageToUsb(
            device = device,
            url = "https://example.test/image.zip",
            expectedZipSha1 = expectedSha1,
            onStep = { onStep(flasher, it) },
            onProgress = {}
        )
        return result to connector
    }

    @Test
    fun rawImageLandsOnTheDriveByteForByte() = runTest {
        val drive = drive()
        val (result, connector) = flash(drive, BytesSource(image, isZip = false))

        assertEquals(UsbFlasher.Result.Success(UsbFlasher.Verification.WRITE_VERIFIED), result)
        assertArrayEquals(image, drive.read(0, image.size))
        val tail = drive.read(0, image.size + 512 - image.size % 512).copyOfRange(image.size, image.size + 512 - image.size % 512)
        assertTrue("final sector is zero-padded", tail.all { it == 0.toByte() })
        assertEquals(1, connector.releases)
    }

    @Test
    fun zippedImageWithMatchingChecksumIsAuthentic() = runTest {
        val zip = zipOf("readme.txt" to "hi".toByteArray(), "chromeos_recovery.bin" to image)
        val drive = drive()
        val (result, _) = flash(drive, BytesSource(zip, isZip = true), expectedSha1 = sha1Hex(zip))

        assertEquals(UsbFlasher.Result.Success(UsbFlasher.Verification.AUTHENTIC), result)
        assertArrayEquals(image, drive.read(0, image.size))
    }

    @Test
    fun checksumIsCaseInsensitive() = runTest {
        val zip = zipOf("image.bin" to image)
        val (result, _) = flash(drive(), BytesSource(zip, isZip = true), expectedSha1 = sha1Hex(zip).uppercase())
        assertEquals(UsbFlasher.Result.Success(UsbFlasher.Verification.AUTHENTIC), result)
    }

    @Test
    fun checksumMismatchIsAVerificationError() = runTest {
        val zip = zipOf("image.bin" to image)
        val (result, _) = flash(drive(), BytesSource(zip, isZip = true), expectedSha1 = "0".repeat(40))
        assertEquals(
            UsbFlasher.Result.VerificationError(context.getString(R.string.error_authenticity_failed)),
            result
        )
    }

    @Test
    fun zipWithoutABinIsRejectedBeforeTouchingTheDrive() = runTest {
        val drive = drive()
        val source = BytesSource(zipOf("notes.txt" to image), isZip = true)
        val (result, _) = flash(drive, source)

        assertEquals(UsbFlasher.Result.WriteError(context.getString(R.string.error_invalid_zip)), result)
        assertTrue("no command reached the drive", drive.commands.isEmpty())
    }

    @Test
    fun aFailedSectorWriteStopsWithAHardwareError() = runTest {
        val drive = drive().apply { failWriteAtLba = 10 }
        val (result, connector) = flash(drive, BytesSource(image, isZip = false))
        assertEquals(UsbFlasher.Result.WriteError(context.getString(R.string.error_hardware_write)), result)
        assertEquals(1, connector.releases)
    }

    @Test
    fun aDriveTooSmallForTheImageIsReportedAsAHardwareError() = runTest {
        // Characterizes today's behavior: a drive smaller than the image fails
        // mid-stream with the generic hardware message, after downloading most
        // of the image. The engine extraction replaces this with an up-front
        // capacity check.
        val drive = drive(sectors = 200)
        val (result, _) = flash(drive, BytesSource(image, isZip = false))
        assertEquals(UsbFlasher.Result.WriteError(context.getString(R.string.error_hardware_write)), result)
    }

    @Test
    fun aCorruptReadBackIsAVerificationError() = runTest {
        val drive = drive().apply { corruptReadAtLba = 300 }
        val (result, _) = flash(drive, BytesSource(image, isZip = false))
        assertEquals(
            UsbFlasher.Result.VerificationError(context.getString(R.string.error_verification_failed)),
            result
        )
    }

    @Test
    fun shortTransfersFromTheDriveStillVerify() = runTest {
        val drive = drive().apply { maxInChunk = 7 }
        val (result, _) = flash(drive, BytesSource(image, isZip = false))
        assertEquals(UsbFlasher.Result.Success(UsbFlasher.Verification.WRITE_VERIFIED), result)
    }

    @Test
    fun cancellingBeforeTheWriteLeavesTheDriveUntouched() = runTest {
        val drive = drive()
        val writing = context.getString(R.string.step_writing_usb)
        val source = BytesSource(image, isZip = false)
        val (result, connector) = flash(drive, source) { flasher, step -> if (step == writing) flasher.cancel() }

        assertEquals(UsbFlasher.Result.Cancelled, result)
        assertFalse(drive.isWritten(0))
        assertEquals(1, connector.releases)
        assertTrue("the download stream is closed", source.stream!!.closed)
    }

    @Test
    fun skippingVerificationIsASuccessNotAFailure() = runTest {
        val verifying = context.getString(R.string.step_verifying)
        val (result, _) = flash(drive(), BytesSource(image, isZip = false)) { flasher, step ->
            if (step == verifying) flasher.skipVerification()
        }
        assertEquals(UsbFlasher.Result.Success(UsbFlasher.Verification.SKIPPED), result)
    }

    @Test
    fun aDriveThatCannotBeClaimedReportsWhy() = runTest {
        val flasher = UsbFlasher(context, { Claim.Failed("no permission") }, BytesSource(image, isZip = false))
        val result = flasher.flashImageToUsb(device, "x.bin", onStep = {}, onProgress = {})
        assertEquals(UsbFlasher.Result.WriteError("no permission"), result)
    }

    @Test
    fun aSourceThatCannotBeOpenedReportsWhy() = runTest {
        val flasher = UsbFlasher(context, FakeDriveConnector(drive()), { _, _ -> SourceResult.Failed("HTTP 404") })
        val result = flasher.flashImageToUsb(device, "x.bin", onStep = {}, onProgress = {})
        assertEquals(UsbFlasher.Result.WriteError("HTTP 404"), result)
    }

    @Test
    fun aDeadCableIsAnErrorNotACrash() = runTest {
        val drive = drive().apply { transportDead = true }
        val (result, connector) = flash(drive, BytesSource(image, isZip = false))
        assertEquals(UsbFlasher.Result.WriteError(context.getString(R.string.error_hardware_write)), result)
        assertEquals(1, connector.releases)
    }
}
