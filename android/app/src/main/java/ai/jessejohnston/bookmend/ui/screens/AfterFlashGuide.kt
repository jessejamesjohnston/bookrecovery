package ai.jessejohnston.bookmend.ui.screens

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.data.DeviceFamily
import ai.jessejohnston.bookmend.usb.UsbFlasher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** How sure the finished drive is, in a word: Google's checksum, or every byte read back. */
@Composable
fun VerificationChip(verification: UsbFlasher.Verification) {
    val label = when (verification) {
        UsbFlasher.Verification.AUTHENTIC -> R.string.flash_verified_chip
        UsbFlasher.Verification.WRITE_VERIFIED -> R.string.flash_checked_chip
        UsbFlasher.Verification.SKIPPED -> return
    }
    AssistChip(
        onClick = {},
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(Icons.Outlined.Verified, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
        colors = AssistChipDefaults.assistChipColors(
            leadingIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}

/**
 * What to do with the drive, in order, with the right recovery keys for the
 * family. Steps follow Google's public help pages for each.
 */
@Composable
fun AfterFlashGuide(family: DeviceFamily, modifier: Modifier = Modifier) {
    val device = stringResource(if (family == DeviceFamily.GOOGLEBOOK) R.string.guide_device_googlebook else R.string.guide_device_chromebook)
    val steps = listOf(
        stringResource(R.string.guide_unplug),
        stringResource(R.string.guide_disconnect),
        stringResource(if (family == DeviceFamily.GOOGLEBOOK) R.string.guide_keys_googlebook else R.string.guide_keys_chromebook),
        stringResource(R.string.guide_insert),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.guide_title, device),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            steps.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(26.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.guide_erases), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "8 min", "40 s", "1 h 5 min": compact and unambiguous. */
fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h" + (seconds % 3600 / 60).let { if (it > 0) " $it min" else "" }
}
