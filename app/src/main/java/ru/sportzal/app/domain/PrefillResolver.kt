package ru.sportzal.app.domain

import kotlinx.serialization.Serializable

/** The identity of what the athlete is actually using for a set. */
@Serializable data class ActualContext(
    val exerciseId: String,
    val equipmentId: String?,
    val setup: String?,
    val loadBasis: String,
    val side: String,
    val equipmentName: String? = null,
)

data class PlannedSlot(
    val plannedSetNo: Int,
    val setType: String,
    val targetWeightKg: Double,
    val repsMin: Int,
    val repsMax: Int,
    val targetRir: Int?,
    val restTargetSec: Int,
    val plannedContext: ActualContext,
)

data class SetDraft(
    val weightKg: Double?,
    val reps: Int?,
    val rir: Int? = null,
    val context: ActualContext,
    /** Distinguishes an untouched RIR from the explicit "not assessed" choice. */
    val rirAnswered: Boolean = rir != null,
)

data class PreviousSetFact(
    val plannedSetNo: Int,
    val weightKg: Double,
    val reps: Int,
    val rir: Int?,
    val context: ActualContext,
    val deviations: List<String> = emptyList(),
)

/** Implements the deliberately conservative workout prefill contract. */
object PrefillResolver {
    fun resolve(
        slot: PlannedSlot,
        previousPlannedSlot: PlannedSlot?,
        previousFact: PreviousSetFact?,
        draft: SetDraft?,
        actualContext: ActualContext,
    ): SetDraft {
        draft?.let { return it }

        val contextChangedFromPlan = actualContext.exerciseId != slot.plannedContext.exerciseId ||
            actualContext.equipmentId != slot.plannedContext.equipmentId ||
            actualContext.loadBasis != slot.plannedContext.loadBasis
        val defaultWeight = when {
            actualContext.loadBasis == "bodyweight" -> 0.0
            contextChangedFromPlan -> null
            else -> slot.targetWeightKg
        }
        val defaults = SetDraft(defaultWeight, slot.repsMin, null, actualContext)
        if (previousPlannedSlot == null || previousFact == null) return defaults

        val adjacent = previousPlannedSlot.plannedSetNo + 1 == slot.plannedSetNo &&
            previousFact.plannedSetNo == previousPlannedSlot.plannedSetNo
        val sameTarget = previousPlannedSlot.setType == slot.setType &&
            previousPlannedSlot.targetWeightKg == slot.targetWeightKg &&
            previousPlannedSlot.repsMin == slot.repsMin &&
            previousPlannedSlot.repsMax == slot.repsMax &&
            previousPlannedSlot.targetRir == slot.targetRir &&
            previousPlannedSlot.restTargetSec == slot.restTargetSec
        val sameContext = previousFact.context == actualContext &&
            previousPlannedSlot.plannedContext == slot.plannedContext
        return if (adjacent && sameTarget && sameContext) {
            // RIR, deviations and notes describe one fact and never flow into another slot.
            SetDraft(previousFact.weightKg, previousFact.reps, null, actualContext)
        } else defaults
    }
}
