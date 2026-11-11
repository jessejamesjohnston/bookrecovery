package ai.jessejohnston.bookmend.ui.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The long game: milestones, night, flying obstacles, and the idle blink. */
class DinoGameEngineMoreTest {

    private val frame = 1000f / 60f

    @Test
    fun theRunnerBlinksWhileWaiting() {
        val engine = DinoGameEngine(Random(4))
        var blinked = false
        repeat(60 * 20) {
            engine.update(frame)
            if (engine.blinking) blinked = true
        }
        assertTrue(blinked)
        assertFalse("blinks end", engine.blinking && engine.trexFrame == 0)
    }

    @Test
    fun aLongRunFlashesMilestonesAndTurnsToNight() {
        val engine = DinoGameEngine(Random(9))
        engine.onJumpPressed()
        engine.onJumpReleased()
        var flashed = false
        var night = false
        var dayAgain = false
        var pterodactylFlapped = false
        var steps = 0
        while (steps++ < 60 * 60 * 8 && !(night && dayAgain)) {
            engine.update(frame)
            // God mode: anything that gets close to the runner is lifted away.
            engine.obstacles.removeAll { it.x < engine.trexX + 70f }
            if (engine.obstacles.any { it.type == DinoGameEngine.ObstacleType.PTERODACTYL && it.frame == 1 }) pterodactylFlapped = true
            if (engine.achievementFlashOn) flashed = true
            if (engine.nightMode) night = true
            if (night && !engine.nightMode) dayAgain = true
        }
        assertEquals(DinoGameEngine.GameStatus.RUNNING, engine.gameStatus)
        assertTrue("score milestones flash", flashed)
        assertTrue("night falls at 700", night)
        assertTrue("and lifts again", dayAgain)
        assertTrue("pterodactyls flap at speed", pterodactylFlapped)
        assertTrue(engine.moonPhase > 0)
    }

    @Test
    fun releasingADuckStandsTheRunnerUp() {
        val engine = DinoGameEngine(Random(2))
        engine.onDuckPressed()
        engine.onDuckReleased()
        assertEquals(DinoGameEngine.TrexStatus.WAITING, engine.trexStatus)

        engine.onJumpPressed(); engine.onJumpReleased()
        repeat(120) { engine.update(frame) }
        engine.onDuckPressed()
        assertEquals(DinoGameEngine.TrexStatus.DUCKING, engine.trexStatus)
        engine.onDuckReleased()
        assertEquals(DinoGameEngine.TrexStatus.RUNNING, engine.trexStatus)
    }
}
