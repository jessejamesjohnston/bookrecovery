package ai.jessejohnston.bookmend.ui.art

import ai.jessejohnston.bookmend.ui.theme.Brand
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/*
 * Bookmend's own illustrations, drawn in code so they stay sharp at any size
 * and, apart from the mend itself, take their colors from the theme (and so
 * from the wallpaper). All are decorative: the text beside them carries the
 * meaning, so none has a content description.
 */

/** Draws in a [w] x [h] design space, scaled to fit and centered in the canvas. */
private fun DrawScope.inDesignSpace(w: Float, h: Float, block: DrawScope.() -> Unit) {
    val s = minOf(size.width / w, size.height / h)
    withTransform({
        translate((size.width - w * s) / 2f, (size.height - h * s) / 2f)
        scale(s, s, pivot = Offset.Zero)
    }) { block() }
}

private fun polyline(vararg points: Float) = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1]); i += 2
    }
}

/** The mend: the app icon's book, crack and three coral stitches, in brand colors. */
@Composable
fun MendBook(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        inDesignSpace(52f, 62f) {
            drawRoundRect(Brand.PaperEdge, Offset(6f, 2f), Size(40f, 54f), CornerRadius(5f))
            drawRoundRect(Brand.Navy, Offset(2f, 0f), Size(42f, 58f), CornerRadius(6f))
            drawRoundRect(Brand.NavyDeep, Offset(2f, 0f), Size(8f, 58f), CornerRadius(4f))
            drawPath(
                polyline(21f, 8f, 28f, 20f, 22f, 29f, 32f, 48f),
                Brand.Paper,
                style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            listOf(19f to 17f, 30f to 12f, 20f to 27f, 30f to 23f, 22f to 38f, 33f to 35f)
                .chunked(2)
                .forEach { (a, b) ->
                    drawLine(Brand.Coral, Offset(a.first, a.second), Offset(b.first, b.second), 3.2f, StrokeCap.Round)
                }
        }
    }
}

/** A phone with a USB drive on an adapter: what to plug in, and where. */
@Composable
fun PhoneAndDrive(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val body = MaterialTheme.colorScheme.surfaceContainerHighest
    val screen = MaterialTheme.colorScheme.primaryContainer
    val accent = MaterialTheme.colorScheme.secondary
    val drive = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        inDesignSpace(120f, 100f) {
            // Phone
            drawRoundRect(body, Offset(14f, 6f), Size(44f, 84f), CornerRadius(9f))
            drawRoundRect(ink, Offset(14f, 6f), Size(44f, 84f), CornerRadius(9f), style = Stroke(2.5f))
            drawRoundRect(screen, Offset(19f, 13f), Size(34f, 66f), CornerRadius(4f))
            drawCircle(accent, 5f, Offset(36f, 40f))
            drawRoundRect(accent.copy(alpha = .35f), Offset(24f, 52f), Size(24f, 4f), CornerRadius(2f))
            drawRoundRect(accent.copy(alpha = .35f), Offset(27f, 60f), Size(18f, 4f), CornerRadius(2f))
            // Cable from the phone's port to the adapter
            drawPath(
                Path().apply { moveTo(36f, 90f); cubicTo(36f, 98f, 62f, 98f, 70f, 80f) },
                ink, style = Stroke(2.5f, cap = StrokeCap.Round)
            )
            // Adapter and drive
            drawRoundRect(ink, Offset(66f, 66f), Size(10f, 16f), CornerRadius(3f))
            drawRoundRect(drive, Offset(76f, 40f), Size(26f, 44f), CornerRadius(6f))
            drawRoundRect(ink, Offset(76f, 40f), Size(26f, 44f), CornerRadius(6f), style = Stroke(2f))
            drawRoundRect(Color.White.copy(alpha = .85f), Offset(83f, 48f), Size(12f, 3f), CornerRadius(1.5f))
            drawCircle(accent, 3.5f, Offset(89f, 72f))
        }
    }
}

/** A laptop on its recovery screen, with the model name line highlighted. */
@Composable
fun RecoveryScreenLaptop(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val shell = MaterialTheme.colorScheme.surfaceContainerHighest
    val panel = MaterialTheme.colorScheme.surface
    val line = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        inDesignSpace(140f, 96f) {
            drawRoundRect(shell, Offset(18f, 4f), Size(104f, 70f), CornerRadius(6f))
            drawRoundRect(ink, Offset(18f, 4f), Size(104f, 70f), CornerRadius(6f), style = Stroke(2.5f))
            drawRect(panel, Offset(24f, 10f), Size(92f, 58f))
            drawCircle(line, 6f, Offset(70f, 24f))
            drawRoundRect(line, Offset(44f, 36f), Size(52f, 4f), CornerRadius(2f))
            drawRoundRect(line, Offset(52f, 44f), Size(36f, 4f), CornerRadius(2f))
            // The line that carries the model name, called out
            drawRoundRect(highlight.copy(alpha = .18f), Offset(36f, 53f), Size(68f, 10f), CornerRadius(3f))
            drawRoundRect(highlight, Offset(40f, 56.5f), Size(60f, 3.5f), CornerRadius(1.75f))
            drawRoundRect(highlight, Offset(36f, 53f), Size(68f, 10f), CornerRadius(3f), style = Stroke(1.5f))
            // Base
            drawPath(
                polyline(8f, 80f, 132f, 80f, 124f, 88f, 16f, 88f, 8f, 80f),
                shell
            )
            drawPath(
                polyline(8f, 80f, 132f, 80f, 124f, 88f, 16f, 88f, 8f, 80f),
                ink, style = Stroke(2.5f, join = StrokeJoin.Round)
            )
        }
    }
}
