package ai.jessejohnston.bookmend.usb

import ai.jessejohnston.bookmend.usb.fake.FakeDriveConnector
import ai.jessejohnston.bookmend.usb.fake.FakeMassStorage
import android.hardware.usb.UsbDevice
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
class DriveInspectorTest {

    private val device: UsbDevice = Shadow.newInstanceOf(UsbDevice::class.java)

    @Test
    fun aDriveReportsItsSizeAndIsReleased() = runTest {
        val connector = FakeDriveConnector(FakeMassStorage(totalSectors = 62_500_000))
        val size = DriveInspector(connector, StandardTestDispatcher(testScheduler)).capacity(device)
        assertEquals(DriveSize.Known(32_000_000_000), size)
        assertEquals(1, connector.releases)
    }

    @Test
    fun aDriveThatWontSayIsUnknown() = runTest {
        val connector = FakeDriveConnector(FakeMassStorage(totalSectors = 64).apply { capacityFails = true })
        assertEquals(DriveSize.Unknown, DriveInspector(connector, StandardTestDispatcher(testScheduler)).capacity(device))
        assertEquals(1, connector.releases)
    }

    @Test
    fun aDriveThatCantBeOpenedSaysWhy() = runTest {
        val inspector = DriveInspector({ Claim.Failed("busy") }, StandardTestDispatcher(testScheduler))
        assertEquals(DriveSize.Unusable("busy"), inspector.capacity(device))
    }
}
