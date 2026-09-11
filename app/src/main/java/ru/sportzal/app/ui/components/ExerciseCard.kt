package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.sportzal.app.ui.workout.ExerciseUiState
import ru.sportzal.app.ui.workout.InteractionSource
import ru.sportzal.app.domain.ActualContext
import java.util.UUID
import ru.sportzal.app.data.files.EquipmentPhotoStore

@Composable
fun ExerciseCard(
    state: ExerciseUiState,
    elapsed: String,
    saving: Boolean,
    onDraft: (Double?, Int?, Int?, Boolean) -> Unit,
    onSave: () -> Unit,
    onInteraction: (InteractionSource, Boolean) -> Unit,
    onEdit: (String, Double, Int, Int?, List<String>, String?, (Boolean) -> Unit) -> Unit,
    onDelete: (String, (Boolean) -> Unit) -> Unit,
    onSkip: (Int, String?, String?, (Boolean) -> Unit) -> Unit,
    onRestore: (Int) -> Unit,
    onContext: (ActualContext, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onExtra: (String, Double, Int, Int?, String?, (Boolean) -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    onEditActual: ((String, Double, Int, Int?, List<String>, String?, ActualContext, (Boolean) -> Unit) -> Unit)? = null,
    photoStore: EquipmentPhotoStore? = null,
) {
    val slot = state.currentSlot
    val draft = state.draft
    var weightText by remember(state.exercise.exerciseInstanceId, slot?.plannedSetNo, draft?.weightKg) {
        mutableStateOf(draft?.weightKg?.display().orEmpty())
    }
    var repsText by remember(state.exercise.exerciseInstanceId, slot?.plannedSetNo, draft?.reps) {
        mutableStateOf(draft?.reps?.toString().orEmpty())
    }
    var skipDialog by remember { mutableStateOf(false) }
    var secondaryMenu by remember { mutableStateOf(false) }
    var contextDialog by remember { mutableStateOf(false) }
    var extraDialog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().onFocusChanged { onInteraction(InteractionSource.FOCUS, it.hasFocus) }.focusGroup()
        .testTag("exercise-${state.exercise.exerciseInstanceId}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (photoStore != null) LocalEquipmentPhoto(state.equipmentPhotoPath, photoStore, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.exercise.title, style = MaterialTheme.typography.headlineSmall)
                IconButton({ secondaryMenu = true }, enabled = !saving, modifier = Modifier
                    .semantics { contentDescription = "Меню упражнения" }
                    .testTag("secondary-${state.exercise.exerciseInstanceId}")) { Text("⋮") }
                DropdownMenu(secondaryMenu, onDismissRequest = { secondaryMenu = false }) {
                    DropdownMenuItem({ Text("Изменить оборудование / настройку") }, { secondaryMenu = false; contextDialog = true; onInteraction(InteractionSource.CONTEXT_DIALOG, true) })
                    DropdownMenuItem({ Text("Дополнительный подход") }, { secondaryMenu = false; extraDialog = true; onInteraction(InteractionSource.EXTRA_SET_DIALOG, true) })
                }
            }
            Text(listOfNotNull(draft?.context?.equipmentName ?: draft?.context?.equipmentId,
                draft?.context?.setup, draft?.context?.loadBasis, draft?.context?.side).joinToString(" · "))
            Text("Подходы: ${state.saved.size}/${state.exercise.plannedSets.size}")
            state.saved.forEach { result -> SetResultRow(result, saving, requiresRir(state, result.plannedSetNo), onInteraction,
                { weight, reps, rir, deviations, note, completed ->
                    onEdit(result.setResultId, weight, reps, rir, deviations, note, completed)
                },
                { completed -> onDelete(result.setResultId, completed) },
                { weight, reps, rir, deviations, note, context, completed ->
                    if (onEditActual != null) onEditActual(result.setResultId, weight, reps, rir, deviations, note, context, completed)
                    else onEdit(result.setResultId, weight, reps, rir, deviations, note, completed)
                }) }
            state.skipped.sortedBy { it.plannedSetNo }.forEach { skipped ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${skipped.plannedSetNo}. Пропущено" + (skipped.reason?.let { " · ${skipReasonLabels[it] ?: it}" } ?: ""),
                        modifier = Modifier.weight(1f))
                    TextButton({ onRestore(skipped.plannedSetNo) }, enabled = !saving,
                        modifier = Modifier.testTag("restore-${state.exercise.exerciseInstanceId}-${skipped.plannedSetNo}")) { Text("Вернуть подход") }
                }
            }
            if (slot != null && draft != null) {
                Text("Подход ${slot.plannedSetNo} · ${slot.setType}")
                Text("Цель: ${slot.targetWeightKg.display()} кг · ${slot.repsMin}–${slot.repsMax}" + (slot.targetRir?.let { " · RIR ${it.rirText()}" } ?: ""))
                val rest = state.restReferenceSec ?: slot.restTargetSec
                Text("Ориентир до следующего подхода: ${rest / 60}:${(rest % 60).toString().padStart(2, '0')}")
                Text(if (state.saved.isEmpty()) "Ещё не записывали" else "С записи: $elapsed",
                    // Exclude the changing value from accessibility events to avoid an announcement every tick.
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Таймер отдыха" }
                        .testTag("elapsed-${state.exercise.exerciseInstanceId}"))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val fields = if (maxWidth < 360.dp) Modifier.fillMaxWidth() else Modifier
                    if (maxWidth < 360.dp) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericField(weightText, "Вес", NumericKind.WEIGHT, {
                            weightText = it; onDraft(parseNumeric(it, NumericKind.WEIGHT)?.toDouble(), parseNumeric(repsText, NumericKind.REPS)?.toInt(), draft.rir, draft.rirAnswered)
                        }, fields.testTag("weight-${state.exercise.exerciseInstanceId}"), enabled = !saving && draft.context.loadBasis != "bodyweight")
                        NumericField(repsText, "Повторы", NumericKind.REPS, {
                            repsText = it; onDraft(parseNumeric(weightText, NumericKind.WEIGHT)?.toDouble(), parseNumeric(it, NumericKind.REPS)?.toInt(), draft.rir, draft.rirAnswered)
                        }, fields.testTag("reps-${state.exercise.exerciseInstanceId}"), enabled = !saving)
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericField(weightText, "Вес", NumericKind.WEIGHT, {
                        weightText = it; onDraft(parseNumeric(it, NumericKind.WEIGHT)?.toDouble(), parseNumeric(repsText, NumericKind.REPS)?.toInt(), draft.rir, draft.rirAnswered)
                    }, Modifier.testTag("weight-${state.exercise.exerciseInstanceId}"), enabled = !saving && draft.context.loadBasis != "bodyweight")
                    NumericField(repsText, "Повторы", NumericKind.REPS, {
                        repsText = it; onDraft(parseNumeric(weightText, NumericKind.WEIGHT)?.toDouble(), parseNumeric(it, NumericKind.REPS)?.toInt(), draft.rir, draft.rirAnswered)
                    }, Modifier.testTag("reps-${state.exercise.exerciseInstanceId}"), enabled = !saving)
                    }
                }
                if (requiresRir(state)) RirSelector(draft.rir, draft.rirAnswered, enabled = !saving) {
                    onDraft(parseNumeric(weightText, NumericKind.WEIGHT)?.toDouble(), parseNumeric(repsText, NumericKind.REPS)?.toInt(), it, true)
                }
                Button(onClick = onSave, enabled = !saving, modifier = Modifier.testTag("save-${state.exercise.exerciseInstanceId}")) {
                    Text("Записать подход")
                }
                TextButton({ skipDialog = true; onInteraction(InteractionSource.SKIP_DIALOG, true) }, enabled = !saving,
                    modifier = Modifier.testTag("skip-${state.exercise.exerciseInstanceId}")) { Text("Пропустить подход") }
            } else Text("Все подходы записаны")
        }
    }
    if (skipDialog && slot != null) SkipSetDialog(saving, {
        skipDialog = false; onInteraction(InteractionSource.SKIP_DIALOG, false)
    }) { reason, note ->
        onSkip(slot.plannedSetNo, reason, note) { committed ->
            if (committed) {
                skipDialog = false
                onInteraction(InteractionSource.SKIP_DIALOG, false)
            }
        }
    }
    if (contextDialog && draft != null) ActualContextDialog(draft.context, state.equipmentChoices, saving, {
        contextDialog = false; onInteraction(InteractionSource.CONTEXT_DIALOG, false)
    }) { context -> onContext(context) { committed -> if (committed) {
        contextDialog = false; onInteraction(InteractionSource.CONTEXT_DIALOG, false)
    } } }
    if (extraDialog && draft != null) ExtraSetDialog(draft.context, saving, {
        extraDialog = false; onInteraction(InteractionSource.EXTRA_SET_DIALOG, false)
    }) { type, weight, reps, rir, note -> onExtra(type, weight, reps, rir, note) { committed -> if (committed) {
        extraDialog = false; onInteraction(InteractionSource.EXTRA_SET_DIALOG, false)
    } } }
}

