package ai.jessejohnston.bookmend.data

import ai.jessejohnston.bookmend.usb.ExpectedDigest

/** What is being recovered. The two families use different catalogs and recovery keys. */
enum class DeviceFamily { CHROMEBOOK, GOOGLEBOOK }

/**
 * One recovery image a person can choose: a model on a channel, wherever it
 * came from. ChromeOS catalogs publish a SHA-1 of the download and match
 * models by hardware ID; the Googlebook catalog publishes an MD5 of the
 * download and is browsed by manufacturer and name.
 */
data class CatalogImage(
    val family: DeviceFamily,
    val manufacturer: String,
    val name: String,
    val channel: String,
    val version: String?,
    val url: String,
    /** The zip as served, when the catalog knows it. */
    val downloadBytes: Long?,
    /** The image written to the drive: also the smallest drive that can hold it. */
    val imageBytes: Long?,
    val digest: ExpectedDigest?,
    val hwidMatch: String? = null,
    val photoUrl: String? = null,
    /** ChromeOS Flex (formerly CloudReady) images, for PCs and Macs. */
    val isFlex: Boolean = false,
) {
    /** Stable across catalog refreshes on the same day; used to save and hand off a choice. */
    val key: String get() = url
}

/** Channel order people expect: the safe default first. */
internal val CHANNEL_ORDER = listOf("STABLE", "LTS", "LTC", "LTR", "BETA", "DEV", "CANARY")

fun List<CatalogImage>.sortedByChannel(): List<CatalogImage> =
    sortedBy { CHANNEL_ORDER.indexOf(it.channel.uppercase()).let { i -> if (i < 0) Int.MAX_VALUE else i } }
