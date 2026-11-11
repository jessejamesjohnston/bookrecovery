package ai.jessejohnston.bookmend.ui.screens

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.ui.art.MendBook
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The first screen: what Bookmend does, what you need, and one way in. */
@Composable
fun WelcomeScreen(onStart: () -> Unit, onUseFile: () -> Unit) {
    StepLayout(
        actions = {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.welcome_start), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onUseFile, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.menu_use_file))
            }
        }
    ) {
        MendBook(Modifier.padding(top = 16.dp).size(112.dp))
        StepTitle(stringResource(R.string.welcome_title))
        StepBody(stringResource(R.string.welcome_body))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.welcome_need_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )
                NeedRow(Icons.Outlined.Usb, stringResource(R.string.welcome_need_drive))
                NeedRow(Icons.Outlined.Cable, stringResource(R.string.welcome_need_adapter))
                NeedRow(Icons.Outlined.Wifi, stringResource(R.string.welcome_need_network))
            }
        }
    }
}

@Composable
private fun NeedRow(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
