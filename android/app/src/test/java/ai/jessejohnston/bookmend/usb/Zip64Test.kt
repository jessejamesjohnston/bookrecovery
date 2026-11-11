package ai.jessejohnston.bookmend.usb

import ai.jessejohnston.bookmend.usb.bot.BotDevice
import ai.jessejohnston.bookmend.usb.fake.FakeMassStorage
import ai.jessejohnston.bookmend.usb.fixture.Zip64Stream
import ai.jessejohnston.bookmend.usb.fixture.readRecoveryZip
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The Googlebook zip layout, on the JVM. The on-device twin is Zip64OnDeviceTest. */
class Zip64Test {

    private val overFourGib = 4L * 1024 * 1024 * 1024 + 512L * 1024 * 1024 + 300

    @Test
    fun theFixtureIsAValidZip() {
        val file = File.createTempFile("zip64", ".zip")
        try {
            Zip64Stream(1_000_003).use { input -> file.outputStream().use { input.copyTo(it) } }
            ZipFile(file).use { zip ->
                val entry = zip.entries().toList().single()
                assertEquals(1_000_003L, entry.size)
                assertEquals(1_000_003L, zip.getInputStream(entry).readBytes().size.toLong())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun aStreamedReadPastFourGibibytesChecksOut() {
        val result = readRecoveryZip(Zip64Stream(overFourGib))
        assertEquals(overFourGib, result.payloadBytes)
        assertEquals(0L, result.nonZeroBytes)
        assertFalse(result.moreEntries)
    }

    @Test
    fun theEngineFlashesAndVerifiesAnImagePastFourGibibytes() {
        // Writes are counted, not stored: a zero-filled drive reads back what a
        // zero image wrote, so the read-back runs for real without 4.5 GB of RAM.
        val drive = FakeMassStorage(totalSectors = 16L * 1024 * 1024, fill = 0, storeWrites = false)
        val download = Zip64Stream(overFourGib).readBytes()
        val md5 = MessageDigest.getInstance("MD5").digest(download).joinToString("") { "%02x".format(it) }

        val outcome = FlashEngine().flash(
            OpenedSource(download.inputStream(), download.size.toLong(), isZip = true),
            BotDevice(drive),
            ExpectedDigest.md5(md5),
            expectedImageSize = overFourGib,
            listener = object : FlashEngine.Listener {}
        )

        assertEquals(FlashEngine.Outcome.Success(UsbFlasher.Verification.AUTHENTIC), outcome)
        assertEquals((overFourGib + 511) / 512, drive.sectorsWritten)
    }
}
