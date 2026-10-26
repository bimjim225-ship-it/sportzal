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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import ru.sportzal.app.data.files.EquipmentPhotoStore
import ru.sportzal.app.ui.text.*
import ru.sportzal.app.ui.workout.progress
import ru.sportzal.app.ui.workout.actualContext
import ru.sportzal.app.ui.workout.hasReplacement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import ru.sportzal.app.ui.theme.NumericTextStyle
import ru.sportzal.app.ui.theme.SportzalColors
import androidx.compose.ui.res.painterResource
import ru.sportzal.app.R

@Composable
fun ExerciseCard(
    state: ExerciseUiState,
    elapsed: String,
    saving: Boolean,
    onDraft: (Double?, Int?, Int?, Boolean) -> Unit,
    onSave: (Double?, Int?, Int?, Boolean) -> Unit,
    onInteraction: (InteractionSource, Boolean) -> Unit,
    onEdit: (String, Double, Int, Int?, List<String>, String?, (Boolean) -> Unit) -> Unit,
    onDelete: (String, (Boolean) -> Unit) -> Unit,
    onSkip: (Int, String?, String?, (Boolean) -> Unit) -> Unit,
    onRestore: (Int) -> Unit,
    onContext: (ActualContext, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onExtra: (String, Double, Int, Int?, String?, (Boolean) -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    onEditActual: ((String, Double, Int, Int?, List<String>, String?, ActualContext, (Boolean) -> Unit) -> Unit)? = null,
    photoStore: EquipmentPhotoStore? = null,
    focused: Boolean = false,
    error: String? = null,
    reloadRequired: Boolean = false,
    onReload: () -> Unit = {},
    onSkipRemaining: (String?, String?, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onCancelExtra: ((Boolean) -> Unit) -> Unit = { it(true) },
) {
    val slot = state.currentSlot
    val draft = state.draft
    val actualContext = state.actualContext()
    val focusManager = LocalFocusManager.current
    val committedSequence = state.saved.maxOfOrNull { it.sequenceNo } ?: 0
    var observedSequence by remember(state.exercise.exerciseInstanceId) { mutableIntStateOf(committedSequence) }
    LaunchedEffect(committedSequence) {
        // A committed fact ends this input interaction. A failed write leaves focus and input intact.
        if (committedSequence > observedSequence) focusManager.clearFocus(force = true)
        observedSequence = committedSequence
    }
    var weightText by remember(state.exercise.exerciseInstanceId, slot?.plannedSetNo, draft?.context) {
        mutableStateOf(draft?.weightKg?.display().orEmpty())
    }
    var repsText by remember(state.exercise.exerciseInstanceId, slot?.plannedSetNo, draft?.context) {
        mutableStateOf(draft?.reps?.toString().orEmpty())
    }
    var visibleRir by remember(state.exercise.exerciseInstanceId, slot?.plannedSetNo, draft?.context) { mutableStateOf(draft?.rir) }
    var visibleRirAnswered by remember(state.exercise.exerciseInstanceId, slot?.plannedSetNo, draft?.context) {
        mutableStateOf(draft?.rirAnswered ?: false)
    }
    var skipDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var skipRemaining by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var submitted by remember(state.exercise.exerciseInstanceId, slot?.plannedSetNo) { mutableStateOf(false) }
    var secondaryMenu by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var contextDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var extraDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var resultsExpanded by androidx.compose.runtime.saveable.rememberSaveable(state.exercise.exerciseInstanceId) { mutableStateOf(!focused) }
    LaunchedEffect(reloadRequired) {
        if (reloadRequired) {
            skipDialog = false; contextDialog = false; extraDialog = false; secondaryMenu = false
            listOf(InteractionSource.SKIP_DIALOG, InteractionSource.CONTEXT_DIALOG, InteractionSource.EXTRA_SET_DIALOG)
                .forEach { onInteraction(it, false) }
        }
    }
    val recordedSets: @Composable () -> Unit = {
            if (state.saved.isNotEmpty()) TextButton({ resultsExpanded = !resultsExpanded }) { Text("Записанные подходы (${state.saved.size})") }
            if (resultsExpanded) state.saved.forEach { result -> SetResultRow(result, saving || reloadRequired, requiresRir(state, result.plannedSetNo), onInteraction,
                { weight, reps, rir, deviations, note, completed ->
                    onEdit(result.setResultId, weight, reps, rir, deviations, note, completed)
                },
                { completed -> onDelete(result.setResultId, completed) },
                { weight, reps, rir, deviations, note, context, completed ->
                    if (onEditActual != null) onEditActual(result.setResultId, weight, reps, rir, deviations, note, context, completed)
                    else onEdit(result.setResultId, weight, reps, rir, deviations, note, completed)
                }, equipmentChoices = state.equipmentChoices, exerciseChoices = state.exerciseChoices, reloadRequired = reloadRequired, error = error) }
            state.skipped.sortedBy { it.plannedSetNo }.forEach { skipped ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${skipped.plannedSetNo}. Пропущено" + (skipped.reason?.let { " · ${skipReasonLabel(it)}" } ?: ""),
                        modifier = Modifier.weight(1f))
                    TextButton({ onRestore(skipped.plannedSetNo) }, enabled = !saving && !reloadRequired,
                        modifier = Modifier.testTag("restore-${state.exercise.exerciseInstanceId}-${skipped.plannedSetNo}")) { Text("Вернуть подход") }
                }
            }
    }
    Card(Modifier.fillMaxWidth().onFocusChanged { onInteraction(InteractionSource.FOCUS, it.hasFocus) }.focusGroup()
        .testTag("exercise-${state.exercise.exerciseInstanceId}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (focused) "Рабочие: ${state.progress().completedWork}/${state.progress().plannedWork}"
                    else actualContext.title ?: state.exercise.title,
                    style = if (focused) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f))
                IconButton({ secondaryMenu = true }, enabled = !saving && !reloadRequired, modifier = Modifier
                    .semantics { contentDescription = "Меню упражнения" }
                    .testTag("secondary-${state.exercise.exerciseInstanceId}")) { androidx.compose.material3.Icon(painterResource(R.drawable.sz_ic_more), null) }
                DropdownMenu(secondaryMenu, onDismissRequest = { secondaryMenu = false }) {
                    DropdownMenuItem({ Text("Заменить упражнение / оборудование") }, { secondaryMenu = false; contextDialog = true; onInteraction(InteractionSource.CONTEXT_DIALOG, true) })
                    if (slot != null) DropdownMenuItem({ Text("Пропустить оставшиеся подходы") }, {
                        secondaryMenu = false; skipRemaining = true; skipDialog = true; onInteraction(InteractionSource.SKIP_DIALOG, true)
                    })
                    DropdownMenuItem({ Text("Дополнительный подход") }, { secondaryMenu = false; extraDialog = true; onInteraction(InteractionSource.EXTRA_SET_DIALOG, true) })
                }
            }
            Text(listOfNotNull(actualContext.equipmentName.takeUnless { focused },
                actualContext.setup, loadBasisLabel(actualContext.loadBasis),
                sideLabel(actualContext.side).takeUnless { focused && actualContext.side == "bilateral" }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium)
            val progress = state.progress()
            if (!focused) Text("Рабочие: ${progress.completedWork}/${progress.plannedWork}", style = MaterialTheme.typography.titleMedium)
            if (progress.plannedWarmup > 0) Text("Разминка: ${progress.completedWarmup}/${progress.plannedWarmup}", style = MaterialTheme.typography.bodySmall)
            if (progress.extra > 0) Text("Дополнительные: ${progress.extra}", style = MaterialTheme.typography.bodySmall)
            if (!focused) recordedSets()
            if (slot != null && draft != null) {
                Text("Подход ${slot.plannedSetNo} · ${setTypeLabel(slot.setType)}")
                Text("${if (state.hasReplacement()) "Исходный план" else "Цель"}: ${slot.targetWeightKg.display()} кг · ${slot.repsMin}–${slot.repsMax}" + (slot.targetRir?.let { " · ${compactRirLabel(it)}" } ?: ""))
                val rest = state.restReferenceSec ?: slot.restTargetSec
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Пауза ${rest / 60}:${(rest % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(if (state.saved.isEmpty()) "Ещё не записывали" else "С записи: $elapsed",
                        style = MaterialTheme.typography.titleSmall.copy(color = SportzalColors.Timer),
                        modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = "Время с записи подхода" }
                            .testTag("elapsed-${state.exercise.exerciseInstanceId}"))
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = maxWidth < 240.dp || LocalDensity.current.fontScale > 1.3f
                    val fields = if (stacked) Modifier.fillMaxWidth() else Modifier
                    if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericField(weightText, "Вес", NumericKind.WEIGHT, {
                            weightText = it; onDraft(parseNumeric(it, NumericKind.WEIGHT)?.toDouble(), parseNumeric(repsText, NumericKind.REPS)?.toInt(), visibleRir, visibleRirAnswered)
                        }, fields.testTag("weight-${state.exercise.exerciseInstanceId}"), enabled = !saving && !reloadRequired && draft.context.loadBasis != "bodyweight",
                            error = if (submitted && parseNumeric(weightText, NumericKind.WEIGHT) == null) "Введите вес" else null)
                        NumericField(repsText, "Повторы", NumericKind.REPS, {
                            repsText = it; onDraft(parseNumeric(weightText, NumericKind.WEIGHT)?.toDouble(), parseNumeric(it, NumericKind.REPS)?.toInt(), visibleRir, visibleRirAnswered)
                        }, fields.testTag("reps-${state.exercise.exerciseInstanceId}"), enabled = !saving && !reloadRequired,
                            error = if (submitted && parseNumeric(repsText, NumericKind.REPS) == null) "Введите повторы" else null)
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericField(weightText, "Вес", NumericKind.WEIGHT, {
                        weightText = it; onDraft(parseNumeric(it, NumericKind.WEIGHT)?.toDouble(), parseNumeric(repsText, NumericKind.REPS)?.toInt(), visibleRir, visibleRirAnswered)
                    }, Modifier.weight(1f).testTag("weight-${state.exercise.exerciseInstanceId}"), enabled = !saving && !reloadRequired && draft.context.loadBasis != "bodyweight",
                            error = if (submitted && parseNumeric(weightText, NumericKind.WEIGHT) == null) "Введите вес" else null)
                    NumericField(repsText, "Повторы", NumericKind.REPS, {
                        repsText = it; onDraft(parseNumeric(weightText, NumericKind.WEIGHT)?.toDouble(), parseNumeric(it, NumericKind.REPS)?.toInt(), visibleRir, visibleRirAnswered)
                    }, Modifier.weight(1f).testTag("reps-${state.exercise.exerciseInstanceId}"), enabled = !saving && !reloadRequired,
                        error = if (submitted && parseNumeric(repsText, NumericKind.REPS) == null) "Введите повторы" else null)
                    }
                }
                if (requiresRir(state)) {
                    RirSelector(visibleRir, visibleRirAnswered, enabled = !saving && !reloadRequired) {
                        visibleRir = it; visibleRirAnswered = true
                        onDraft(parseNumeric(weightText, NumericKind.WEIGHT)?.toDouble(), parseNumeric(repsText, NumericKind.REPS)?.toInt(), it, true)
                    }
                }
                if (submitted && requiresRir(state) && !visibleRirAnswered)
                    Text("Выберите запас повторов или «Не оценил»", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("save-error"))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("save-error")) }
                if (reloadRequired) OutlinedButton(onReload, enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag("reload-workout")) { Text("Обновить сохранённые данные") }
                Button(onClick = {
                    submitted = true
                    val weight = parseNumeric(weightText, NumericKind.WEIGHT)?.toDouble()
                    val reps = parseNumeric(repsText, NumericKind.REPS)?.toInt()
                    if (weight != null && reps != null && (!requiresRir(state) || visibleRirAnswered))
                        onSave(weight, reps, visibleRir, visibleRirAnswered)
                },
                    enabled = !saving && !reloadRequired, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("save-${state.exercise.exerciseInstanceId}")) {
                    Text("Записать подход")
                }
                TextButton({ skipRemaining = false; skipDialog = true; onInteraction(InteractionSource.SKIP_DIALOG, true) }, enabled = !saving && !reloadRequired,
                    modifier = Modifier.testTag("skip-${state.exercise.exerciseInstanceId}")) { Text("Пропустить подход") }
            } else {
                Text(if (state.skipped.isEmpty()) "Все подходы записаны" else "Подходы закрыты · есть пропуски")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (reloadRequired) OutlinedButton(onReload) { Text("Обновить сохранённые данные") }
            }
            if (focused) recordedSets()


        }
    }
    if (skipDialog && slot != null) SkipSetDialog(saving, error, if (skipRemaining) "Пропустить оставшиеся подходы?" else "Пропустить подход", {
        skipDialog = false; onInteraction(InteractionSource.SKIP_DIALOG, false)
    }) { reason, note ->
        val done: (Boolean) -> Unit = { committed -> if (committed) {
            skipDialog = false; onInteraction(InteractionSource.SKIP_DIALOG, false)
        } }
        if (skipRemaining) onSkipRemaining(reason, note, done) else onSkip(slot.plannedSetNo, reason, note, done)
    }
    if (contextDialog && draft != null) ActualContextDialog(draft.context, state.equipmentChoices, state.exerciseChoices, saving, error, {
        contextDialog = false; onInteraction(InteractionSource.CONTEXT_DIALOG, false)
    }) { context -> onContext(context) { committed -> if (committed) {
        contextDialog = false; onInteraction(InteractionSource.CONTEXT_DIALOG, false)
    } } }
    if (extraDialog) ExtraSetDialog(actualContext, saving, error, {
        onCancelExtra { if (it) { extraDialog = false; onInteraction(InteractionSource.EXTRA_SET_DIALOG, false) } }
    }) { type, weight, reps, rir, note -> onExtra(type, weight, reps, rir, note) { committed -> if (committed) {
        extraDialog = false; onInteraction(InteractionSource.EXTRA_SET_DIALOG, false)
    } } }
}

