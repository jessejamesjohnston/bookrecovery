package ai.jessejohnston.bookmend.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a drive turned out to be, as the drive screen shows it. */
sealed interface DriveSize {
    data class Known(val bytes: Long) : DriveSize
    /** Claimed, but it wouldn't report a size. Flashing can still try. */
    data object Unknown : DriveSize
    /** Couldn't be opened; [message] says why, in plain words. */
    data class Unusable(val message: String) : DriveSize
}

/**
 * Answers "is this a USB storage drive, and how big is it?" over the same
 * claim and SCSI path the flash uses, so a drive that passes here is one the
 * flash can open. Needs USB permission for [capacity].
 */
class DriveInspector(
    private val drives: DriveConnector,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun capacity(device: UsbDevice): DriveSize = withContext(io) {
        when (val claim = drives.connect(device)) {
            is Claim.Failed -> DriveSize.Unusable(claim.message)
            is Claim.Ok -> try {
                claim.bot.readCapacity()
                    ?.let { DriveSize.Known(it.totalSectors * it.sectorSize) }
                    ?: DriveSize.Unknown
            } finally {
                claim.release()
            }
        }
    }

    companion object {
        /** True when the device has a USB mass-storage interface (a drive, card reader or hub port). */
        fun isStorage(device: UsbDevice): Boolean =
            isStorage((0 until device.interfaceCount).map { device.getInterface(it).interfaceClass })

        fun isStorage(interfaceClasses: List<Int>): Boolean = UsbConstants.USB_CLASS_MASS_STORAGE in interfaceClasses
    }
}
