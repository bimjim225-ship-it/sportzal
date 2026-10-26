package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.sportzal.app.ui.text.rirLabel

@Composable
fun RirSelector(value: Int?, answered: Boolean, enabled: Boolean = true, onValueChange: (Int?) -> Unit) {
    var help by rememberSaveable { mutableStateOf(false) }
    Column {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text("Запас повторов", Modifier.weight(1f))
          TextButton({ help = true }) { Text("Что это?") }
      }
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
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("Как оценить запас повторов") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Сколько дополнительных повторов вы ещё смогли бы выполнить после записанного подхода?")
            Text("0 — ни одного")
            Text("1 — ещё один повтор")
            Text("2 — ещё два повтора")
            Text("3 — ещё три повтора")
            Text("4+ — четыре или больше")
            Text("Если не можете оценить, выберите «Не оценил».")
        } }, confirmButton = { TextButton({ help = false }) { Text("Понятно") } })
}
