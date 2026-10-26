package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The body owns its scrolling; the action occupies separate space, never an overlay. */
@Composable
fun ScreenWithAction(bottomAction: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        if (bottomAction != null) Surface(Modifier.testTag("screen-action"), color = MaterialTheme.colorScheme.surface) {
            Column {
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) { bottomAction() }
            }
        }
    }
}
