package ai.jessejohnston.bookmend.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A destructive action that takes a deliberate press: the fill grows while
 * held and the action runs when it is full; letting go early drains it. A
 * screen reader or keyboard user activates it directly (holding isn't
 * practical with TalkBack), since the sheet around it already asks.
 */
@Composable
fun HoldToConfirmButton(
    label: String,
    holdingLabel: String,
    accessibilityLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 1200,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val container = MaterialTheme.colorScheme.errorContainer
    val fill = MaterialTheme.colorScheme.error
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(container)
            // One node for screen readers: a button, named by its label.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = accessibilityLabel) { onConfirm(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val run = scope.launch {
                        progress.animateTo(1f, tween((holdMillis * (1f - progress.value)).toInt(), easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onConfirm()
                    }
                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        run.cancel()
                        scope.launch { progress.animateTo(0f, tween(250)) }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(fill))
        Text(
            if (progress.value > 0f) holdingLabel else label,
            style = MaterialTheme.typography.titleMedium,
            color = if (progress.value > 0.5f) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
