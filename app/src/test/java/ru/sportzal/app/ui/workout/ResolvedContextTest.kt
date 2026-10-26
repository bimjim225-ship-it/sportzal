package ru.sportzal.app.ui.workout

import org.junit.Assert.*
import org.junit.Test
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.domain.*
import ru.sportzal.app.model.*

class ResolvedContextTest {
    private val exercise = ExerciseDocument("instance", "row", "Тяга", "planned", "Плановая настройка", "machine_display", "bilateral", 1, "none", emptyList())
    private val actual = SetResultEntity("fact", "w", 1, "instance", 1, "work", "actual-movement", "Другое движение",
        "actual-machine", "Другой тренажёр", null, "per_hand", "left", 10.0, 10, null,
        "2026-10-26T10:00:00Z", null, null, null, "[]", null)

    @Test fun afterLastSlotMediaAndExtraSetKeepActualEquipmentAndExplicitlyClearedSetup() {
        val context = ExerciseUiState(exercise, null, null, listOf(actual)).actualContext()
        assertEquals("actual-machine", context.equipmentId)
        assertEquals("actual-movement", context.exerciseId)
        assertEquals("per_hand", context.loadBasis)
        assertNull(context.setup)
    }

    @Test fun currentDraftTakesPrecedenceOverPreviousFactAndPlan() {
        val draft = SetDraft(10.0, 8, null, ActualContext("draft-movement", null, null, "bodyweight", "bilateral"))
        val context = ExerciseUiState(exercise, null, draft, listOf(actual)).actualContext()
        assertEquals("draft-movement", context.exerciseId)
        assertNull(context.equipmentId)
        assertNull(context.setup)
    }
}
