package ru.sportzal.app.ui.today

import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.ui.text.compactRirLabel

/** A summary never presents differing prescriptions as one repeated target. */
fun compactPrescriptionLines(exercise: ExerciseDocument): List<String> =
    exercise.plannedSets.groupBy { it.setType }.map { (type, sets) ->
        val name = if (type == "warmup") "Разминка" else "Рабочие"
        val targets = sets.map { it.copy(setNo = 1) }.distinct()
        if (targets.size != 1) "$name: ${sets.size} · Разные назначения"
        else {
            val set = targets.single()
            val weight = if (exercise.loadBasis == "bodyweight") "свой вес" else "${summaryWeight(set.targetWeightKg)} кг"
            "$name: ${sets.size} × $weight · ${set.repsMin}–${set.repsMax}" +
                (set.targetRir?.let { " · ${compactRirLabel(it)}" } ?: "") +
                " · Пауза ${set.restTargetSec / 60}:${(set.restTargetSec % 60).toString().padStart(2, '0')}"
        }
    }

private fun summaryWeight(value: Double) =
    (if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()).replace('.', ',')