@Composable private fun ActualContextDialog(current: ActualContext, equipment: List<ru.sportzal.app.model.EquipmentDocument>,
    exercises: List<ru.sportzal.app.model.ExerciseDocument>, saving: Boolean, error: String?, onDismiss: () -> Unit, onSave: (ActualContext) -> Unit) {
    var contextJson by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ru.sportzal.app.model.StrictJson.encodeToString(current)) }
    val context = ru.sportzal.app.model.StrictJson.decodeFromString<ActualContext>(contextJson)
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Замена и настройка") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Замена действует на следующие подходы. Уже записанные результаты сохранятся.")
            ActualContextEditor(context, equipment, exercises, !saving) { contextJson = ru.sportzal.app.model.StrictJson.encodeToString(it) }
        }
    }, dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Отмена") } }, confirmButton = { Column {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton({ onSave(context.normalized()) }, enabled = !saving && context.validForSave(),
            modifier = Modifier.testTag("save-context")) { Text("Сохранить") }
    } })
}

@Composable private fun ExtraSetDialog(context: ActualContext, saving: Boolean, error: String?, onDismiss: () -> Unit,
    onSave: (String, Double, Int, Int?, String?) -> Unit) {
    var weight by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(if (context.loadBasis == "bodyweight") "0" else "") }
    var reps by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }; var rir by remember { mutableStateOf<Int?>(null) }
    var rirAnswered by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var type by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("work") }; var note by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val w = weight.replace(',', '.').toDoubleOrNull(); val r = reps.toIntOrNull()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Дополнительный подход") }, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(listOfNotNull(context.equipmentName, context.setup).joinToString(" · "))
            OutlinedTextField(weight, { weight = it }, label = { Text("Вес") }, enabled = context.loadBasis != "bodyweight", modifier = Modifier.testTag("extra-weight"))
            OutlinedTextField(reps, { reps = it }, label = { Text("Повторы") }, modifier = Modifier.testTag("extra-reps"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("work", "warmup").forEach { value -> FilterChip(type == value, { type = value }, { Text(setTypeLabel(value)) }) }
            }
            RirSelector(rir, rirAnswered, enabled = !saving) { rir = it; rirAnswered = true }
            OutlinedTextField(note, { note = it }, label = { Text("Комментарий") })
        }
    }, dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Отмена") } }, confirmButton = { Column {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton({ onSave(type, w!!, r!!, rir, note.trim().ifEmpty { null }) }, enabled = !saving && w != null && w.isFinite() && w >= 0 && r != null && r >= 0,
            modifier = Modifier.testTag("save-extra")) { Text("Записать") }
    } })
}

