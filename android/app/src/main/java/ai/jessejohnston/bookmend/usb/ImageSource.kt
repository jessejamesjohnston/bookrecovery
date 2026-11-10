package ai.jessejohnston.bookmend.usb

import ai.jessejohnston.bookmend.R
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** An opened image: the raw byte stream and what is known about it before reading. */
class OpenedSource(val stream: InputStream, val contentLength: Long, val isZip: Boolean)

sealed interface SourceResult {
    class Ok(val source: OpenedSource) : SourceResult

    /** [message] is localized and user-facing. */
    class Failed(val message: String) : SourceResult
}

/** Opens the bytes behind an image URL. Tests substitute in-memory images. */
fun interface ImageSourceOpener {
    fun open(url: String, onStep: (String) -> Unit): SourceResult
}

/** Official images over HTTPS, and local files picked through the system file picker. */
class HttpAndContentSourceOpener(private val context: Context) : ImageSourceOpener {

    private companion object {
        const val TAG = "ImageSource"
    }

    override fun open(url: String, onStep: (String) -> Unit): SourceResult {
        var isZip = url.endsWith(".zip", ignoreCase = true)
        val inputStream: InputStream
        val contentLength: Long

        if (url.startsWith("content://")) {
            onStep(context.getString(R.string.step_opening_local))
            val uri = Uri.parse(url)
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var size = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) size = it.getLong(sizeIndex)
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val displayName = it.getString(nameIndex) ?: ""
                        if (displayName.endsWith(".zip", ignoreCase = true)) isZip = true
                    }
                }
            }

            if (size <= 0) {
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        val len = afd.length
                        if (len > 0) size = len
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get AssetFileDescriptor length: ${e.message}")
                }
            }

            if (size <= 0) {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                        val len = fd.statSize
                        if (len > 0) size = len
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get statSize: ${e.message}")
                }
            }

            if (size <= 0) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val len = stream.available().toLong()
                        if (len > 0) size = len
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get available(): ${e.message}")
                }
            }

            if (size <= 0) {
                Log.e(TAG, "Absolutely failed to find file size. Using fallback.")
            }

            contentLength = size
            inputStream = context.contentResolver.openInputStream(uri)
                ?: return SourceResult.Failed(context.getString(R.string.error_open_local))
        } else {
            onStep(context.getString(R.string.step_connecting_server))
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP ${connection.responseCode}")
                return SourceResult.Failed(
                    context.getString(R.string.error_http_server, connection.responseCode)
                )
            }
            // contentLength (Int) overflows on >2GB downloads; several official
            // images exceed that compressed.
            contentLength = connection.contentLengthLong
            inputStream = connection.inputStream
        }
        return SourceResult.Ok(OpenedSource(inputStream, contentLength, isZip))
    }
}
