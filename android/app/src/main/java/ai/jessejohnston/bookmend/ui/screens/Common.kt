package ai.jessejohnston.bookmend.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ai.jessejohnston.bookmend.ui.wizardContentWidth

/**
 * The shape of every wizard step on a phone: scrolling content, and the
 * step's actions pinned at the bottom where a thumb reaches them. On wide
 * windows the whole column is capped and centered.
 */
@Composable
fun StepLayout(
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.wizardContentWidth()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = actions
        )
    }
}

/** A step's title: large, emphasized, and announced as a heading. */
@Composable
fun StepTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMediumEmphasized,
        modifier = modifier.semantics { heading() }
    )
}

@Composable
fun StepBody(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Fills the screen and centers [content]: loading and empty states. */
@Composable
fun CenteredState(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        content = content
    )
}

/** Human-readable size in decimal units, as drives are sold ("32 GB"). */
fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return when {
        gb >= 10 -> "%.0f GB".format(gb)
        gb >= 1 -> "%.1f GB".format(gb).replace(".0 GB", " GB")
        else -> "%.0f MB".format(bytes / 1_000_000.0)
    }
}

/**
 * The drive size to ask for: the image size rounded up to the next size
 * drives are actually sold in.
 */
fun driveSizeFor(imageBytes: Long): String {
    val sizes = listOf(4L, 8L, 16L, 32L, 64L, 128L, 256L)
    val gb = sizes.firstOrNull { it * 1_000_000_000L >= imageBytes } ?: ((imageBytes / 1_000_000_000L) + 1)
    return "$gb GB"
}
