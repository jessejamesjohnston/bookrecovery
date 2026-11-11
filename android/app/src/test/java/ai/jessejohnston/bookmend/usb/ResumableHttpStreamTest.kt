package ai.jessejohnston.bookmend.usb

import ai.jessejohnston.bookmend.usb.fake.imageBytes
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * A real HTTP server on localhost that honors Range and If-Range like a CDN,
 * and can be told to cut connections partway through a body.
 */
class ResumableHttpStreamTest {

    private val file = imageBytes(300_000, seed = 3)
    private lateinit var server: HttpServer
    private val requests = mutableListOf<Map<String, String?>>()

    /** Bytes to send before cutting each successive response; null sends it all. */
    private val cutAfter = ArrayDeque<Int?>()
    private var etag: String? = "\"v1\""
    private var rangeStatus = 206

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/image.zip", ::serve)
        server.createContext("/missing") { ex -> ex.sendResponseHeaders(404, -1); ex.close() }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private val url get() = "http://127.0.0.1:${server.address.port}/image.zip"

    private fun serve(ex: HttpExchange) {
        val range = ex.requestHeaders.getFirst("Range")
        val ifRange = ex.requestHeaders.getFirst("If-Range")
        requests += mapOf("Range" to range, "If-Range" to ifRange)
        etag?.let { ex.responseHeaders.add("ETag", it) }
        val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
        val honorRange = range != null && (ifRange == null || ifRange == etag) && rangeStatus == 206
        if (range != null && rangeStatus != 206 && rangeStatus != 200) {
            ex.sendResponseHeaders(rangeStatus, -1); ex.close(); return
        }
        val from = if (honorRange) start else 0
        val body = file.copyOfRange(from, file.size)
        if (honorRange) ex.responseHeaders.add("Content-Range", "bytes $from-${file.size - 1}/${file.size}")
        ex.sendResponseHeaders(if (honorRange) 206 else 200, body.size.toLong())
        val cut = cutAfter.removeFirstOrNull()
        ex.responseBody.use { out ->
            if (cut == null) out.write(body) else { out.write(body, 0, cut); out.flush() }
        }
        ex.close()
    }

    private fun stream(reconnects: MutableList<Int> = mutableListOf(), retries: Int = 4) =
        ResumableHttpStream(url, maxRetries = retries, backoffMs = { 0 }, sleep = {}, onReconnecting = { reconnects += it })

    @Test
    fun anUnbrokenDownloadIsJustTheFile() {
        val s = stream().open()
        assertEquals(file.size.toLong(), s.contentLength)
        assertArrayEquals(file, s.readBytes())
        assertEquals(1, requests.size)
    }

    @Test
    fun droppedConnectionsResumeFromTheExactByte() {
        cutAfter.addAll(listOf(100_000, 50_000, null))
        val reconnects = mutableListOf<Int>()
        val bytes = stream(reconnects).open().use { it.readBytes() }

        assertArrayEquals(file, bytes)
        assertEquals(3, requests.size)
        assertTrue(requests[1]["Range"]!!.startsWith("bytes="))
        assertEquals("\"v1\"", requests[1]["If-Range"])
        // Each resume starts where the bytes actually stopped.
        val starts = requests.drop(1).map { it["Range"]!!.removePrefix("bytes=").substringBefore('-').toInt() }
        assertTrue(starts[0] in 1..100_000 && starts[1] > starts[0])
        assertEquals(listOf(1, 1), reconnects)
    }

    @Test
    fun aFileThatChangedIsRefusedNotSpliced() {
        cutAfter.add(100_000)
        val s = stream().open()
        etag = "\"v2\""
        try {
            s.readBytes()
            fail("expected the download to be refused")
        } catch (e: DownloadLostException) {
            assertTrue(e.message!!.contains("changed"))
        }
    }

    @Test
    fun aServerWithoutValidatorsCannotResume() {
        etag = null
        cutAfter.add(100_000)
        try {
            stream().open().readBytes()
            fail("expected the download to be lost")
        } catch (e: DownloadLostException) {
            assertTrue(e.message!!.contains("can't resume"))
        }
    }

    @Test
    fun retriesAreCappedWhenTheServerKeepsFailing() {
        cutAfter.add(100_000)
        rangeStatus = 503
        val reconnects = mutableListOf<Int>()
        try {
            stream(reconnects, retries = 3).open().readBytes()
            fail("expected the download to be lost")
        } catch (e: DownloadLostException) {
            assertEquals(listOf(1, 2, 3), reconnects)
        }
    }

    @Test
    fun aFailedFirstRequestReportsItsStatus() {
        try {
            ResumableHttpStream("http://127.0.0.1:${server.address.port}/missing").open()
            fail("expected an HTTP error")
        } catch (e: HttpStatusException) {
            assertEquals(404, e.code)
        }
    }
}
