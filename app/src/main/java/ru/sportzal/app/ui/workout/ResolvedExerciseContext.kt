package ru.sportzal.app.ui.workout

import ru.sportzal.app.domain.ActualContext

/** Resolve the context object first: a null actual field is an intentional value, not a fallback request. */
fun ExerciseUiState.actualContext(): ActualContext {
    draft?.let { return it.context }
    saved.maxByOrNull { it.sequenceNo }?.let { fact ->
        return ActualContext(fact.exerciseIdActual, fact.equipmentIdActual, fact.setupActual,
            fact.loadBasisActual, fact.sideActual, fact.equipmentNameActual,
            fact.titleActual.takeIf { fact.exerciseIdActual != exercise.exerciseId || it != exercise.title })
    }
    val equipment = equipmentChoices.firstOrNull { it.equipmentId == exercise.equipmentId }
    return ActualContext(exercise.exerciseId, exercise.equipmentId, exercise.setupHint ?: equipment?.setupHint,
        exercise.loadBasis, exercise.side, equipment?.name)
}
