package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.sportzal.app.R

@Composable
fun AppHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null,
    compact: Boolean = false, action: @Composable RowScope.() -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onBack) { Icon(painterResource(R.drawable.sz_ic_back), "Назад") }
            Text(title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            action()
        }
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun SportzalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, busy: Boolean = false, secondary: Boolean = false) {
    val size = modifier.fillMaxWidth().heightIn(min = 56.dp)
    val content: @Composable RowScope.() -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = if (busy) androidx.compose.ui.graphics.Color.Transparent else androidx.compose.ui.graphics.Color.Unspecified)
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary)
        }
    }
    if (secondary) OutlinedButton(onClick, size, shape = MaterialTheme.shapes.medium, enabled = enabled && !busy, content = content)
    else Button(onClick, size, shape = MaterialTheme.shapes.medium, enabled = enabled && !busy, content = content)
}

@Composable
fun StatusNotice(title: String, body: String? = null, error: Boolean = false,
    actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Surface(shape = MaterialTheme.shapes.medium,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (actionLabel != null && onAction != null) TextButton(onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(label: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 160.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        CircularProgressIndicator(Modifier.size(28.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OpenFailure(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusNotice(message, error = true)
        SportzalButton("Повторить", onRetry)
        SportzalButton("На главный экран", onBack, secondary = true)
    }
}
