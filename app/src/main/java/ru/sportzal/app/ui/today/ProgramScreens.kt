package ru.sportzal.app.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.sportzal.app.data.files.ImportPreview
import ru.sportzal.app.model.*
import ru.sportzal.app.ui.components.*
import ru.sportzal.app.ui.media.*
import ru.sportzal.app.ui.text.*

@Composable
fun PlannedWorkoutScreen(plan: PlannedWorkoutDocument, equipment: List<EquipmentDocument>, busy: Boolean,
    onBack: () -> Unit, onStart: () -> Unit, error: String? = null) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { AppHeader(plan.title, formatPlanDate(plan.plannedDate), onBack) }
        planItems(plan, equipment)
        error?.let { item { StatusNotice(it, error = true) } }
        item { SportzalButton("Начать тренировку", onStart, busy = busy) }
    }
}

@Composable
fun ProgramImportScreen(preview: ImportPreview?, reading: Boolean, busy: Boolean, message: String?,
    onBack: () -> Unit, onChoose: () -> Unit, onAccept: (ImportPreview.Valid) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppHeader("Новый план", onBack = onBack) }
        if (reading) item { LoadingState("Читаем файл плана") }
        when (preview) {
            is ImportPreview.Valid -> {
                item { Text("План готов к принятию", style = MaterialTheme.typography.headlineSmall)
                    Text("Версия ${preview.program.programVersion} · Тренировок: ${preview.program.workouts.size}") }
                preview.program.coachNotes?.let { item { StatusNotice("Заметка ИИ", it) } }
                preview.program.workouts.forEach { plan ->
                    item { Text(plan.title, style = MaterialTheme.typography.titleLarge); Text(formatPlanDate(plan.plannedDate), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    planItems(plan, preview.program.equipmentUpserts)
                }
                message?.let { item { StatusNotice(it, error = true) } }
                item { StatusNotice("Файл прочитан", "Новый план применяется к будущим занятиям. Активная тренировка и история сохраняются.") }
                item { SportzalButton("Принять план", { onAccept(preview) }, busy = busy) }
            }
            is ImportPreview.Error -> item { StatusNotice("План не принят", preview.message, error = true) }
            null -> Unit
        }
        if (!reading) item { SportzalButton("Выбрать другой файл", onChoose, enabled = !busy, secondary = true) }
    }
}

private fun LazyListScope.planItems(plan: PlannedWorkoutDocument, equipment: List<EquipmentDocument>) {
    plan.notes?.let { note -> item { StatusNotice("Из плана ИИ", note) } }
    plan.blocks.forEach { block ->
        item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(block.title, style = MaterialTheme.typography.titleMedium)
            Text(if (block.mode == "rotation") "Чередование упражнений" else "Подходы по порядку", style = MaterialTheme.typography.bodySmall)
            block.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        } }
        block.exercises.forEach { exercise -> item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EquipmentIllustration(LocalVisualBindings.current.equipment(exercise.equipmentId), Modifier.size(76.dp), thumbnail = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(exercise.title, style = MaterialTheme.typography.titleMedium)
                        plannedSetLines(exercise).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                (exercise.setupHint ?: equipment.firstOrNull { it.equipmentId == exercise.equipmentId }?.setupHint)?.let { Text(it) }
                Text(loadBasisLabel(exercise.loadBasis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                exercise.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            } }
        } }
    }
}

/** Preserve changing prescriptions; never collapse different weights into a misleading n× target. */
fun plannedSetLines(exercise: ExerciseDocument): List<String> = exercise.plannedSets.map { set ->
    val weight = if (exercise.loadBasis == "bodyweight") "свой вес" else "${plainWeight(set.targetWeightKg)} кг"
    "${setTypeLabel(set.setType)} ${set.setNo}: $weight · ${set.repsMin}–${set.repsMax}" +
        (set.targetRir?.let { " · ${compactRirLabel(it)}" } ?: "") +
        " · Пауза ${set.restTargetSec / 60}:${(set.restTargetSec % 60).toString().padStart(2, '0')}"
}

private fun plainWeight(value: Double) = (if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()).replace('.', ',')
