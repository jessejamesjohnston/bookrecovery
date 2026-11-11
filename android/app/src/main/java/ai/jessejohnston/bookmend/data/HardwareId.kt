package ai.jessejohnston.bookmend.data

/**
 * Finds a Chromebook from the hardware ID its recovery screen shows (for
 * example "SPHERION C4B-K3E"). Catalog entries carry a regular expression per
 * model; a bad expression in the catalog is skipped, never thrown.
 */
object HardwareId {

    /** Normalizes what a person typed: trims, collapses spaces, upper-cases. */
    fun normalize(input: String): String = input.trim().replace(Regex("\\s+"), " ").uppercase()

    /** The model names whose pattern matches [input], best guess first. */
    fun match(input: String, images: List<CatalogImage>): List<String> {
        val id = normalize(input)
        if (id.isEmpty()) return emptyList()
        return images.asSequence()
            .filter { it.hwidMatch != null }
            .filter { image ->
                runCatching { Regex(image.hwidMatch!!, RegexOption.IGNORE_CASE).containsMatchIn(id) }
                    .getOrDefault(false)
            }
            .map { it.name }
            .distinct()
            .toList()
    }
}
