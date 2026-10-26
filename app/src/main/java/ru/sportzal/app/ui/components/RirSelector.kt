package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.sportzal.app.ui.text.rirLabel

@Composable
fun RirSelector(value: Int?, answered: Boolean, enabled: Boolean = true, onValueChange: (Int?) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(0, 1, 2, 3, 4, null).forEach { item ->
            FilterChip(selected = answered && value == item, onClick = { onValueChange(item) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
                    .semantics { contentDescription = item?.let(::rirLabel) ?: "Не оценил" }
                    .testTag("rir-${item ?: "not-assessed"}"),
                label = { Text(if (item == 4) "4+" else item?.toString() ?: "Не оценил") })
        }
    }
}
