package ai.jessejohnston.bookmend.usb

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.usb.fake.imageBytes
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Opening an image: Google's server (with resume) or a file from the system picker. */
@RunWith(RobolectricTestRunner::class)
class ImageSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val body = imageBytes(120_000)
    private lateinit var server: HttpServer
    private var cutFirst = false

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/r.zip") { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            ex.responseHeaders.add("ETag", "\"a\"")
            val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            if (range != null) ex.responseHeaders.add("Content-Range", "bytes $from-${body.size - 1}/${body.size}")
            ex.sendResponseHeaders(if (range != null) 206 else 200, (body.size - from).toLong())
            ex.responseBody.use { out ->
                if (cutFirst && range == null) out.write(body, 0, 40_000) else out.write(body, from, body.size - from)
            }
            ex.close()
        }
        server.createContext("/gone.zip") { ex -> ex.sendResponseHeaders(410, -1); ex.close() }
        server.start()
    }

    @After fun stop() = server.stop(0)

    private fun base() = "http://127.0.0.1:${server.address.port}"

    @Test
    fun anOfficialImageStreamsAndKnowsItsSize() {
        val steps = mutableListOf<String>()
        val result = HttpAndContentSourceOpener(context).open("${base()}/r.zip") { steps += it } as SourceResult.Ok

        assertTrue(result.source.isZip)
        assertEquals(body.size.toLong(), result.source.contentLength)
        assertArrayEquals(body, result.source.stream.use { it.readBytes() })
        assertEquals(listOf(context.getString(R.string.step_connecting_server)), steps)
    }

    @Test
    fun aDroppedDownloadSaysSoAndCarriesOn() {
        cutFirst = true
        val steps = mutableListOf<String>()
        val result = HttpAndContentSourceOpener(context).open("${base()}/r.zip") { steps += it } as SourceResult.Ok
        // The first retry waits a second, as it would on a phone.
        assertArrayEquals(body, result.source.stream.readBytes())
        assertTrue(context.getString(R.string.step_reconnecting) in steps)
        assertEquals(context.getString(R.string.step_downloading_unpacking), steps.last())
    }

    @Test
    fun aServerErrorBecomesAPlainMessage() {
        val result = HttpAndContentSourceOpener(context).open("${base()}/gone.zip") {} as SourceResult.Failed
        assertEquals(context.getString(R.string.error_http_server, 410), result.message)
    }

    @Test
    fun aPickedFileOpensFromTheContentResolver() {
        val uri = Uri.parse("content://files.test/image.zip")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(body))
        val steps = mutableListOf<String>()
        val result = HttpAndContentSourceOpener(context).open(uri.toString()) { steps += it } as SourceResult.Ok

        assertTrue(result.source.isZip)
        assertArrayEquals(body, result.source.stream.readBytes())
        assertEquals(context.getString(R.string.step_opening_local), steps.single())
    }
}
