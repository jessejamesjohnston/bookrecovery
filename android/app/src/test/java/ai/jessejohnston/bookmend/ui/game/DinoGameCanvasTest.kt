package ai.jessejohnston.bookmend.ui.game

import ai.jessejohnston.bookmend.ui.theme.BookmendTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.requestFocus
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The waiting game, played: taps, swipes and keys reach the engine, frames draw, crashes report. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DinoGameCanvasTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun inputMovesTheRunnerAndACrashIsReported() {
        val engine = DinoGameEngine(Random(1))
        var crashes = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BookmendTheme(dynamicColor = false) {
                DinoGameCanvas(engine, active = true, Modifier.fillMaxWidth().height(160.dp), onCrashed = { _, _ -> crashes++ })
            }
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onNode(isFocusable()).requestFocus()
        compose.onNode(isFocusable()).performKeyInput { pressKey(Key.Spacebar) }
        repeat(20) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(DinoGameEngine.GameStatus.RUNNING, engine.gameStatus)
        compose.onNode(isFocusable()).performTouchInput { click(center) }
        repeat(20) { compose.mainClock.advanceTimeByFrame() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/game.png", roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))

        compose.onNode(isFocusable()).performTouchInput { swipeDown() }
        repeat(5) { compose.mainClock.advanceTimeByFrame() }
        compose.onNode(isFocusable()).performKeyInput { pressKey(Key.Spacebar) }
        compose.onNode(isFocusable()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(isFocusable()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNode(isFocusable()).performKeyInput { pressKey(Key.A) }

        // Without jumping, the runner meets an obstacle sooner or later.
        var frames = 0
        while (engine.gameStatus != DinoGameEngine.GameStatus.CRASHED && frames++ < 6000) {
            compose.mainClock.advanceTimeByFrame()
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/game-over.png", roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        assertTrue(crashes >= 1)
        compose.onNode(isFocusable()).performTouchInput { click(center) }
        repeat(5) { compose.mainClock.advanceTimeByFrame() }
    }
}
