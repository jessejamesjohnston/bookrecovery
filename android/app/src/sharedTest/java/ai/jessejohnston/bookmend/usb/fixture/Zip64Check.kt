package ai.jessejohnston.bookmend.usb.fixture

import java.io.InputStream
import java.util.zip.ZipInputStream

/** What a streaming read of a recovery zip found. */
data class ZipReadResult(val entryName: String, val payloadBytes: Long, val nonZeroBytes: Long, val moreEntries: Boolean)

/**
 * Reads the first `.bin` entry the way the flasher does (java.util.zip,
 * front to back, no seeking) and counts what comes out. ZipInputStream checks
 * the entry's CRC and sizes against the data descriptor when the entry ends,
 * so a misparsed ZIP64 descriptor throws here.
 */
fun readRecoveryZip(stream: InputStream): ZipReadResult {
    val zip = ZipInputStream(stream)
    val entry = requireNotNull(zip.nextEntry) { "no entry" }
    val buffer = ByteArray(1 shl 20)
    var total = 0L
    var nonZero = 0L
    while (true) {
        val n = zip.read(buffer)
        if (n == -1) break
        for (i in 0 until n) if (buffer[i] != 0.toByte()) nonZero++
        total += n
    }
    val more = zip.nextEntry != null
    zip.close()
    return ZipReadResult(entry.name, total, nonZero, more)
}
