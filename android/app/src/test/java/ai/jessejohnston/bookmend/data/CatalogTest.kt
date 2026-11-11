package ai.jessejohnston.bookmend.data

import ai.jessejohnston.bookmend.usb.ExpectedDigest
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {

    @Test
    fun chromeOsEntriesBecomeImagesWithSha1() {
        val images = CatalogParser.chromeOs(CatalogFixtures.chromeOs, isFlex = false)
        val stable = images.first { it.url.endsWith("a_stable.zip") }

        assertEquals(DeviceFamily.CHROMEBOOK, stable.family)
        assertEquals("Acer", stable.manufacturer)
        assertEquals(ExpectedDigest.sha1("aa11"), stable.digest)
        assertEquals(4500000000L, stable.imageBytes)
        assertEquals(1700000000L, stable.downloadBytes)
        assertEquals("16700.68.0", stable.version)
    }

    @Test
    fun entriesWithoutAUrlOrNameAreDropped() {
        val names = CatalogParser.chromeOs(CatalogFixtures.chromeOs, isFlex = false).map { it.name }
        assertTrue("No URL" !in names)
        assertEquals(6, names.size)
    }

    @Test
    fun blanksAreNormalized() {
        val spaced = CatalogParser.chromeOs(CatalogFixtures.chromeOs, isFlex = false).first { it.url.endsWith("s.zip") }
        assertEquals("Spaced", spaced.name)
        assertEquals("Other", spaced.manufacturer)
        assertEquals("STABLE", spaced.channel)
        assertNull("a blank checksum is no checksum", spaced.digest)
    }

    @Test
    fun googlebookEntriesUseMd5AndIgnoreThePlaceholderSize() {
        val images = CatalogParser.googlebook(CatalogFixtures.googlebook)
        val acer = images.first { it.manufacturer == "Acer" }
        val hp = images.first { it.manufacturer == "HP" }

        assertEquals(2, images.size)
        assertEquals(DeviceFamily.GOOGLEBOOK, acer.family)
        assertEquals(ExpectedDigest.md5("b478bb5f4c3ca8512e747897e40f69e3"), acer.digest)
        assertEquals(19069493760L, acer.imageBytes)
        assertNull("zipfilesize 1 is a placeholder", acer.downloadBytes)
        assertEquals("16708002", acer.version)
        assertEquals("HP Googlebook 14", hp.name)
        assertEquals(7700000000L, hp.downloadBytes)
    }

    @Test
    fun malformedJsonIsAnEmptyCatalogNotACrash() {
        assertTrue(CatalogParser.chromeOs("{not json", isFlex = false).isEmpty())
        assertTrue(CatalogParser.googlebook("""{"an":"object"}""").isEmpty())
        assertTrue(CatalogParser.googlebook("null").isEmpty())
    }

    @Test
    fun channelsSortSafestFirst() {
        val acer = CatalogParser.chromeOs(CatalogFixtures.chromeOs, false).filter { it.manufacturer == "Acer" }
        assertEquals(listOf("STABLE", "LTC", "BETA"), acer.reversed().sortedByChannel().map { it.channel })
        val odd = acer.first().copy(channel = "NIGHTLY")
        assertEquals("NIGHTLY", (acer + odd).sortedByChannel().last().channel)
    }

    @Test
    fun hardwareIdsFindTheirModel() {
        val images = CatalogParser.chromeOs(CatalogFixtures.chromeOs, false)
        assertEquals(listOf("Acer Chromebook 514 (CB514-2H, CB514-2HT)"), HardwareId.match("  spherion   c4b-k3e ", images))
        assertEquals(listOf("Lenovo Chromebook Plus (14\", 10)"), HardwareId.match("NAVI-SNEN A1B", images))
        assertTrue("a broken catalog pattern is skipped", HardwareId.match("UNCLOSED", images).isEmpty())
        assertTrue(HardwareId.match("   ", images).isEmpty())
        assertTrue(HardwareId.match("NOTHING-LIKE-IT", images).isEmpty())
    }

    private class FakeFetcher(private val bodies: Map<String, String>) : CatalogFetcher {
        val calls = mutableListOf<String>()
        override fun get(url: String): String {
            calls += url
            return bodies[url] ?: throw IOException("offline")
        }
    }

    @Test
    fun aChromebookCatalogIncludesFlexAndIsCached() = runTest {
        val fetcher = FakeFetcher(
            mapOf(RecoveryRepository.CHROMEOS_URL to CatalogFixtures.chromeOs, RecoveryRepository.FLEX_URL to CatalogFixtures.flex)
        )
        val repo = RecoveryRepository(fetcher, StandardTestDispatcher(testScheduler))
        val first = repo.load(DeviceFamily.CHROMEBOOK) as CatalogResult.Loaded
        val second = repo.load(DeviceFamily.CHROMEBOOK) as CatalogResult.Loaded

        assertEquals(7, first.images.size)
        assertTrue(first.images.single { it.isFlex }.name == "ChromeOS Flex")
        assertSame(first.images, second.images)
        assertEquals(2, fetcher.calls.size)
    }

    @Test
    fun flexBeingDownStillLoadsChromeOs() = runTest {
        val repo = RecoveryRepository(FakeFetcher(mapOf(RecoveryRepository.CHROMEOS_URL to CatalogFixtures.chromeOs)), StandardTestDispatcher(testScheduler))
        assertEquals(6, (repo.load(DeviceFamily.CHROMEBOOK) as CatalogResult.Loaded).images.size)
    }

    @Test
    fun anOfflineLoadIsNotCached() = runTest {
        val bodies = mutableMapOf<String, String>()
        val fetcher = object : CatalogFetcher {
            override fun get(url: String) = bodies[url] ?: throw IOException("offline")
        }
        val repo = RecoveryRepository(fetcher, StandardTestDispatcher(testScheduler))

        assertEquals(CatalogResult.Unavailable, repo.load(DeviceFamily.GOOGLEBOOK))
        bodies[RecoveryRepository.GOOGLEBOOK_URL] = CatalogFixtures.googlebook
        assertEquals(2, (repo.load(DeviceFamily.GOOGLEBOOK) as CatalogResult.Loaded).images.size)
    }

    @Test
    fun theHttpFetcherReadsBodiesAndRejectsErrors() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { ex ->
            val body = "[]".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.createContext("/missing") { ex -> ex.sendResponseHeaders(404, -1); ex.close() }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals("[]", HttpCatalogFetcher.get("$base/ok"))
            val failed = runCatching { HttpCatalogFetcher.get("$base/missing") }.exceptionOrNull()
            assertTrue(failed is IOException)
        } finally {
            server.stop(0)
        }
    }
}
