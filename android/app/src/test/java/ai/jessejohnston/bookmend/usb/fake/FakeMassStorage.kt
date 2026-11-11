package ai.jessejohnston.bookmend.usb.fake

import ai.jessejohnston.bookmend.usb.bot.BulkTransport
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A USB flash drive simulated at the Bulk-Only Transport level: it parses the
 * 31-byte command blocks [ai.jessejohnston.bookmend.usb.bot.BotDevice] sends,
 * keeps sectors in a sparse map, and answers with 13-byte status blocks. Tests
 * therefore exercise the real SCSI framing, not a stand-in for it.
 *
 * Sectors never written read back as [fill], so a test can tell written,
 * zeroed and untouched regions apart.
 */
class FakeMassStorage(
    /** Drive size in 512-byte sectors. Writes past it fail, as on real media. */
    val totalSectors: Long,
    private val fill: Byte = 0xAA.toByte(),
    /** False keeps nothing: writes are counted and every read returns [fill]. */
    private val storeWrites: Boolean = true
) : BulkTransport {

    /** Sectors accepted by successful writes. */
    var sectorsWritten = 0L
        private set

    /** Fail (status 1) any WRITE(10) that touches this LBA. */
    var failWriteAtLba: Long? = null

    /** Flip one byte of this LBA on every READ(10), simulating bad flash. */
    var corruptReadAtLba: Long? = null

    /** READ CAPACITY(10) answers with a failed status. */
    var capacityFails = false

    /** Largest IN transfer the drive returns at once (short reads are legal). */
    var maxInChunk = Int.MAX_VALUE

    /** Every bulk OUT call fails at the transport level (cable pulled). */
    var transportDead = false

    /** Each received command, as (opcode, lba, sector count). */
    val commands = mutableListOf<Triple<Int, Long, Int>>()

    private val sectors = HashMap<Long, ByteArray>()
    /** Bytes the drive will return on bulk IN, as whole arrays so large reads stay cheap. */
    private val pendingIn = ArrayDeque<ByteArray>()
    private var pendingInPos = 0
    private var pendingWrite: PendingWrite? = null

    private class PendingWrite(val tag: Int, val lba: Long, val bytes: ByteArray, var received: Int = 0)

    /** The drive's contents from [startLba], [length] bytes long. */
    fun read(startLba: Long, length: Int): ByteArray {
        val out = ByteArray(length)
        var pos = 0
        var lba = startLba
        while (pos < length) {
            val sector = sectorAt(lba)
            val n = minOf(SECTOR, length - pos)
            System.arraycopy(sector, 0, out, pos, n)
            pos += n
            lba++
        }
        return out
    }

    /** True once any WRITE(10) has stored data at [lba]. */
    fun isWritten(lba: Long): Boolean = sectors.containsKey(lba)

    override fun bulkOut(buffer: ByteArray, length: Int, timeoutMs: Int): Int {
        if (transportDead) return -1
        val write = pendingWrite
        if (write != null) {
            System.arraycopy(buffer, 0, write.bytes, write.received, length)
            write.received += length
            if (write.received == write.bytes.size) finishWrite(write)
            return length
        }
        require(length == 31) { "expected a 31-byte CBW, got $length bytes" }
        val cbw = ByteBuffer.wrap(buffer, 0, 31).order(ByteOrder.LITTLE_ENDIAN)
        require(cbw.int == CBW_SIGNATURE) { "bad CBW signature" }
        val tag = cbw.int
        val transferLength = cbw.int
        cbw.get() // flags
        cbw.get() // LUN
        cbw.get() // command length
        val opcode = buffer[15].toInt() and 0xff
        val lba = beInt(buffer, 17).toLong() and 0xffffffffL
        val count = ((buffer[22].toInt() and 0xff) shl 8) or (buffer[23].toInt() and 0xff)
        commands += Triple(opcode, lba, count)
        when (opcode) {
            OP_WRITE10 -> {
                require(transferLength == count * SECTOR) { "WRITE(10) length mismatch" }
                pendingWrite = PendingWrite(tag, lba, ByteArray(transferLength))
            }
            OP_READ10 -> {
                require(transferLength == count * SECTOR) { "READ(10) length mismatch" }
                if (lba + count > totalSectors) {
                    queueStatus(tag, transferLength, failed = true)
                } else {
                    val data = ByteArray(count * SECTOR)
                    for (i in 0 until count) {
                        System.arraycopy(sectorAt(lba + i), 0, data, i * SECTOR, SECTOR)
                        if (corruptReadAtLba == lba + i) {
                            data[i * SECTOR] = (data[i * SECTOR].toInt() xor 0xff).toByte()
                        }
                    }
                    pendingIn.addLast(data)
                    queueStatus(tag, 0, failed = false)
                }
            }
            OP_READ_CAPACITY10 -> {
                if (capacityFails) {
                    queueStatus(tag, 8, failed = true)
                } else {
                    val answer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                    answer.putInt((totalSectors - 1).toInt())
                    answer.putInt(SECTOR)
                    pendingIn.addLast(answer.array())
                    queueStatus(tag, 0, failed = false)
                }
            }
            else -> error("unsupported opcode 0x${opcode.toString(16)}")
        }
        return length
    }

    override fun bulkIn(buffer: ByteArray, length: Int, timeoutMs: Int): Int {
        val head = pendingIn.firstOrNull() ?: return -1
        val n = minOf(length, maxInChunk, head.size - pendingInPos, buffer.size)
        System.arraycopy(head, pendingInPos, buffer, 0, n)
        pendingInPos += n
        if (pendingInPos == head.size) {
            pendingIn.removeFirst()
            pendingInPos = 0
        }
        return n
    }

    private fun finishWrite(write: PendingWrite) {
        pendingWrite = null
        val count = write.bytes.size / SECTOR
        val outOfRange = write.lba + count > totalSectors
        val injected = failWriteAtLba?.let { it >= write.lba && it < write.lba + count } == true
        if (!outOfRange && !injected) {
            sectorsWritten += count
            if (storeWrites) for (i in 0 until count) {
                sectors[write.lba + i] = write.bytes.copyOfRange(i * SECTOR, (i + 1) * SECTOR)
            }
        }
        queueStatus(write.tag, 0, failed = outOfRange || injected)
    }

    private fun queueStatus(tag: Int, residue: Int, failed: Boolean) {
        val csw = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        csw.putInt(CSW_SIGNATURE)
        csw.putInt(tag)
        csw.putInt(residue)
        csw.put(if (failed) 1 else 0)
        pendingIn.addLast(csw.array())
    }

    private val blank = ByteArray(SECTOR) { fill }

    private fun sectorAt(lba: Long): ByteArray = sectors[lba] ?: blank

    private fun beInt(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xff) shl 24) or ((b[at + 1].toInt() and 0xff) shl 16) or
            ((b[at + 2].toInt() and 0xff) shl 8) or (b[at + 3].toInt() and 0xff)

    companion object {
        const val SECTOR = 512
        const val OP_WRITE10 = 0x2A
        const val OP_READ10 = 0x28
        const val OP_READ_CAPACITY10 = 0x25
        private const val CBW_SIGNATURE = 0x43425355
        private const val CSW_SIGNATURE = 0x53425355
    }
}
