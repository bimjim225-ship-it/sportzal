package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.serialization.decodeFromString
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.model.StrictJson
import ru.sportzal.app.ui.workout.InteractionSource
import ru.sportzal.app.domain.ActualContext
import ru.sportzal.app.ui.text.compactRirLabel
import ru.sportzal.app.ui.text.deviationLabel
import ru.sportzal.app.ui.text.loadBasisLabel
import ru.sportzal.app.ui.text.sideLabel

internal val deviationCodes = listOf("range_shortened", "technique_changed", "discomfort", "setup_changed",
    "equipment_changed", "exercise_changed", "other")

@Composable
fun SetResultRow(
    result: SetResultEntity,
    saving: Boolean,
    showRir: Boolean,
    onInteraction: (InteractionSource, Boolean) -> Unit,
    onEdit: (Double, Int, Int?, List<String>, String?, (Boolean) -> Unit) -> Unit,
    onDelete: ((Boolean) -> Unit) -> Unit,
    onEditContext: (Double, Int, Int?, List<String>, String?, ActualContext, (Boolean) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    equipmentChoices: List<ru.sportzal.app.model.EquipmentDocument> = emptyList(),
) {
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun closeAll() { menu = false; edit = false; confirmDelete = false; onInteraction(InteractionSource.SET_ACTIONS, false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${result.plannedSetNo?.let { "$it." } ?: "Доп."} ${result.weightKg.g} кг × ${result.reps}" + (result.rir?.let { " · ${compactRirLabel(it)}" } ?: ""), modifier = Modifier.weight(1f))
        IconButton({ menu = true; onInteraction(InteractionSource.SET_ACTIONS, true) }, enabled = !saving,
            modifier = Modifier.semantics { contentDescription = "Меню записанного подхода" }
                .testTag("set-actions-${result.setResultId}")) { Text("⋮") }
        DropdownMenu(menu, onDismissRequest = ::closeAll) {
            DropdownMenuItem({ Text("Изменить") }, { menu = false; edit = true })
            DropdownMenuItem({ Text("Удалить") }, { menu = false; confirmDelete = true })
        }
    }
    if (edit) EditSetDialog(result, showRir, saving, ::closeAll, equipmentChoices) { weight, reps, rir, deviations, note, context ->
        onEditContext(weight, reps, rir, deviations, note, context) { committed -> if (committed) closeAll() }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!saving) closeAll() }, title = { Text("Удалить этот подход?") },
        text = { Text(if (result.plannedSetNo != null) "Плановый подход снова станет незаполненным." else "Дополнительный подход будет удалён из истории.") },
        dismissButton = { TextButton(::closeAll, enabled = !saving) { Text("Отмена") } },
        confirmButton = { TextButton({ onDelete { committed -> if (committed) closeAll() } }, enabled = !saving,
            modifier = Modifier.testTag("confirm-delete")) { Text("Удалить") } })
}

@Composable
internal fun EditSetDialog(
    result: SetResultEntity, showRir: Boolean, saving: Boolean, onDismiss: () -> Unit,
    equipmentChoices: List<ru.sportzal.app.model.EquipmentDocument> = emptyList(),
    onSave: (Double, Int, Int?, List<String>, String?, ActualContext) -> Unit,
) {
    var weight by remember { mutableStateOf(result.weightKg.g) }
    var reps by remember { mutableStateOf(result.reps.toString()) }
    var rir by remember { mutableStateOf(result.rir) }
    var deviations by remember { mutableStateOf(runCatching { StrictJson.decodeFromString<List<String>>(result.deviationsJson) }.getOrDefault(emptyList()).toSet()) }
    var note by remember { mutableStateOf(result.note.orEmpty()) }
    var equipmentId by remember { mutableStateOf(result.equipmentIdActual.orEmpty()) }
    var equipmentName by remember { mutableStateOf(result.equipmentNameActual.orEmpty()) }
    var manualEquipment by remember { mutableStateOf(equipmentChoices.none { it.equipmentId == result.equipmentIdActual }) }
    var setup by remember { mutableStateOf(result.setupActual.orEmpty()) }
    var loadBasis by remember { mutableStateOf(result.loadBasisActual) }
    var side by remember { mutableStateOf(result.sideActual) }
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    val parsedReps = reps.toIntOrNull()
    val valid = parsedWeight != null && parsedWeight.isFinite() && parsedWeight >= 0 && parsedReps != null && parsedReps >= 0 &&
        (loadBasis != "bodyweight" || parsedWeight == 0.0)
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Подход ${result.plannedSetNo ?: result.sequenceNo}") },
        text = { Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(weight, { weight = it }, label = { Text("Вес") }, enabled = loadBasis != "bodyweight")
            OutlinedTextField(reps, { reps = it }, label = { Text("Повторы") }, modifier = Modifier.testTag("edit-reps"))
            Text("Оборудование", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            equipmentChoices.forEach { item -> FilterChip(!manualEquipment && equipmentId == item.equipmentId, {
                if (equipmentId != item.equipmentId) { weight = ""; equipmentId = item.equipmentId }
                manualEquipment = false; equipmentName = item.name; setup = item.setupHint.orEmpty()
            }, { Text(item.name) }) }
            TextButton({
                manualEquipment = true; equipmentId = "local:${java.util.UUID.randomUUID()}"; equipmentName = ""; weight = ""
            }) { Text("Указать другое оборудование") }
            if (manualEquipment) OutlinedTextField(equipmentName, { equipmentName = it }, label = { Text("Название оборудования") })
            OutlinedTextField(setup, { setup = it }, label = { Text("Настройка тренажёра") }, modifier = Modifier.testTag("edit-setup"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                loadBases.forEach { value -> FilterChip(loadBasis == value, { if (loadBasis != value) {
                loadBasis = value; weight = if (value == "bodyweight") "0" else ""
            } }, { Text(loadBasisLabel(value)) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("bilateral", "left", "right").forEach { value -> FilterChip(side == value, { side = value }, { Text(sideLabel(value)) }) }
            }
            if (showRir) {
                Text("Запас повторов")
                RirSelector(rir, answered = true, enabled = !saving) { rir = it }
            }
            Text("Отклонения")
            deviationCodes.forEach { value -> FilterChip(value in deviations, {
                deviations = if (value in deviations) deviations - value else deviations + value
            }, { Text(deviationLabel(value)) }, modifier = Modifier.testTag("deviation-$value")) }
            OutlinedTextField(note, { note = it }, label = { Text("Комментарий") })
        } },
        dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Отмена") } },
        confirmButton = { TextButton({ onSave(parsedWeight!!, parsedReps!!, rir, deviations.toList(), note,
            ActualContext(result.exerciseIdActual, equipmentId.trim().ifEmpty { null }, setup.trim().ifEmpty { null },
                loadBasis, side, equipmentName.trim().ifEmpty { null })) },
            enabled = valid && !saving, modifier = Modifier.testTag("save-edit")) { Text("Сохранить") } })
}

private val loadBases = listOf("machine_display", "total_external", "per_hand", "assistance", "bodyweight")

private val Double.g: String get() = (if (this % 1.0 == 0.0) toLong().toString() else toString()).replace('.', ',')
