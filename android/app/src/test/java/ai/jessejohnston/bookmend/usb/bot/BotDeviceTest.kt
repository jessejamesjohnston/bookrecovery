package ai.jessejohnston.bookmend.usb.bot

import ai.jessejohnston.bookmend.usb.fake.FakeMassStorage
import ai.jessejohnston.bookmend.usb.fake.imageBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SCSI framing: command blocks, big-endian fields, status blocks and short reads. */
class BotDeviceTest {

    @Test
    fun writeThenReadRoundTripsAtAHighLba() {
        val drive = FakeMassStorage(totalSectors = 0x0100_0000) // 8 GiB: LBAs need all four bytes
        val bot = BotDevice(drive)
        val data = imageBytes(4 * 512)
        val lba = 0x00AB_CDEF

        assertTrue(bot.writeSectors(lba, data))
        assertEquals(Triple(FakeMassStorage.OP_WRITE10, lba.toLong(), 4), drive.commands.last())
        assertArrayEquals(data, bot.readSectors(lba, 4))
        assertEquals(Triple(FakeMassStorage.OP_READ10, lba.toLong(), 4), drive.commands.last())
    }

    @Test
    fun writesThatAreNotWholeSectorsAreRefused() {
        val drive = FakeMassStorage(totalSectors = 64)
        assertFalse(BotDevice(drive).writeSectors(0, ByteArray(511)))
        assertTrue(drive.commands.isEmpty())
    }

    @Test
    fun aFailedStatusFailsTheWrite() {
        val drive = FakeMassStorage(totalSectors = 64).apply { failWriteAtLba = 1 }
        assertFalse(BotDevice(drive).writeSectors(0, ByteArray(1024)))
    }

    @Test
    fun aDeadTransportFailsEveryCommand() {
        val bot = BotDevice(FakeMassStorage(totalSectors = 64).apply { transportDead = true })
        assertFalse(bot.writeSectors(0, ByteArray(512)))
        assertNull(bot.readSectors(0, 1))
        assertNull(bot.readCapacity())
    }

    @Test
    fun capacityIsTheLastLbaPlusOne() {
        val capacity = BotDevice(FakeMassStorage(totalSectors = 30_310_400)).readCapacity()
        assertEquals(BotDevice.Capacity(totalSectors = 30_310_400, sectorSize = 512), capacity)
    }

    @Test
    fun aFailedCapacityQueryIsNull() {
        assertNull(BotDevice(FakeMassStorage(totalSectors = 64).apply { capacityFails = true }).readCapacity())
    }

    @Test
    fun readsPastTheEndAreNull() {
        assertNull(BotDevice(FakeMassStorage(totalSectors = 8)).readSectors(6, 4))
    }

    @Test
    fun statusBlocksSplitAcrossTransfersAreReassembled() {
        val drive = FakeMassStorage(totalSectors = 64).apply { maxInChunk = 5 }
        val bot = BotDevice(drive)
        val data = imageBytes(2 * 512)

        assertTrue(bot.writeSectors(3, data))
        assertArrayEquals(data, bot.readSectors(3, 2))
        assertEquals(BotDevice.Capacity(64, 512), bot.readCapacity())
    }
}
