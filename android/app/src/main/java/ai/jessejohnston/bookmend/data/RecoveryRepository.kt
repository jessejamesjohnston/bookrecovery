package ai.jessejohnston.bookmend.data

import androidx.annotation.VisibleForTesting
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Fetches a catalog's text. Tests serve fixtures instead. */
fun interface CatalogFetcher {
    @Throws(IOException::class)
    fun get(url: String): String
}

/** Plain HTTPS GET with timeouts; non-200 is an error. */
object HttpCatalogFetcher : CatalogFetcher {
    override fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} for $url")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

sealed interface CatalogResult {
    data class Loaded(val images: List<CatalogImage>) : CatalogResult

    /** Nothing could be fetched; the screen offers to try again. */
    data object Unavailable : CatalogResult
}

/**
 * Google's public recovery catalogs, fetched once per process per family and
 * shared by every screen. Only public catalogs are read: ChromeOS (plus
 * ChromeOS Flex) and the Googlebook catalog Google's Device Recovery Utility
 * uses. A failed fetch isn't cached, so the next try gets a fresh chance.
 */
class RecoveryRepository(
    private val fetcher: CatalogFetcher = HttpCatalogFetcher,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        /** Process-wide instance so every screen shares one cached catalog. Tests may swap it. */
        @set:VisibleForTesting
        var instance: RecoveryRepository = RecoveryRepository()

        const val CHROMEOS_URL = "https://dl.google.com/dl/edgedl/chromeos/recovery/recovery2.json"
        const val FLEX_URL = "https://dl.google.com/dl/edgedl/chromeos/recovery/cloudready_recovery2.json"
        const val GOOGLEBOOK_URL = "https://dl.google.com/dl/edgedl/device/recovery/production_recovery.json"
    }

    private val mutex = Mutex()
    private val cache = mutableMapOf<DeviceFamily, List<CatalogImage>>()

    suspend fun load(family: DeviceFamily): CatalogResult = mutex.withLock {
        cache[family]?.let { return@withLock CatalogResult.Loaded(it) }
        val images = withContext(io) {
            when (family) {
                DeviceFamily.CHROMEBOOK ->
                    fetch(CHROMEOS_URL) { CatalogParser.chromeOs(it, isFlex = false) } +
                        fetch(FLEX_URL) { CatalogParser.chromeOs(it, isFlex = true) }
                DeviceFamily.GOOGLEBOOK -> fetch(GOOGLEBOOK_URL) { CatalogParser.googlebook(it) }
            }
        }
        if (images.isEmpty()) return@withLock CatalogResult.Unavailable
        cache[family] = images
        CatalogResult.Loaded(images)
    }

    private inline fun fetch(url: String, parse: (String) -> List<CatalogImage>): List<CatalogImage> =
        try {
            parse(fetcher.get(url))
        } catch (e: IOException) {
            emptyList()
        }
}
