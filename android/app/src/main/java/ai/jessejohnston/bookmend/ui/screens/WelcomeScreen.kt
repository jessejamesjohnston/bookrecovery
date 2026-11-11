package ai.jessejohnston.bookmend.ui.screens

import ai.jessejohnston.bookmend.ui.art.MendBook
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.ui.wizardContentWidth

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .wizardContentWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MendBook(modifier = Modifier.fillMaxWidth().height(140.dp).padding(bottom = 32.dp))
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onNext()
            },
            // Expressive button shapes: the corners morph slightly on press.
            shapes = ButtonDefaults.shapes()
        ) {
            Text(stringResource(R.string.welcome_get_started))
        }
    }
}
