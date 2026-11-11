package ai.jessejohnston.bookmend.ui.screens

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.data.DeviceFamily
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LaptopChromebook
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Chromebook or Googlebook: they use different catalogs and different recovery keys. */
@Composable
fun FamilyScreen(selected: DeviceFamily?, onChoose: (DeviceFamily) -> Unit) {
    StepLayout {
        StepTitle(stringResource(R.string.family_title), Modifier.padding(top = 16.dp))
        StepBody(stringResource(R.string.family_body))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
            modifier = Modifier.fillMaxWidth()
        ) {
            FamilyCard(
                icon = Icons.Outlined.LaptopChromebook,
                title = stringResource(R.string.family_chromebook),
                hint = stringResource(R.string.family_chromebook_hint),
                isSelected = selected == DeviceFamily.CHROMEBOOK,
                onClick = { onChoose(DeviceFamily.CHROMEBOOK) },
                modifier = Modifier.weight(1f).widthIn(min = 260.dp)
            )
            FamilyCard(
                icon = Icons.Outlined.Laptop,
                title = stringResource(R.string.family_googlebook),
                hint = stringResource(R.string.family_googlebook_hint),
                isSelected = selected == DeviceFamily.GOOGLEBOOK,
                onClick = { onChoose(DeviceFamily.GOOGLEBOOK) },
                modifier = Modifier.weight(1f).widthIn(min = 260.dp)
            )
        }
    }
}

@Composable
private fun FamilyCard(
    icon: ImageVector,
    title: String,
    hint: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.semantics {
            role = Role.Button
            this.selected = isSelected
        }
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
