package ai.jessejohnston.bookmend.usb

import ai.jessejohnston.bookmend.usb.bot.BotDevice
import ai.jessejohnston.bookmend.usb.verify.BoundedDigest
import ai.jessejohnston.bookmend.usb.verify.IncrementalDigest
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * The checksum a catalog publishes for the download as served (the `.zip`).
 * ChromeOS catalogs publish SHA-1; the Googlebook catalog publishes MD5 of the
 * same thing.
 */
data class ExpectedDigest(val algorithm: String, val hex: String) {
    companion object {
        fun sha1(hex: String) = ExpectedDigest("SHA-1", hex)
        fun md5(hex: String) = ExpectedDigest("MD5", hex)
    }
}

/**
 * Everything between an opened image stream and a claimed drive: size check,
 * unzip, write, authenticity check and read-back. No Android types, so the
 * whole path runs in plain JVM tests against a simulated drive.
 *
 * Images are written in 512-byte sectors through [BotDevice]. The drive's
 * capacity is read first: when the image size is known up front (a catalog
 * size, a raw file's length, a zip entry header), a drive that is too small
 * fails before any download is read. When it isn't known, a write past the
 * end of the drive is still reported as "too small", not as a hardware fault.
 */
class FlashEngine(private val clock: () -> Long = System::currentTimeMillis) {

    enum class Step { UNPACKING, DOWNLOADING, WRITING, VERIFYING }

    enum class Failure { INVALID_ZIP, DRIVE_TOO_SMALL, HARDWARE_WRITE, AUTHENTICITY, VERIFICATION }

    sealed interface Outcome {
        data class Success(val verification: UsbFlasher.Verification) : Outcome
        data object Cancelled : Outcome
        data class Failed(val failure: Failure) : Outcome
    }

    interface Listener {
        fun onStep(step: Step) {}
        fun onProgress(fraction: Float) {}
        fun onVerifyProgress(fraction: Float) {}
    }

    @Volatile
    private var isCancelled = false

    @Volatile
    private var isVerificationSkipped = false

    fun cancel() {
        isCancelled = true
    }

    /** Ends an in-flight verification early. Skipping is not a failure. */
    fun skipVerification() {
        isVerificationSkipped = true
    }

    /**
     * Clears a cancel or skip before another attempt. [flash] deliberately
     * doesn't do this itself: a cancel that arrives just before the write
     * loop starts must still stop it.
     */
    fun reset() {
        isCancelled = false
        isVerificationSkipped = false
    }

