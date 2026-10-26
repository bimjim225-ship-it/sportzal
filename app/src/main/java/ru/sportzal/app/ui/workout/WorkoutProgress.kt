package ru.sportzal.app.ui.workout

data class ExerciseProgress(val completedWork: Int, val plannedWork: Int,
    val completedWarmup: Int, val plannedWarmup: Int, val extra: Int, val skipped: Int,
    val completedPlanned: Int, val totalPlanned: Int, val remaining: Int) {
    val fullyPerformed get() = completedPlanned == totalPlanned
}

fun ExerciseUiState.progress(): ExerciseProgress {
    val planned = exercise.plannedSets.associateBy { it.setNo }
    val performed = saved.filter { it.plannedSetNo in planned }.mapNotNull { it.plannedSetNo }.toSet()
    val skippedSlots = skipped.filter { it.plannedSetNo in planned && it.plannedSetNo !in performed }
        .map { it.plannedSetNo }.toSet()
    return ExerciseProgress(
        performed.count { planned[it]?.setType == "work" }, planned.values.count { it.setType == "work" },
        performed.count { planned[it]?.setType == "warmup" }, planned.values.count { it.setType == "warmup" },
        saved.count { it.plannedSetNo == null }, skippedSlots.size, performed.size, planned.size,
        (planned.size - performed.size - skippedSlots.size).coerceAtLeast(0),
    )
}
