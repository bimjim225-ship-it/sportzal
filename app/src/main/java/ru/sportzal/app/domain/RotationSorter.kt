package ru.sportzal.app.domain

import ru.sportzal.app.model.ClockAnchor
import ru.sportzal.app.model.elapsedBetween

data class ClockReading(val wall: java.time.Instant, val bootId: String?, val elapsedRealtimeMs: Long?) {
    fun anchor() = ClockAnchor(wall, bootId, elapsedRealtimeMs)
}

data class ExerciseRuntimeCard(
    val exerciseInstanceId: String,
    val plannedOrder: Int,
    val latestSavedSet: ClockAnchor? = null,
    val completed: Boolean = false,
)

fun sortRotation(cards: List<ExerciseRuntimeCard>, now: ClockReading): List<ExerciseRuntimeCard> =
    cards.sortedWith(compareBy<ExerciseRuntimeCard> {
        when {
            it.completed -> 2
            it.latestSavedSet == null -> 0
            else -> 1
        }
    }.thenComparator { left, right ->
        if (!left.completed && left.latestSavedSet != null && right.latestSavedSet != null) {
            val l = elapsedBetween(left.latestSavedSet, now.anchor()).duration
            val r = elapsedBetween(right.latestSavedSet, now.anchor()).duration
            r.compareTo(l)
        } else 0
    }.thenBy { it.plannedOrder })

/** Keeps list identity stable while a field/menu/touch interaction owns a card. */
class RotationCoordinator(initial: List<ExerciseRuntimeCard>) {
    var cards: List<ExerciseRuntimeCard> = initial
        private set
    private var interacting = false
    private var deferred: Pair<List<ExerciseRuntimeCard>, ClockReading>? = null

    fun beginInteraction() { interacting = true }
    fun update(source: List<ExerciseRuntimeCard>, now: ClockReading, committed: Boolean = false) {
        if (interacting) deferred = source to now else if (committed) cards = sortRotation(source, now)
        // Timer-only refreshes intentionally do not reorder an already visible list.
    }
    fun endInteraction() {
        interacting = false
        deferred?.let { cards = sortRotation(it.first, it.second) }
        deferred = null
    }
}
