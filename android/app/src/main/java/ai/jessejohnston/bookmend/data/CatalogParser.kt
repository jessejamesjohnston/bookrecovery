package ai.jessejohnston.bookmend.data

import ai.jessejohnston.bookmend.usb.ExpectedDigest
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

/** Google's Googlebook catalog entry (`device/recovery/production_recovery.json`). */
internal data class GooglebookEntry(
    @SerializedName("manufacturer") val manufacturer: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("model") val model: String?,
    @SerializedName("device") val device: String?,
    @SerializedName("channel") val channel: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("filesize") val filesize: Long?,
    @SerializedName("zipfilesize") val zipfilesize: Long?,
    @SerializedName("md5") val md5: String?,
    @SerializedName("buildidentifier") val buildIdentifier: String?,
)

/**
 * Turns the catalogs' JSON into [CatalogImage]s, dropping entries that can't
 * be flashed (no URL, no name). Pure: no network, no Android.
 */
object CatalogParser {

    private val gson = Gson()

    fun chromeOs(json: String, isFlex: Boolean): List<CatalogImage> {
        val type = object : TypeToken<List<RecoveryImage>>() {}.type
        val entries: List<RecoveryImage> = parse(json, type)
        return entries.mapNotNull { e ->
            val url = e.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = e.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CatalogImage(
                family = DeviceFamily.CHROMEBOOK,
                manufacturer = e.manufacturer?.trim()?.takeIf { it.isNotEmpty() } ?: OTHER,
                name = name,
                channel = e.channel?.uppercase() ?: "STABLE",
                version = e.version,
                url = url,
                downloadBytes = e.zipfilesize?.takeIf { it > 1 },
                imageBytes = e.filesize?.takeIf { it > 0 },
                digest = e.sha1?.takeIf { it.isNotBlank() }?.let { ExpectedDigest.sha1(it) },
                hwidMatch = e.hwidmatch?.takeIf { it.isNotBlank() },
                photoUrl = e.photourl,
                isFlex = isFlex,
            )
        }
    }

    fun googlebook(json: String): List<CatalogImage> {
        val type = object : TypeToken<List<GooglebookEntry>>() {}.type
        val entries: List<GooglebookEntry> = parse(json, type)
        return entries.mapNotNull { e ->
            val url = e.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = (e.name ?: e.model)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CatalogImage(
                family = DeviceFamily.GOOGLEBOOK,
                manufacturer = e.manufacturer?.trim()?.takeIf { it.isNotEmpty() } ?: OTHER,
                name = name,
                channel = e.channel?.uppercase() ?: "STABLE",
                version = e.buildIdentifier,
                url = url,
                // The catalog writes 1 here as a placeholder; treat it as unknown.
                downloadBytes = e.zipfilesize?.takeIf { it > 1 },
                imageBytes = e.filesize?.takeIf { it > 0 },
                digest = e.md5?.takeIf { it.isNotBlank() }?.let { ExpectedDigest.md5(it) },
            )
        }
    }

    private fun <T> parse(json: String, type: java.lang.reflect.Type): List<T> = try {
        gson.fromJson<List<T>>(json, type).orEmpty().filterNotNull()
    } catch (e: JsonParseException) {
        emptyList()
    } catch (e: IllegalStateException) {
        emptyList()
    }

    private const val OTHER = "Other"
}
