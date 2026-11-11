package ai.jessejohnston.bookmend.usb.fixture

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Generates, on the fly, a zip laid out exactly like Google's Googlebook
 * recovery downloads: one deflated `.bin` entry whose local header has the
 * data-descriptor flag set, zero sizes and NO ZIP64 extra field, followed by
 * a 24-byte ZIP64 data descriptor and ZIP64 end records. The payload is
 * [payloadBytes] zeros, so a multi-gigabyte image costs a few megabytes of
 * deflate output and no disk.
 *
 * Shared by the JVM tests and the on-device test: only Android's own
 * java.util.zip can say whether Android parses this layout correctly.
 */
class Zip64Stream(
    private val payloadBytes: Long,
    private val entryName: String = "android-desktop_signed_recovery_image.bin"
) : InputStream() {

    private val name = entryName.toByteArray(Charsets.UTF_8)
    private val deflater = Deflater(Deflater.BEST_SPEED, true)
    private val crc = CRC32()
    private val zeros = ByteArray(ZERO_CHUNK)
    private val deflateOut = ByteArray(ZERO_CHUNK)

    private var fed = 0L
    private var compressed = 0L
    private var pending: ByteArray = localHeader()
    private var pendingPos = 0
    private var phase = Phase.BODY

    private enum class Phase { BODY, TRAILER, DONE }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (pendingPos == pending.size) {
            if (!refill()) return -1
        }
        val n = minOf(len, pending.size - pendingPos)
        System.arraycopy(pending, pendingPos, b, off, n)
        pendingPos += n
        return n
    }

    private fun refill(): Boolean {
        when (phase) {
            Phase.BODY -> {
                if (deflater.needsInput() && fed < payloadBytes) {
                    val n = minOf(ZERO_CHUNK.toLong(), payloadBytes - fed).toInt()
                    deflater.setInput(zeros, 0, n)
                    crc.update(zeros, 0, n)
                    fed += n
                    if (fed == payloadBytes) deflater.finish()
                } else if (payloadBytes == 0L && !deflater.finished()) {
                    deflater.finish()
                }
                val n = deflater.deflate(deflateOut)
                if (n > 0) {
                    compressed += n
                    setPending(deflateOut.copyOf(n))
                } else if (deflater.finished()) {
                    deflater.end()
                    phase = Phase.TRAILER
                    setPending(trailer())
                }
                return true
            }
            Phase.TRAILER -> {
                phase = Phase.DONE
                return false
            }
            Phase.DONE -> return false
        }
    }

    private fun setPending(bytes: ByteArray) {
        pending = bytes
        pendingPos = 0
    }

    private fun localHeader(): ByteArray = le(30 + name.size) {
        putInt(0x04034b50)
        putShort(20) // version needed
        putShort(0x0808) // data descriptor + UTF-8 names
        putShort(8) // deflate
        putShort(0); putShort(0) // time, date
        putInt(0); putInt(0); putInt(0) // crc, sizes: in the descriptor
        putShort(name.size.toShort())
        putShort(0) // no extra field, no ZIP64 extra
        put(name)
    }

    private fun trailer(): ByteArray {
        val headerSize = 30L + name.size
        val out = ByteArrayOutputStream()
        out.write(le(24) {
            putInt(0x08074b50)
            putInt(crc.value.toInt())
            putLong(compressed)
            putLong(payloadBytes)
        })
        val centralOffset = headerSize + compressed + 24
        val central = le(46 + name.size + 28) {
            putInt(0x02014b50)
            putShort(45); putShort(45) // made by, needed
            putShort(0x0808)
            putShort(8)
            putShort(0); putShort(0)
            putInt(crc.value.toInt())
            putInt(-1); putInt(-1) // sizes in the ZIP64 extra
            putShort(name.size.toShort())
            putShort(28) // ZIP64 extra
            putShort(0); putShort(0); putShort(0) // comment, disk, internal attrs
            putInt(0) // external attrs
            putInt(-1) // local header offset in the ZIP64 extra
            put(name)
            putShort(0x0001); putShort(24)
            putLong(payloadBytes); putLong(compressed); putLong(0)
        }
        out.write(central)
        val zip64EndOffset = centralOffset + central.size
        out.write(le(56) {
            putInt(0x06064b50)
            putLong(44)
            putShort(45); putShort(45)
            putInt(0); putInt(0)
            putLong(1); putLong(1)
            putLong(central.size.toLong())
            putLong(centralOffset)
        })
        out.write(le(20) {
            putInt(0x07064b50)
            putInt(0)
            putLong(zip64EndOffset)
            putInt(1)
        })
        out.write(le(22) {
            putInt(0x06054b50)
            putShort(0); putShort(0)
            putShort(1); putShort(1)
            putInt(central.size)
            putInt(-1) // central directory offset in the ZIP64 record
            putShort(0)
        })
        return out.toByteArray()
    }

    private inline fun le(size: Int, block: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(block).array()

    private companion object {
        const val ZERO_CHUNK = 1 shl 20
    }
}
