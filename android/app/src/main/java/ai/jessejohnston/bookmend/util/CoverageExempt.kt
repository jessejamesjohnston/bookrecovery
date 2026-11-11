package ai.jessejohnston.bookmend.util

/**
 * Marks code the coverage gate leaves out, because a JVM test can't run it:
 * the last thin layer over real USB hardware, and Android 17 APIs that
 * Robolectric doesn't model yet. Everything else counts toward the 95%.
 * [reason] is required so each exemption explains itself; the README lists
 * them all.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class CoverageExempt(val reason: String)
