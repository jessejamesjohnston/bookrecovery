package ai.jessejohnston.bookmend.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import ai.jessejohnston.bookmend.R
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles the low-level USB writing of the recovery image.
 *
 * This class abstracts the Android USB Host API, handling the complexities of:
 * 1. Claiming the correct USB Interfaces and Endpoints for block-level transfer.
 * 2. Unzipping the ChromeOS recovery `.bin` payload on the fly from a `.zip` stream.
 * 3. Safely executing bulk transfers of the 16GB+ images to the physical flash drive
 *    in chunks to prevent OutOfMemory errors on constrained mobile devices.
 * 4. Verifying the result: the downloaded `.zip` is hashed in flight and compared
 *    against the manifest checksum (authenticity), and the written sectors are read
 *    back over the same bulk endpoints and compared against the digest of the data
 *    that was written (write verification).
 *
 * Note: Since standard Android devices lack root block access (`/dev/block/sda`), this
 * implementation relies on the USB Mass Storage Class protocol to communicate via SCSI commands.
 */
class UsbFlasher(
    private val context: Context,
    private val drives: DriveConnector,
    private val sources: ImageSourceOpener = HttpAndContentSourceOpener(context)
) {

    constructor(usbManager: UsbManager, context: Context) :
        this(context, UsbDriveConnector(usbManager, context))

    companion object {
        private const val TAG = "UsbFlasher"
        private const val SECTOR_SIZE = 512

        /** Read-back chunk: 128 sectors = 64 KB, the same buffer discipline as the write path. */
        private const val VERIFY_CHUNK_SECTORS = 128

        /** Erase: zero the first 16 MiB (MBR/GPT + early filesystem metadata). */
        private const val HEAD_WIPE_BYTES = 16L * 1024 * 1024

        /** Erase: zero the last 1 MiB (backup GPT), when capacity is known. */
        private const val TAIL_WIPE_BYTES = 1L * 1024 * 1024
    }

    /** Terminal outcome of a flash attempt. */
    sealed interface Result {
        /** The image was written and verification reached the stated level. */
        data class Success(val verification: Verification) : Result

        /** The user cancelled; the drive holds a partial image. */
        data object Cancelled : Result

        /** Writing failed. [message] is localized and user-facing. */
        data class WriteError(val message: String) : Result

        /**
         * The write completed but verification failed — either the download's
         * checksum didn't match the manifest or the read-back didn't match
         * what was written. The drive contents cannot be trusted; the caller
         * should offer a retry.
         */
        data class VerificationError(val message: String) : Result
    }

    /** How far verification got on a successful flash. */
    enum class Verification {
        /** Download matched the manifest checksum AND the read-back matched the write. */
        AUTHENTIC,

        /** No manifest checksum available (local image); the read-back matched the write. */
        WRITE_VERIFIED,

        /** The user chose to skip mid-verification. Skipping is not a failure. */
        SKIPPED
    }

    private val engine = FlashEngine()

    fun cancel() {
        engine.cancel()
    }

    /** Requests that an in-flight verification pass end early (skip ≠ fail). */
    fun skipVerification() {
        engine.skipVerification()
    }

    /** Clears a cancel or skip so the next flash runs: call before a retry. */
    fun reset() {
        engine.reset()
    }

    /**
     * Streams the recovery image at [url] onto [device], then verifies it.
     * The drive is claimed before the download starts, so a drive that can't
     * be used (no permission, too small) never costs a download.
     *
     * @param expectedDownloadDigest The catalog checksum of the download, when
     *   flashing an official image. Null for local files, which get write
     *   verification only.
     * @param onStep Human-readable phase announcements.
     * @param onProgress Write progress, 0..1.
     * @param onVerifyProgress Read-back verification progress, 0..1.
     */
    suspend fun flashImageToUsb(
        device: UsbDevice,
        url: String,
        expectedDownloadDigest: ExpectedDigest? = null,
        expectedImageSize: Long? = null,
        onStep: (String) -> Unit,
        onProgress: (Float) -> Unit,
        onVerifyProgress: (Float) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        var releaseDrive: (() -> Unit)? = null
        var stream: InputStream? = null
        try {
            val claim = drives.connect(device)
            if (claim is Claim.Failed) return@withContext Result.WriteError(claim.message)
            val drive = claim as Claim.Ok
            releaseDrive = drive.release

            val opened = when (val source = sources.open(url, onStep)) {
                is SourceResult.Failed -> return@withContext Result.WriteError(source.message)
                is SourceResult.Ok -> source.source
            }
            stream = opened.stream

            val local = url.startsWith("content://")
            val listener = object : FlashEngine.Listener {
                override fun onStep(step: FlashEngine.Step) = onStep(context.getString(stepText(step, local)))
                override fun onProgress(fraction: Float) = onProgress(fraction)
                override fun onVerifyProgress(fraction: Float) = onVerifyProgress(fraction)
            }
            when (val outcome = engine.flash(opened, drive.bot, expectedDownloadDigest, expectedImageSize, listener)) {
                is FlashEngine.Outcome.Success -> Result.Success(outcome.verification)
                FlashEngine.Outcome.Cancelled -> Result.Cancelled
                is FlashEngine.Outcome.Failed -> failure(outcome.failure)
            }
        } catch (e: DownloadLostException) {
            Log.e(TAG, "Download lost", e)
            Result.WriteError(context.getString(R.string.error_download_lost))
        } catch (e: Exception) {
            Log.e(TAG, "Error flashing to USB", e)
            Result.WriteError(context.getString(R.string.error_unexpected, e.message ?: e.javaClass.simpleName))
        } finally {
            try {
                stream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close input stream", e)
            }
            try {
                releaseDrive?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close USB connection", e)
            }
        }
    }

    private fun stepText(step: FlashEngine.Step, local: Boolean): Int = when (step) {
        FlashEngine.Step.UNPACKING -> if (local) R.string.step_unpacking_local else R.string.step_downloading_unpacking
        FlashEngine.Step.DOWNLOADING -> if (local) R.string.step_reading_local else R.string.step_downloading
        FlashEngine.Step.WRITING -> R.string.step_writing_usb
        FlashEngine.Step.VERIFYING -> R.string.step_verifying
    }

    private fun failure(failure: FlashEngine.Failure): Result = when (failure) {
        FlashEngine.Failure.INVALID_ZIP -> Result.WriteError(context.getString(R.string.error_invalid_zip))
        FlashEngine.Failure.DRIVE_TOO_SMALL -> Result.WriteError(context.getString(R.string.error_drive_too_small))
        FlashEngine.Failure.HARDWARE_WRITE -> Result.WriteError(context.getString(R.string.error_hardware_write))
        FlashEngine.Failure.AUTHENTICITY -> Result.VerificationError(context.getString(R.string.error_authenticity_failed))
        FlashEngine.Failure.VERIFICATION -> Result.VerificationError(context.getString(R.string.error_verification_failed))
    }

    /** Terminal outcome of an erase. */
    sealed interface EraseResult {
        data object Done : EraseResult
        data class Error(val message: String) : EraseResult
    }

    /**
     * Erases the drive by zeroing its partition structures: the first 16 MiB
     * (MBR/primary GPT plus early filesystem metadata) and, when the drive's
     * capacity is readable, the last 1 MiB (backup GPT). Operating systems then
     * see a blank drive and offer to format it.
     *
     * This is a signature wipe, not data destruction — matching what the
     * desktop Chromebook Recovery Utility's erase does, minus its FAT32
     * reformat (a possible follow-up). It takes seconds, so unlike the flash
     * it runs without the KeepAlive foreground service.
     */
    suspend fun eraseDevice(
        device: UsbDevice,
        onProgress: (Float) -> Unit
    ): EraseResult = withContext(Dispatchers.IO) {
        var releaseDrive: (() -> Unit)? = null
        try {
            val claim = drives.connect(device)
            if (claim is Claim.Failed) {
                return@withContext EraseResult.Error(claim.message)
            }
            val ok = claim as Claim.Ok
            releaseDrive = ok.release
            val bot = ok.bot

            val capacity = bot.readCapacity()
            if (capacity == null) {
                Log.w(TAG, "READ CAPACITY failed; wiping the head only.")
            }

            val headSectors = HEAD_WIPE_BYTES / SECTOR_SIZE
            val headToWipe = capacity?.let { minOf(headSectors, it.totalSectors) } ?: headSectors
            val tailSectors =
                if (capacity != null && capacity.totalSectors > headSectors + TAIL_WIPE_BYTES / SECTOR_SIZE) {
                    TAIL_WIPE_BYTES / SECTOR_SIZE
                } else 0L
            val totalToWipe = headToWipe + tailSectors
            var wiped = 0L
            val zeros = ByteArray(VERIFY_CHUNK_SECTORS * SECTOR_SIZE)

            fun wipeRange(startLba: Long, count: Long): Boolean {
                var lba = startLba
                val end = startLba + count
                while (lba < end) {
                    val sectors = minOf(VERIFY_CHUNK_SECTORS.toLong(), end - lba).toInt()
                    val buffer = if (sectors == VERIFY_CHUNK_SECTORS) zeros else ByteArray(sectors * SECTOR_SIZE)
                    if (!bot.writeSectors(lba.toInt(), buffer)) {
                        Log.e(TAG, "Failed to zero sectors at LBA $lba")
                        return false
                    }
                    lba += sectors
                    wiped += sectors
                    onProgress((wiped.toDouble() / totalToWipe).toFloat().coerceIn(0f, 1f))
                }
                return true
            }

            if (!wipeRange(0, headToWipe)) {
                return@withContext EraseResult.Error(context.getString(R.string.error_erase_failed))
            }
            if (tailSectors > 0 && capacity != null) {
                if (!wipeRange(capacity.totalSectors - tailSectors, tailSectors)) {
                    return@withContext EraseResult.Error(context.getString(R.string.error_erase_failed))
                }
            }
            onProgress(1f)
            EraseResult.Done
        } catch (e: Exception) {
            Log.e(TAG, "Error erasing USB device", e)
            EraseResult.Error(context.getString(R.string.error_unexpected, e.message ?: e.javaClass.simpleName))
        } finally {
            try {
                releaseDrive?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close USB connection", e)
            }
        }
    }
}
