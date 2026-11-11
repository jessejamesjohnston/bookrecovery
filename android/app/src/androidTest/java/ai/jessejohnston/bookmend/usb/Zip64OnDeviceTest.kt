package ai.jessejohnston.bookmend.usb

import ai.jessejohnston.bookmend.usb.fixture.Zip64Stream
import ai.jessejohnston.bookmend.usb.fixture.readRecoveryZip
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Googlebook recovery zips put their sizes in a ZIP64 data descriptor after a
 * 19 GB entry, with no ZIP64 field in the local header. JVM tests run desktop
 * OpenJDK's java.util.zip; this runs Android's, which is what ships. A wrong
 * parse would only show at the very end of a real flash, so it is checked here
 * past the 4 GiB line.
 */
@RunWith(AndroidJUnit4::class)
class Zip64OnDeviceTest {

    @Test
    fun androidReadsAZip64DescriptorPastFourGibibytes() {
        val size = 4L * 1024 * 1024 * 1024 + 512L * 1024 * 1024 + 300
        val result = readRecoveryZip(Zip64Stream(size))

        assertEquals("android-desktop_signed_recovery_image.bin", result.entryName)
        assertEquals(size, result.payloadBytes)
        assertEquals(0L, result.nonZeroBytes)
        assertFalse(result.moreEntries)
    }
}
