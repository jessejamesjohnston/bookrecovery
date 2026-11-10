package ai.jessejohnston.bookmend.usb

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.usb.fake.BytesSource
import ai.jessejohnston.bookmend.usb.fake.FakeDriveConnector
import ai.jessejohnston.bookmend.usb.fake.FakeMassStorage
import android.content.Context
import android.hardware.usb.UsbDevice
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow

/** Erase zeroes the partition structures at both ends and leaves the middle alone. */
@RunWith(RobolectricTestRunner::class)
class EraseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val device: UsbDevice = Shadow.newInstanceOf(UsbDevice::class.java)

    private val mib = 2048L // sectors per MiB

    private suspend fun erase(drive: FakeMassStorage): Pair<UsbFlasher.EraseResult, List<Float>> {
        val progress = mutableListOf<Float>()
        val flasher = UsbFlasher(context, FakeDriveConnector(drive), BytesSource(ByteArray(0), false))
        return flasher.eraseDevice(device) { progress += it } to progress
    }

    private fun zeroed(drive: FakeMassStorage, lba: Long) =
        drive.isWritten(lba) && drive.read(lba, 512).all { it == 0.toByte() }

    @Test
    fun zeroesTheHeadAndTheBackupGptOnly() = runTest {
        val drive = FakeMassStorage(totalSectors = 64 * mib)
        val (result, progress) = erase(drive)

        assertEquals(UsbFlasher.EraseResult.Done, result)
        assertTrue(zeroed(drive, 0))
        assertTrue(zeroed(drive, 16 * mib - 1))
        assertFalse("past the head stays untouched", drive.isWritten(16 * mib))
        assertFalse(drive.isWritten(63 * mib - 1))
        assertTrue(zeroed(drive, 63 * mib))
        assertTrue(zeroed(drive, 64 * mib - 1))
        assertEquals(1f, progress.last())
    }

    @Test
    fun withoutACapacityOnlyTheHeadIsWiped() = runTest {
        val drive = FakeMassStorage(totalSectors = 64 * mib).apply { capacityFails = true }
        val (result, _) = erase(drive)

        assertEquals(UsbFlasher.EraseResult.Done, result)
        assertTrue(zeroed(drive, 16 * mib - 1))
        assertFalse(drive.isWritten(64 * mib - 1))
    }

    @Test
    fun aDriveSmallerThanTheHeadIsWipedWhole() = runTest {
        val drive = FakeMassStorage(totalSectors = 8 * mib)
        val (result, _) = erase(drive)

        assertEquals(UsbFlasher.EraseResult.Done, result)
        assertTrue(zeroed(drive, 8 * mib - 1))
        assertEquals(
            "no write past the end",
            0,
            drive.commands.count { it.first == FakeMassStorage.OP_WRITE10 && it.second + it.third > 8 * mib }
        )
    }

    @Test
    fun aFailedWriteIsReported() = runTest {
        val drive = FakeMassStorage(totalSectors = 64 * mib).apply { failWriteAtLba = 5 }
        val (result, _) = erase(drive)
        assertEquals(UsbFlasher.EraseResult.Error(context.getString(R.string.error_erase_failed)), result)
    }

    @Test
    fun aFailedTailWriteIsReported() = runTest {
        val drive = FakeMassStorage(totalSectors = 64 * mib).apply { failWriteAtLba = 64 * mib - 1 }
        val (result, _) = erase(drive)
        assertEquals(UsbFlasher.EraseResult.Error(context.getString(R.string.error_erase_failed)), result)
    }

    @Test
    fun aDriveThatCannotBeClaimedReportsWhy() = runTest {
        val flasher = UsbFlasher(context, { Claim.Failed("not mass storage") }, BytesSource(ByteArray(0), false))
        assertEquals(UsbFlasher.EraseResult.Error("not mass storage"), flasher.eraseDevice(device) {})
    }
}
