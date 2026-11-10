package ai.jessejohnston.bookmend.usb.fake

import ai.jessejohnston.bookmend.usb.Claim
import ai.jessejohnston.bookmend.usb.DriveConnector
import ai.jessejohnston.bookmend.usb.ImageSourceOpener
import ai.jessejohnston.bookmend.usb.OpenedSource
import ai.jessejohnston.bookmend.usb.SourceResult
import ai.jessejohnston.bookmend.usb.bot.BotDevice
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/** Connects every device to [drive] and counts releases. */
class FakeDriveConnector(private val drive: FakeMassStorage) : DriveConnector {
    var releases = 0
    override fun connect(device: android.hardware.usb.UsbDevice): Claim =
        Claim.Ok(BotDevice(drive)) { releases++ }
}

/** Serves [bytes] for any URL, as a zip download when [isZip]. */
class BytesSource(private val bytes: ByteArray, private val isZip: Boolean) : ImageSourceOpener {
    var stream: TrackingStream? = null
    override fun open(url: String, onStep: (String) -> Unit): SourceResult {
        val s = TrackingStream(bytes)
        stream = s
        return SourceResult.Ok(OpenedSource(s, bytes.size.toLong(), isZip))
    }
}

/** Records whether the flasher closed the stream. */
class TrackingStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    var closed = false
    override fun close() {
        closed = true
        super.close()
    }
}

/** Deterministic pseudo-random bytes, so a misplaced sector can't hide in a pattern. */
fun imageBytes(size: Int, seed: Int = 7): ByteArray = Random(seed).nextBytes(size)

/** A zip holding [entries] (name to bytes), in order. */
fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, data) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

fun sha1Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
