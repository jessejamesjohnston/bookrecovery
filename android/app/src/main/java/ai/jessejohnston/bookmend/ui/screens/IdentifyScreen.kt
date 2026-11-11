package ai.jessejohnston.bookmend.ui.screens

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.ui.CatalogState
import ai.jessejohnston.bookmend.ui.IdentifyResult
import ai.jessejohnston.bookmend.ui.art.RecoveryScreenLaptop
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * Chromebooks name themselves on the recovery screen ("SPHERION C4B-K3E").
 * Typing that finds the exact model; browsing is the fallback.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IdentifyScreen(
    catalog: CatalogState,
    identify: (String) -> IdentifyResult,
    onRetry: () -> Unit,
    onFound: (String) -> Unit,
    onBrowse: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<IdentifyResult?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val ready = catalog is CatalogState.Ready

    fun submit() {
        if (!ready || input.isBlank()) return
        keyboard?.hide()
        val found = identify(input)
        result = found
        if (found is IdentifyResult.Match) onFound(found.modelName)
    }

    StepLayout(
        actions = {
            Button(
                onClick = ::submit,
                enabled = ready && input.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (catalog is CatalogState.Loading) {
                    LoadingIndicator(Modifier.size(24.dp))
                } else {
                    Text(stringResource(R.string.identify_find), style = MaterialTheme.typography.titleMedium)
                }
            }
            OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.identify_browse))
            }
        }
    ) {
        RecoveryScreenLaptop(Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp))
        StepTitle(stringResource(R.string.identify_title))
        StepBody(stringResource(R.string.identify_body))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; result = null },
            label = { Text(stringResource(R.string.identify_field)) },
            singleLine = true,
            isError = result is IdentifyResult.NoMatch,
            supportingText = (result as? IdentifyResult.NoMatch)?.let { miss ->
                { Text(stringResource(R.string.identify_no_match, miss.input), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier.fillMaxWidth()
        )
        (result as? IdentifyResult.Several)?.let { several ->
            Text(
                stringResource(R.string.identify_several),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            several.modelNames.forEach { name ->
                ListItem(
                    headlineContent = { Text(name) },
                    modifier = Modifier.fillMaxWidth().clickable { onFound(name) }
                )
            }
        }
        if (catalog is CatalogState.Unavailable) {
            Text(stringResource(R.string.models_unavailable_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.models_unavailable_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
        }
    }
}

