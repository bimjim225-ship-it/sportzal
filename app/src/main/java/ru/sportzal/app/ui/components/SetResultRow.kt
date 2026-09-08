package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.sportzal.app.data.db.SetResultEntity

@Composable
fun SetResultRow(result: SetResultEntity) {
    Row(Modifier.fillMaxWidth()) {
        Text("${result.plannedSetNo}. ${result.weightKg.g} кг × ${result.reps}" + (result.rir?.let { " · RIR ${it.rirText()}" } ?: ""))
    }
}

private val Double.g: String get() = if (this % 1.0 == 0.0) toInt().toString() else toString()
