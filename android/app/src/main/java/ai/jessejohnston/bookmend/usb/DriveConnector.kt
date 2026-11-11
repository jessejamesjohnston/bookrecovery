package ai.jessejohnston.bookmend.usb

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.usb.bot.BotDevice
import ai.jessejohnston.bookmend.usb.bot.UsbBulkTransport
import ai.jessejohnston.bookmend.util.CoverageExempt
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log

/** A claimed drive, or the user-facing reason it couldn't be claimed. */
sealed interface Claim {
    /** [release] gives the interface back and closes the connection. */
    class Ok(val bot: BotDevice, val release: () -> Unit) : Claim

    class Failed(val message: String) : Claim
}

/** Opens a USB device as mass storage. Tests substitute a simulated drive. */
fun interface DriveConnector {
    fun connect(device: UsbDevice): Claim
}

@CoverageExempt("Opens and claims a physical USB device; needs a real USB stack.")
class UsbDriveConnector(
    private val usbManager: UsbManager,
    private val context: Context
) : DriveConnector {

    private companion object {
        const val TAG = "UsbDriveConnector"
    }

    /**
     * Opens [device], finds the mass-storage interface and its bulk endpoints,
     * and claims it. On failure the connection is closed and a localized,
     * user-facing message is returned.
     */
    override fun connect(device: UsbDevice): Claim {
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "Permission denied for USB device.")
            return Claim.Failed(context.getString(R.string.error_usb_permission))
        }

        var massStorageInterface: UsbInterface? = null
        var endpointIn: UsbEndpoint? = null
        var endpointOut: UsbEndpoint? = null

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                massStorageInterface = intf
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        endpointIn = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        endpointOut = ep
                    }
                }
                break
            }
        }

        if (massStorageInterface == null || endpointIn == null || endpointOut == null) {
            Log.e(TAG, "Could not find mass storage interface or endpoints.")
            connection.close()
            return Claim.Failed(context.getString(R.string.error_not_mass_storage))
        }

        if (!connection.claimInterface(massStorageInterface, true)) {
            Log.e(TAG, "Could not claim mass storage interface.")
            connection.close()
            return Claim.Failed(context.getString(R.string.error_claim_interface))
        }

        val claimed = massStorageInterface
    return Claim.Ok(BotDevice(UsbBulkTransport(connection, endpointIn, endpointOut))) {
        connection.releaseInterface(claimed)
        connection.close()
    }
    }
}