private val loadBases = listOf("machine_display", "total_external", "per_hand", "assistance", "bodyweight")

@Composable private fun ActualContextDialog(current: ActualContext, equipment: List<ru.sportzal.app.model.EquipmentDocument>,
    saving: Boolean, onDismiss: () -> Unit, onSave: (ActualContext) -> Unit) {
    var selectedId by remember { mutableStateOf(current.equipmentId) }
    var manual by remember { mutableStateOf(current.equipmentId?.startsWith("local:") == true) }
    var manualId by remember { mutableStateOf(current.equipmentId?.takeIf { it.startsWith("local:") } ?: "local:${UUID.randomUUID()}") }
    var name by remember { mutableStateOf(current.equipmentName.orEmpty()) }
    var setup by remember { mutableStateOf(current.setup.orEmpty()) }
    var basis by remember { mutableStateOf(current.loadBasis) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Оборудование и настройка") }, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            equipment.forEach { item -> FilterChip(!manual && selectedId == item.equipmentId, {
                manual = false; selectedId = item.equipmentId; name = item.name; setup = item.setupHint.orEmpty()
            }, { Text(item.name) }, modifier = Modifier.testTag("equipment-${item.equipmentId}")) }
            FilterChip(manual, { manual = true; selectedId = manualId }, { Text("Другое оборудование") })
            if (manual) OutlinedTextField(name, { name = it }, label = { Text("Название") }, modifier = Modifier.testTag("manual-equipment-name"))
            OutlinedTextField(setup, { setup = it }, label = { Text("Настройка / setup") }, modifier = Modifier.testTag("actual-setup"))
            if (manual) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                loadBases.forEach { value -> FilterChip(basis == value, { basis = value }, { Text(value) }) }
            }
        }
    }, dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Отмена") } }, confirmButton = {
        TextButton({ onSave(current.copy(equipmentId = if (manual) manualId else selectedId,
            equipmentName = name.trim().ifEmpty { null }, setup = setup.trim().ifEmpty { null }, loadBasis = basis)) },
            enabled = !saving && (!manual || name.isNotBlank()), modifier = Modifier.testTag("save-context")) { Text("Сохранить") }
    })
}

