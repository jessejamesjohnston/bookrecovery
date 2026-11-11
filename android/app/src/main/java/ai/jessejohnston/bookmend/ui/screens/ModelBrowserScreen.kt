package ai.jessejohnston.bookmend.ui.screens

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.data.CatalogImage
import ai.jessejohnston.bookmend.data.DeviceFamily
import ai.jessejohnston.bookmend.data.sortedByChannel
import ai.jessejohnston.bookmend.ui.CatalogState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Pick a model, then a version. One adaptive layout for every window: on a
 * phone the list and the model's versions are separate panes with back
 * between them; on a tablet, unfolded foldable or Googlebook window they sit
 * side by side. Resizing moves between the two without losing the choice.
 *
 * @param initialQuery Prefills the search (from a hardware-ID lookup); an
 *   exact model name opens that model directly.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ModelBrowserScreen(
    family: DeviceFamily,
    catalog: CatalogState,
    initialQuery: String?,
    selected: CatalogImage?,
    onRetry: () -> Unit,
    onChoose: (CatalogImage) -> Unit,
) {
    when (catalog) {
        CatalogState.Loading -> LoadingState()
        CatalogState.Unavailable -> UnavailableState(onRetry)
        is CatalogState.Ready -> {
            val navigator = rememberListDetailPaneScaffoldNavigator<String>()
            val scope = rememberCoroutineScope()
            val images = remember(catalog, family) { catalog.images.filter { it.family == family } }

            LaunchedEffect(initialQuery, images) {
                val exact = images.firstOrNull { it.name.equals(initialQuery, ignoreCase = true) }
                if (exact != null && navigator.currentDestination?.contentKey == null) {
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, exact.name)
                }
            }

            NavigableListDetailPaneScaffold(
                navigator = navigator,
                listPane = {
                    AnimatedPane {
                        ModelList(
                            family = family,
                            images = images,
                            initialQuery = initialQuery,
                            openModel = navigator.currentDestination?.contentKey,
                            onOpen = { name -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, name) } }
                        )
                    }
                },
                detailPane = {
                    AnimatedPane {
                        val name = navigator.currentDestination?.contentKey
                        if (name == null) {
                            CenteredState {
                                Text(
                                    stringResource(R.string.models_pick_prompt),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            ModelDetail(
                                images = images.filter { it.name == name }.sortedByChannel(),
                                selected = selected,
                                onChoose = onChoose
                            )
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingState() {
    CenteredState {
        LoadingIndicator()
        Text(stringResource(R.string.models_loading), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun UnavailableState(onRetry: () -> Unit) {
    CenteredState {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.models_unavailable_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.models_unavailable_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
    }
}

/** One row per model, grouped under sticky manufacturer headers, filtered as you type. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelList(
    family: DeviceFamily,
    images: List<CatalogImage>,
    initialQuery: String?,
    openModel: String?,
    onOpen: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val groups = remember(images, query) { groupModels(images, query) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepTitle(stringResource(if (family == DeviceFamily.GOOGLEBOOK) R.string.models_title_googlebook else R.string.models_title_chromebook))
            if (images.size > 12) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.models_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (family == DeviceFamily.GOOGLEBOOK) {
                item(key = "note") {
                    Row(
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.models_googlebook_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (groups.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.models_none_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            groups.forEach { (manufacturer, models) ->
                stickyHeader(key = "h:$manufacturer") {
                    Text(
                        manufacturer,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .semantics { heading() }
                    )
                }
                items(models, key = { "m:${it.name}" }) { model ->
                    ListItem(
                        headlineContent = { Text(model.name) },
                        supportingContent = if (model.isFlex) { { Text(stringResource(R.string.models_flex_label)) } } else null,
                        colors = ListItemDefaults.colors(
                            containerColor = if (model.name == openModel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .selectable(selected = model.name == openModel, role = Role.Button) { onOpen(model.name) }
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

/** A model's versions as choices, Stable first and marked; then the choice is confirmed. */
@Composable
private fun ModelDetail(images: List<CatalogImage>, selected: CatalogImage?, onChoose: (CatalogImage) -> Unit) {
    val first = images.firstOrNull() ?: return
    var choice by rememberSaveable(first.name) {
        mutableStateOf(images.firstOrNull { it.key == selected?.key }?.key ?: first.key)
    }
    StepLayout(
        actions = {
            Button(
                onClick = { images.firstOrNull { it.key == choice }?.let(onChoose) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.models_select_version), style = MaterialTheme.typography.titleMedium)
            }
        }
    ) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StepTitle(first.name)
            Text(first.manufacturer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.models_channel_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
        images.forEachIndexed { index, image ->
            ChannelOption(
                image = image,
                recommended = index == 0 && image.channel == "STABLE",
                isSelected = image.key == choice,
                onSelect = { choice = image.key }
            )
        }
    }
}

@Composable
private fun ChannelOption(image: CatalogImage, recommended: Boolean, isSelected: Boolean, onSelect: () -> Unit) {
    OutlinedCard(
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth().selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = isSelected, onClick = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(channelLabel(image.channel), style = MaterialTheme.typography.titleMedium)
                    if (recommended) {
                        Text(
                            stringResource(R.string.models_channel_recommended),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                image.version?.let { Text(stringResource(R.string.models_channel_version, it), style = MaterialTheme.typography.bodyMedium) }
                sizeLine(image)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun channelLabel(channel: String): String = when (channel.uppercase()) {
    "STABLE" -> stringResource(R.string.models_channel_stable)
    "BETA" -> stringResource(R.string.models_channel_beta)
    "DEV" -> stringResource(R.string.models_channel_dev)
    "LTS" -> stringResource(R.string.models_channel_lts)
    "LTC" -> stringResource(R.string.models_channel_ltc)
    "LTR" -> stringResource(R.string.models_channel_ltr)
    else -> channel.lowercase().replaceFirstChar { it.titlecase() }
}

@Composable
private fun sizeLine(image: CatalogImage): String? {
    val needs = image.imageBytes?.let(::driveSizeFor) ?: return null
    return image.downloadBytes?.let { stringResource(R.string.models_size_line, formatBytes(it), needs) }
        ?: stringResource(R.string.models_size_line_unknown, needs)
}

/** Distinct models under their manufacturer, both sorted, filtered by [query]. */
internal fun groupModels(images: List<CatalogImage>, query: String): List<Pair<String, List<CatalogImage>>> {
    val q = query.trim()
    return images
        .distinctBy { it.name }
        .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.manufacturer.contains(q, ignoreCase = true) }
        .groupBy { it.manufacturer }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .map { (manufacturer, models) -> manufacturer to models.sortedBy { it.name.lowercase() } }
}
