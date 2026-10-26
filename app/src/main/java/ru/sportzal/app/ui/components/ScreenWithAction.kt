package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.sportzal.app.R

/** The body owns its scrolling; the action occupies separate space, never an overlay. */
@Composable
fun ScreenWithAction(bottomAction: (@Composable () -> Unit)?, error: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        if (bottomAction != null) Surface(Modifier.testTag("screen-action"), color = MaterialTheme.colorScheme.surface) {
            Column {
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    error?.let { ActionError(it) }
                    bottomAction()
                }
            }
        }
    }
}

/** One line keeps the action and editable body usable above an IME. Full text remains available. */
@Composable
private fun ActionError(message: String) {
    var details by rememberSaveable(message) { mutableStateOf(false) }
    TextButton({ details = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
        Text(message, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium)
        Icon(painterResource(R.drawable.sz_ic_chevron_right), "Подробности ошибки", Modifier.size(20.dp))
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("Ошибка") },
        text = { Text(message, Modifier.verticalScroll(rememberScrollState()).testTag("action-error-detail")) },
        confirmButton = { TextButton({ details = false }) { Text("Закрыть") } })
}
