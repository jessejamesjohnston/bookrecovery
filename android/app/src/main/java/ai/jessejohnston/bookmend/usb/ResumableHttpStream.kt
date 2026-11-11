package ai.jessejohnston.bookmend.usb

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The download dropped and couldn't be picked up again. */
class DownloadLostException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The first request didn't succeed; [code] is the HTTP status. */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * A download that survives dropped connections. When a read fails, or the
 * body ends early, it waits a moment and asks the server for the rest with
 * `Range: bytes=<position>-`, guarded by `If-Range` with the first response's
 * ETag (or Last-Modified) so a file that changed in between is refused
 * rather than spliced in. Readers above it (the unzip, the checksum) see one
 * unbroken stream.
 *
 * Consecutive failures are capped; any successful read resets the count, so
 * a long download on flaky Wi-Fi can recover many times.
 */
class ResumableHttpStream(
    private val url: String,
    private val connect: (url: String, headers: Map<String, String>) -> HttpURLConnection = ::defaultConnect,
    private val maxRetries: Int = 6,
    private val backoffMs: (attempt: Int) -> Long = { attempt -> minOf(30_000L, 1_000L shl (attempt - 1)) },
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val onReconnecting: (attempt: Int) -> Unit = {},
    private val onResumed: () -> Unit = {},
) : InputStream() {

    private var connection: HttpURLConnection? = null
    private var body: InputStream? = null
    private var position = 0L
    private var validator: String? = null
    private var failures = 0
    private var closed = false

    /** The full length from the first response, or -1 when the server didn't say. */
    var contentLength = -1L
        private set

    /** Makes the first request. Throws [HttpStatusException] unless the server answers 200. */
    fun open(): ResumableHttpStream {
        val c = connect(url, emptyMap())
        val code = c.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            c.disconnect()
            throw HttpStatusException(code)
        }
        contentLength = c.contentLengthLong
        validator = c.getHeaderField("ETag") ?: c.getHeaderField("Last-Modified")
        connection = c
        body = c.inputStream
        return this
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (true) {
            check(!closed) { "closed" }
            val stream = body ?: throw IOException("not opened")
            val n = try {
                stream.read(b, off, len)
            } catch (e: IOException) {
                resume(e)
                continue
            }
            if (n == -1) {
                if (contentLength >= 0 && position < contentLength) {
                    resume(IOException("ended at $position of $contentLength bytes"))
                    continue
                }
                return -1
            }
            position += n
            if (failures > 0) {
                failures = 0
                onResumed()
            }
            return n
        }
    }

    private fun resume(cause: IOException) {
        disconnect()
        while (true) {
            failures++
            if (failures > maxRetries) throw DownloadLostException("gave up at byte $position", cause)
            if (validator == null) throw DownloadLostException("the server can't resume this download", cause)
            onReconnecting(failures)
            sleep(backoffMs(failures))
            val c = try {
                connect(url, mapOf("Range" to "bytes=$position-", "If-Range" to validator!!))
            } catch (e: IOException) {
                continue
            }
            val code = try { c.responseCode } catch (e: IOException) { c.disconnect(); continue }
            val range = c.getHeaderField("Content-Range")
            if (code == HttpURLConnection.HTTP_PARTIAL && range != null && range.startsWith("bytes $position-")) {
                connection = c
                body = c.inputStream
                return
            }
            c.disconnect()
            // 200 means the server ignored the range or the file changed: either
            // way the rest can't be joined to what was already written.
            if (code == HttpURLConnection.HTTP_OK) throw DownloadLostException("the file changed on the server", cause)
        }
    }

    private fun disconnect() {
        try { body?.close() } catch (_: IOException) {}
        connection?.disconnect()
        body = null
        connection = null
    }

    override fun close() {
        closed = true
        disconnect()
    }

    companion object {
        fun defaultConnect(url: String, headers: Map<String, String>): HttpURLConnection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
    }
}
