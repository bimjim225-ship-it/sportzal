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
import kotlinx.serialization.encodeToString
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
    exerciseChoices: List<ru.sportzal.app.model.ExerciseDocument> = emptyList(),
    reloadRequired: Boolean = false,
    error: String? = null,
) {
    var menu by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var edit by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var confirmDelete by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    fun closeAll() { menu = false; edit = false; confirmDelete = false; onInteraction(InteractionSource.SET_ACTIONS, false) }
    androidx.compose.runtime.LaunchedEffect(reloadRequired) { if (reloadRequired) closeAll() }
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
    if (edit) EditSetDialog(result, showRir, saving, ::closeAll, equipmentChoices, exerciseChoices, error = error) { weight, reps, rir, deviations, note, context ->
        onEditContext(weight, reps, rir, deviations, note, context) { committed -> if (committed) closeAll() }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!saving) closeAll() }, title = { Text("Удалить этот подход?") },
        text = { Column { Text(if (result.plannedSetNo != null) "Плановый подход снова станет незаполненным." else "Дополнительный подход будет удалён из истории."); error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) } } },
        dismissButton = { TextButton(::closeAll, enabled = !saving) { Text("Отмена") } },
        confirmButton = { TextButton({ onDelete { committed -> if (committed) closeAll() } }, enabled = !saving,
            modifier = Modifier.testTag("confirm-delete")) { Text("Удалить") } })
}

@Composable
internal fun EditSetDialog(
    result: SetResultEntity, showRir: Boolean, saving: Boolean, onDismiss: () -> Unit,
    equipmentChoices: List<ru.sportzal.app.model.EquipmentDocument> = emptyList(),
    exerciseChoices: List<ru.sportzal.app.model.ExerciseDocument> = emptyList(),
    error: String? = null,
    onSave: (Double, Int, Int?, List<String>, String?, ActualContext) -> Unit,
) {
    var weight by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(result.weightKg.g) }
    var reps by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(result.reps.toString()) }
    var rir by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(result.rir) }
    var deviations by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(runCatching { StrictJson.decodeFromString<List<String>>(result.deviationsJson) }.getOrDefault(emptyList()).toSet()) }
    var note by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(result.note.orEmpty()) }
    var contextJson by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(StrictJson.encodeToString(
        ActualContext(result.exerciseIdActual, result.equipmentIdActual, result.setupActual, result.loadBasisActual,
            result.sideActual, result.equipmentNameActual, result.titleActual))) }
    val context = StrictJson.decodeFromString<ActualContext>(contextJson)
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    val parsedReps = reps.toIntOrNull()
    val valid = parsedWeight != null && parsedWeight.isFinite() && parsedWeight >= 0 && parsedReps != null && parsedReps >= 0 &&
        (context.loadBasis != "bodyweight" || parsedWeight == 0.0) && context.validForSave()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Подход ${result.plannedSetNo ?: result.sequenceNo}") },
        text = { Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(weight, { weight = it }, label = { Text("Вес") }, enabled = !saving && context.loadBasis != "bodyweight")
            OutlinedTextField(reps, { reps = it }, label = { Text("Повторы") }, modifier = Modifier.testTag("edit-reps"))
            ActualContextEditor(context, equipmentChoices, exerciseChoices, !saving) { next ->
                val movementChanged = next.exerciseId != context.exerciseId
                if (movementChanged || next.equipmentId != context.equipmentId || next.loadBasis != context.loadBasis) {
                    weight = if (next.loadBasis == "bodyweight") "0" else ""; rir = null
                }
                if (movementChanged) reps = ""
                contextJson = StrictJson.encodeToString(next)
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
        confirmButton = { Column {
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            TextButton({ onSave(parsedWeight!!, parsedReps!!, rir, deviations.toList(), note,
            context.normalized()) },
            enabled = valid && !saving, modifier = Modifier.testTag("save-edit")) { Text("Сохранить") } } })
}

private val loadBases = listOf("machine_display", "total_external", "per_hand", "assistance", "bodyweight")

private val Double.g: String get() = (if (this % 1.0 == 0.0) toLong().toString() else toString()).replace('.', ',')
