package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun RirSelector(value: Int?, answered: Boolean, enabled: Boolean = true, onValueChange: (Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("0" to 0, "1" to 1, "2" to 2, "3" to 3, "4+" to 4, "Не оценил" to null).forEach { (label, item) ->
            FilterChip(selected = answered && value == item, onClick = { onValueChange(item) }, enabled = enabled,
                modifier = Modifier.testTag("rir-${item ?: "not-assessed"}"), label = { Text(label) })
        }
    }
}
