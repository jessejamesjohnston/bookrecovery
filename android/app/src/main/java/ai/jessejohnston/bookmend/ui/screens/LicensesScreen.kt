package ai.jessejohnston.bookmend.ui.screens

import ai.jessejohnston.bookmend.R
import androidx.annotation.RawRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** A component Bookmend ships, and the license it ships under. */
data class Notice(val name: String, val holder: String, val license: String, @param:RawRes val text: Int)

val Notices = listOf(
    Notice("Bookmend", "Copyright 2026 Jesse Johnston", "Apache License 2.0", R.raw.license_apache_2_0),
    Notice("Book Recovery Utility", "Alexander (github.com/kuscher), relicensed with permission", "Apache License 2.0", R.raw.license_apache_2_0),
    Notice("Android Jetpack (AndroidX, Jetpack Compose, Material 3)", "The Android Open Source Project", "Apache License 2.0", R.raw.license_apache_2_0),
    Notice("Material Icons", "Google LLC", "Apache License 2.0", R.raw.license_apache_2_0),
    Notice("Kotlin and kotlinx.coroutines", "JetBrains s.r.o. and contributors", "Apache License 2.0", R.raw.license_apache_2_0),
    Notice("Gson", "Google LLC", "Apache License 2.0", R.raw.license_apache_2_0),
    Notice("Figtree", "The Figtree Project Authors", "SIL Open Font License 1.1", R.raw.license_figtree_ofl),
    Notice("T-Rex runner sprites", "The Chromium Authors", "BSD 3-Clause License", R.raw.license_chromium_bsd),
)

/** Every third-party notice, each with its full text a tap away. */
@Composable
fun LicensesScreen() {
    StepLayout {
        StepBody(stringResource(R.string.licenses_intro), Modifier.padding(top = 8.dp))
        Notices.forEach { NoticeCard(it) }
        Text(
            stringResource(R.string.licenses_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun NoticeCard(notice: Notice) {
    val context = LocalContext.current
    var open by rememberSaveable(notice.name) { mutableStateOf(false) }
    val text = remember(notice.text, open) {
        if (open) context.resources.openRawResource(notice.text).bufferedReader().use { it.readText() } else ""
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(notice.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(notice.holder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(notice.license, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (open) {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(top = 8.dp))
                }
            }
            TextButton(onClick = { open = !open }, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(if (open) R.string.licenses_hide else R.string.licenses_show))
            }
        }
    }
}