@Composable private fun ExtraSetDialog(context: ActualContext, saving: Boolean, onDismiss: () -> Unit,
    onSave: (String, Double, Int, Int?, String?) -> Unit) {
    var weight by remember { mutableStateOf(if (context.loadBasis == "bodyweight") "0" else "") }
    var reps by remember { mutableStateOf("") }; var rir by remember { mutableStateOf<Int?>(null) }
    var type by remember { mutableStateOf("work") }; var note by remember { mutableStateOf("") }
    val w = weight.replace(',', '.').toDoubleOrNull(); val r = reps.toIntOrNull()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Дополнительный подход") }, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(listOfNotNull(context.equipmentName ?: context.equipmentId, context.setup).joinToString(" · "))
            OutlinedTextField(weight, { weight = it }, label = { Text("Вес") }, enabled = context.loadBasis != "bodyweight", modifier = Modifier.testTag("extra-weight"))
            OutlinedTextField(reps, { reps = it }, label = { Text("Повторы") }, modifier = Modifier.testTag("extra-reps"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("work", "warmup").forEach { value -> FilterChip(type == value, { type = value }, { Text(value) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..4).forEach { value -> FilterChip(rir == value, { rir = value }, { Text(if (value == 4) "4+" else "$value") }) }
                FilterChip(rir == null, { rir = null }, { Text("Не оценил") }) }
            OutlinedTextField(note, { note = it }, label = { Text("Комментарий") })
        }
    }, dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Отмена") } }, confirmButton = {
        TextButton({ onSave(type, w!!, r!!, rir, note.trim().ifEmpty { null }) }, enabled = !saving && w != null && w >= 0 && r != null && r >= 0,
            modifier = Modifier.testTag("save-extra")) { Text("Записать") }
    })
}

private fun requiresRir(state: ExerciseUiState, setNo: Int? = state.currentSlot?.plannedSetNo): Boolean = when (state.exercise.rirCapture) {
    "all_work_sets" -> state.exercise.plannedSets.firstOrNull { it.setNo == setNo }?.setType == "work"
    "last_work_set" -> setNo == state.exercise.plannedSets.lastOrNull { it.setType == "work" }?.setNo
    else -> false
}
private val skipReasonLabels = linkedMapOf<String?, String>(null to "Без причины", "equipment_busy" to "Оборудование занято",
    "time_limit" to "Не хватает времени", "fatigue" to "Усталость", "discomfort" to "Дискомфорт", "other" to "Другое")

@Composable private fun SkipSetDialog(saving: Boolean, onDismiss: () -> Unit, onSkip: (String?, String?) -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight - 32.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("Пропустить подход", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        skipReasonLabels.forEach { (value, label) ->
                            FilterChip(
                                selected = reason == value,
                                onClick = { reason = value },
                                label = { Text(label) },
                                modifier = Modifier.testTag("skip-reason-${value ?: "none"}"),
                            )
                        }
                        OutlinedTextField(note, { note = it }, label = { Text("Комментарий") })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onDismiss, enabled = !saving) { Text("Отмена") }
                        TextButton(
                            onClick = { onSkip(reason, note) },
                            enabled = !saving,
                            modifier = Modifier.testTag("confirm-skip"),
                        ) { Text("Пропустить") }
                    }
                }
            }
        }
    }
}
private fun Double.display() = if (this % 1.0 == 0.0) toInt().toString() else toString()
internal fun Int.rirText() = if (this == 4) "4+" else toString()
