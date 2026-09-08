package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

enum class NumericKind { WEIGHT, REPS }

fun parseNumeric(text: String, kind: NumericKind): Number? = when (kind) {
    NumericKind.WEIGHT -> text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    NumericKind.REPS -> text.toIntOrNull()?.takeIf { it >= 0 }
}

@Composable
fun NumericField(
    value: String,
    label: String,
    kind: NumericKind,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            val allowed = when (kind) {
                NumericKind.WEIGHT -> candidate.matches(Regex("[0-9]*([.,][0-9]*)?"))
                NumericKind.REPS -> candidate.all(Char::isDigit)
            }
            if (allowed) onValueChange(candidate)
        },
        modifier = modifier.heightIn(min = 48.dp).widthIn(min = 96.dp),
        enabled = enabled,
        label = { Text(label) },
        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(keyboardType = if (kind == NumericKind.WEIGHT) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
    )
}