    /**
     * Writes [source] to [bot] and verifies it. The caller owns [source]'s
     * stream and the drive: this closes neither.
     *
     * @param expected The catalog checksum of the download; null for local
     *   files, which get write verification only.
     * @param expectedImageSize The catalog's uncompressed size, when known.
     */
    fun flash(
        source: OpenedSource,
        bot: BotDevice,
        expected: ExpectedDigest?,
        expectedImageSize: Long?,
        listener: Listener
    ): Outcome {
        if (isCancelled) return Outcome.Cancelled

        val driveBytes = bot.readCapacity()?.let { it.totalSectors * it.sectorSize }
        val catalogSize = expectedImageSize?.takeIf { it > 0 }
        val upFrontSize = catalogSize ?: source.contentLength.takeIf { !source.isZip && it > 0 }
        if (tooBig(upFrontSize, driveBytes)) return Outcome.Failed(Failure.DRIVE_TOO_SMALL)

        // With a catalog checksum, hash the raw (compressed) stream as it flows
        // past, so the download's digest is ready when the write finishes.
        val sourceDigest = expected?.let { IncrementalDigest(it.algorithm) }
        val sourceStream: InputStream =
            if (sourceDigest != null) DigestingInputStream(source.stream, sourceDigest) else source.stream

        // Progress denominator, best first: the catalog size, the zip entry
        // header, a 3x-compression guess from the download size, then 4 GB.
        var estimatedSize: Long = catalogSize ?: when {
            source.contentLength > 0 && source.isZip -> source.contentLength * 3
            source.contentLength > 0 -> source.contentLength
            else -> FALLBACK_ESTIMATE
        }

        val data: InputStream
        if (source.isZip) {
            listener.onStep(Step.UNPACKING)
            val zip = ZipInputStream(sourceStream)
            var entry = zip.nextEntry
            while (entry != null && (entry.isDirectory || !entry.name.endsWith(".bin", ignoreCase = true))) {
                entry = zip.nextEntry
            }
            if (entry == null) return Outcome.Failed(Failure.INVALID_ZIP)
            if (catalogSize == null && entry.size > 0) {
                if (tooBig(entry.size, driveBytes)) return Outcome.Failed(Failure.DRIVE_TOO_SMALL)
                estimatedSize = entry.size
            }
            data = zip
        } else {
            listener.onStep(Step.DOWNLOADING)
            data = sourceStream
        }

        // Stream the image to the drive, digesting the bytes as they pass: that
        // digest is what the read-back is checked against.
        listener.onStep(Step.WRITING)
        val writtenDigest = IncrementalDigest()
        var totalRead = 0L
        var lba = 0L
        var lastUpdate = clock()
        val readBuffer = ByteArray(READ_BUFFER)
        val chunk = ByteArray(CHUNK_BUFFER)
        var chunkPos = 0

        fun write(bytes: ByteArray): Failure? {
            if (bot.writeSectors(lba.toInt(), bytes)) {
                lba += bytes.size / SECTOR
                return null
            }
            val pastEnd = driveBytes != null && (lba * SECTOR + bytes.size) > driveBytes
            return if (pastEnd) Failure.DRIVE_TOO_SMALL else Failure.HARDWARE_WRITE
        }

        var bytesRead = data.read(readBuffer)
        while (bytesRead != -1) {
            if (isCancelled) return Outcome.Cancelled
            writtenDigest.update(readBuffer, 0, bytesRead)
            System.arraycopy(readBuffer, 0, chunk, chunkPos, bytesRead)
            chunkPos += bytesRead
            totalRead += bytesRead

            if (chunkPos >= WRITE_THRESHOLD) {
                val whole = (chunkPos / SECTOR) * SECTOR
                write(chunk.copyOf(whole))?.let { return Outcome.Failed(it) }
                val remainder = chunkPos - whole
                if (remainder > 0) System.arraycopy(chunk, whole, chunk, 0, remainder)
                chunkPos = remainder
            }

            val now = clock()
            if (now - lastUpdate > PROGRESS_INTERVAL_MS) {
                lastUpdate = now
                listener.onProgress((totalRead.toDouble() / estimatedSize).toFloat().coerceIn(0f, 1f))
            }
            bytesRead = data.read(readBuffer)
        }

        // The last partial sector, zero-padded. The padding never enters a digest.
        if (chunkPos > 0) {
            val padded = ByteArray(((chunkPos + SECTOR - 1) / SECTOR) * SECTOR)
            System.arraycopy(chunk, 0, padded, 0, chunkPos)
            write(padded)?.let { return Outcome.Failed(it) }
        }

        // Authenticity: a zip's central directory sits past the .bin entry, so
        // drain the rest of the download before comparing checksums.
        if (sourceDigest != null && expected != null) {
            val drain = ByteArray(READ_BUFFER)
            while (sourceStream.read(drain) != -1) {
                if (isCancelled) return Outcome.Cancelled
            }
            if (!IncrementalDigest.matches(expected.hex, sourceDigest.hexDigest())) {
                return Outcome.Failed(Failure.AUTHENTICITY)
            }
        }

        // Write verification: read back whole sectors, digest exactly the bytes
        // that were written, and compare.
        listener.onStep(Step.VERIFYING)
        listener.onVerifyProgress(0f)
        val expectedWriteHex = writtenDigest.hexDigest()
        val readBack = BoundedDigest(totalRead)
        val totalSectors = (totalRead + SECTOR - 1) / SECTOR
        var verifyLba = 0L
        var lastVerifyUpdate = clock()
        while (verifyLba < totalSectors) {
            if (isCancelled) return Outcome.Cancelled
            if (isVerificationSkipped) return Outcome.Success(UsbFlasher.Verification.SKIPPED)
            val sectors = minOf(VERIFY_CHUNK_SECTORS.toLong(), totalSectors - verifyLba).toInt()
            val bytes = bot.readSectors(verifyLba.toInt(), sectors)
                ?: return Outcome.Failed(Failure.VERIFICATION)
            readBack.offer(bytes, bytes.size)
            verifyLba += sectors
            val now = clock()
            if (now - lastVerifyUpdate > PROGRESS_INTERVAL_MS) {
                lastVerifyUpdate = now
                listener.onVerifyProgress((readBack.bytesDigested.toDouble() / totalRead).toFloat().coerceIn(0f, 1f))
            }
        }
        listener.onVerifyProgress(1f)
        if (!IncrementalDigest.matches(expectedWriteHex, readBack.hexDigest())) {
            return Outcome.Failed(Failure.VERIFICATION)
        }
        return Outcome.Success(
            if (expected != null) UsbFlasher.Verification.AUTHENTIC else UsbFlasher.Verification.WRITE_VERIFIED
        )
    }

    private fun tooBig(imageBytes: Long?, driveBytes: Long?) =
        imageBytes != null && driveBytes != null && imageBytes > driveBytes

    /** Passes every byte read through [digest], while ZipInputStream consumes the stream. */
    private class DigestingInputStream(
        source: InputStream,
        private val digest: IncrementalDigest
    ) : FilterInputStream(source) {

        private val single = ByteArray(1)

        override fun read(): Int {
            val value = super.read()
            if (value != -1) {
                single[0] = value.toByte()
                digest.update(single, 0, 1)
            }
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = super.read(b, off, len)
            if (count > 0) digest.update(b, off, count)
            return count
        }
    }

    private companion object {
        const val SECTOR = 512
        const val READ_BUFFER = 64 * 1024
        const val CHUNK_BUFFER = 128 * 1024

        /** Write once at least this much is buffered (whole sectors only). */
        const val WRITE_THRESHOLD = 64 * 1024

        /** Read-back chunk: 128 sectors = 64 KB, the same discipline as the write path. */
        const val VERIFY_CHUNK_SECTORS = 128
        const val PROGRESS_INTERVAL_MS = 500L
        const val FALLBACK_ESTIMATE = 4L * 1024 * 1024 * 1024
    }
}
