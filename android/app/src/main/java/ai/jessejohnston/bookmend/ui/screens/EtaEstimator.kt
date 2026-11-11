package ai.jessejohnston.bookmend.ui.screens

/**
 * Time left for a long write, from progress samples. It waits for enough
 * evidence (a few percent and some seconds) before guessing, smooths the rate
 * so one slow chunk doesn't swing the number, and rounds to what a person
 * plans around.
 */
class EtaEstimator(private val minFraction: Float = 0.02f, private val minElapsedMs: Long = 8_000) {

    private var startMs: Long? = null
    private var lastMs = 0L
    private var lastFraction = 0f
    private var rate: Double? = null // fraction per millisecond, smoothed

    fun reset() {
        startMs = null; rate = null; lastFraction = 0f
    }

    /** Adds a sample; returns whole seconds left, or null while it can't tell yet. */
    fun update(nowMs: Long, fraction: Float): Long? {
        val start = startMs ?: run { startMs = nowMs; lastMs = nowMs; lastFraction = fraction; return null }
        val dt = nowMs - lastMs
        if (dt > 0 && fraction > lastFraction) {
            val instant = (fraction - lastFraction).toDouble() / dt
            rate = rate?.let { 0.8 * it + 0.2 * instant } ?: ((fraction.toDouble()) / (nowMs - start).coerceAtLeast(1))
            lastMs = nowMs
            lastFraction = fraction
        }
        val r = rate ?: return null
        if (fraction < minFraction || nowMs - start < minElapsedMs || r <= 0.0) return null
        val ms = (1.0 - fraction) / r
        return roundForPeople((ms / 1000).toLong())
    }

    companion object {
        /** Under a minute stays in seconds (to 10 s); under an hour, whole minutes. */
        fun roundForPeople(seconds: Long): Long = when {
            seconds < 60 -> ((seconds + 9) / 10) * 10
            seconds < 3600 -> ((seconds + 59) / 60) * 60
            else -> ((seconds + 299) / 300) * 300
        }
    }
}
