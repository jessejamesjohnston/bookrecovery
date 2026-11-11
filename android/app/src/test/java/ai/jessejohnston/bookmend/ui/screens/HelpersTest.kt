package ai.jessejohnston.bookmend.ui.screens

import ai.jessejohnston.bookmend.data.CatalogParser
import ai.jessejohnston.bookmend.data.CatalogFixtures
import ai.jessejohnston.bookmend.ui.Route
import ai.jessejohnston.bookmend.usb.DriveInspector
import android.hardware.usb.UsbConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HelpersTest {

    @Test
    fun sizesReadLikeTheBoxTheDriveCameIn() {
        assertEquals("32 GB", formatBytes(32_000_000_000))
        assertEquals("1.7 GB", formatBytes(1_700_000_000))
        assertEquals("2 GB", formatBytes(2_000_000_000))
        assertEquals("512 MB", formatBytes(512_000_000))
        assertEquals("4 GB", driveSizeFor(2_147_483_648))
        assertEquals("8 GB", driveSizeFor(4_500_000_000))
        assertEquals("32 GB", driveSizeFor(19_069_493_760))
        assertEquals("4 GB", driveSizeFor(1_000_000))
        assertEquals("301 GB", driveSizeFor(300_500_000_000))
    }

    @Test
    fun durationsAreCompact() {
        assertEquals("40 s", formatDuration(40))
        assertEquals("8 min", formatDuration(480))
        assertEquals("1 h", formatDuration(3600))
        assertEquals("1 h 5 min", formatDuration(3900))
    }

    @Test
    fun theEstimateWaitsForEvidenceThenSettles() {
        val eta = EtaEstimator()
        assertNull(eta.update(0, 0f))
        assertNull("too early to guess", eta.update(2_000, 0.01f))
        // Steady 1% per second: 80 s left at 20%, which rounds up to "about 2 min".
        var left: Long? = null
        for (s in 3..20) left = eta.update(s * 1000L, s / 100f)
        assertEquals(120L, left)
        // Near the end it counts in seconds.
        for (s in 21..95) left = eta.update(s * 1000L, s / 100f)
        assertEquals(10L, left)
        eta.reset()
        assertNull(eta.update(30_000, 0.5f))
    }

    @Test
    fun estimatesRoundToWhatPeoplePlanAround() {
        assertEquals(10L, EtaEstimator.roundForPeople(3))
        assertEquals(60L, EtaEstimator.roundForPeople(55))
        assertEquals(120L, EtaEstimator.roundForPeople(61))
        assertEquals(3900L, EtaEstimator.roundForPeople(3601 + 299))
    }

    @Test
    fun modelsGroupUnderManufacturersAndFilter() {
        val images = CatalogParser.chromeOs(CatalogFixtures.chromeOs, false)
        val all = groupModels(images, "")
        assertEquals(listOf("Acer", "Acme", "Lenovo", "Other"), all.map { it.first })
        assertEquals(1, all.first().second.size)

        assertEquals(listOf("Lenovo"), groupModels(images, "lenovo").map { it.first })
        assertEquals(listOf("Acer"), groupModels(images, "514").map { it.first })
        assertTrue(groupModels(images, "nothing").isEmpty())
    }

    @Test
    fun onlyMassStorageCountsAsADrive() {
        assertTrue(DriveInspector.isStorage(listOf(UsbConstants.USB_CLASS_HID, UsbConstants.USB_CLASS_MASS_STORAGE)))
        assertFalse(DriveInspector.isStorage(listOf(UsbConstants.USB_CLASS_HID)))
        assertFalse(DriveInspector.isStorage(emptyList()))
    }

    @Test
    fun routesKnowTheirStep() {
        assertEquals(Route.Models, Route.of(Route.Models.pattern))
        assertNull(Route.of("nowhere"))
        assertEquals("models", Route.Models.withQuery(" "))
        assertTrue(Route.Models.withQuery("Acer 514").startsWith("models?query="))
        assertEquals(listOf(0, 1, 1, 1, 2, 3, 0, 0, 0), Route.all.map { it.step })
    }
}
