package ai.jessejohnston.bookmend.ui

import android.net.Uri

/**
 * Every destination in the wizard. [step] places a destination in the
 * three-part indicator (Device, Drive, Write); 0 means it sits outside the
 * wizard (welcome, tools). Centralized so the graph, the indicator and every
 * navigate() call agree.
 */
sealed class Route(val pattern: String, val step: Int = 0) {
    data object Welcome : Route("welcome")
    data object Family : Route("family", step = 1)
    data object Identify : Route("identify", step = 1)
    data object Drive : Route("drive", step = 2)
    data object Flash : Route("flash", step = 3)
    data object EraseDrive : Route("erase_drive")
    data object EraseFlash : Route("erase_flash")
    data object Licenses : Route("licenses")

    /** The model browser, optionally opened on a search. */
    data object Models : Route("models?$ARG_QUERY={$ARG_QUERY}", step = 1) {
        fun withQuery(query: String? = null): String =
            if (query.isNullOrBlank()) "models" else "models?$ARG_QUERY=${Uri.encode(query)}"
    }

    companion object {
        const val ARG_QUERY = "query"
        const val STEPS = 3

        // Lazy: built eagerly, this list would hold null for whichever route
        // object was being initialized when the companion was first touched.
        val all by lazy { listOf(Welcome, Family, Identify, Models, Drive, Flash, EraseDrive, EraseFlash, Licenses) }

        fun of(pattern: String?): Route? = all.firstOrNull { it.pattern == pattern }
    }
}
