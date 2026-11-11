package ai.jessejohnston.bookmend.usb.bot

import ai.jessejohnston.bookmend.util.CoverageExempt
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint

/**
 * The two bulk pipes of a USB mass-storage interface. [BotDevice] speaks SCSI
 * over this, so tests can stand a simulated drive behind it and exercise the
 * real command framing.
 *
 * Both calls follow [UsbDeviceConnection.bulkTransfer]: they return the number
 * of bytes moved, or a negative number on failure.
 */
interface BulkTransport {
    fun bulkOut(buffer: ByteArray, length: Int, timeoutMs: Int): Int
    fun bulkIn(buffer: ByteArray, length: Int, timeoutMs: Int): Int
}

/** The real pipes. Deliberately a passthrough: everything above it is testable. */
@CoverageExempt("Calls UsbDeviceConnection.bulkTransfer on a physical drive.")
class UsbBulkTransport(
    private val connection: UsbDeviceConnection,
    private val endpointIn: UsbEndpoint,
    private val endpointOut: UsbEndpoint
) : BulkTransport {
    override fun bulkOut(buffer: ByteArray, length: Int, timeoutMs: Int): Int =
        connection.bulkTransfer(endpointOut, buffer, length, timeoutMs)

    override fun bulkIn(buffer: ByteArray, length: Int, timeoutMs: Int): Int =
        connection.bulkTransfer(endpointIn, buffer, length, timeoutMs)
}