private fun requiresRir(state: ExerciseUiState, setNo: Int? = state.currentSlot?.plannedSetNo): Boolean = when (state.exercise.rirCapture) {
    "all_work_sets" -> state.exercise.plannedSets.firstOrNull { it.setNo == setNo }?.setType == "work"
    "last_work_set" -> setNo == state.exercise.plannedSets.lastOrNull { it.setType == "work" }?.setNo
    else -> false
}
private val skipReasons = listOf<String?>(null, "equipment_busy", "time_limit", "fatigue", "discomfort", "other")

@Composable private fun SkipSetDialog(saving: Boolean, error: String?, title: String, onDismiss: () -> Unit, onSkip: (String?, String?) -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    var note by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight - 32.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        skipReasons.forEach { value ->
                            FilterChip(
                                selected = reason == value,
                                onClick = { reason = value },
                                label = { Text(skipReasonLabel(value)) },
                                modifier = Modifier.testTag("skip-reason-${value ?: "none"}"),
                            )
                        }
                        OutlinedTextField(note, { note = it }, label = { Text("Комментарий") })
                    }
                    Spacer(Modifier.height(8.dp))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
private fun Double.display() = (if (this % 1.0 == 0.0) toLong().toString() else toString()).replace('.', ',')
internal fun Int.rirText() = if (this == 4) "4+" else toString()
