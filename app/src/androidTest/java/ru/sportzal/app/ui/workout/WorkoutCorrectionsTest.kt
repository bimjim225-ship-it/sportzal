package ru.sportzal.app.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.data.db.SkippedSetEntity
import ru.sportzal.app.domain.ActualContext
import ru.sportzal.app.domain.ClockReading
import ru.sportzal.app.domain.PlannedSlot
import ru.sportzal.app.domain.SetDraft
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.model.PlannedSetDocument
import ru.sportzal.app.ui.theme.SportzalTheme
import java.time.Instant

/** Exercises the real dialogs and their commit-aware ownership contract. */
class WorkoutCorrectionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editClosesOnlyAfterCommitAndDisplaysCommittedValues() {
        var ui by mutableStateOf(workout(saved = listOf(result())))
        val interactions = mutableListOf<Boolean>()
        compose.setContent { content(ui, { ui = it }, interactions) }

        compose.onNodeWithTag("set-actions-set-1").performClick()
        compose.onNodeWithText("Изменить").performClick()
        compose.onNodeWithTag("edit-reps").performTextClearance()
        compose.onNodeWithTag("edit-reps").performTextInput("8")
        compose.onNodeWithText("3").performClick()
        compose.onNodeWithTag("deviation-technique_changed").performClick()
        compose.onNodeWithTag("save-edit").performClick()

        compose.onNodeWithTag("save-edit").assertDoesNotExist()
        compose.onNodeWithText("1. 100 кг × 8 · RIR 3").assertIsDisplayed()
        assertEquals(listOf(true, false), interactions)
    }

    @Test fun failedEditKeepsDialogAndEnteredValuesForRetry() {
        var ui by mutableStateOf(workout(saved = listOf(result())))
        val interactions = mutableListOf<Boolean>()
        compose.setContent { SportzalTheme { WorkoutScreen(ui, { "0:00" }, { _, _, _, _, _ -> }, { _, _ -> },
            { _, active -> interactions += active }, {}, onEdit = { _, _, _, _, _, _, completed -> completed(false) }) } }
        compose.onNodeWithTag("set-actions-set-1").performClick()
        compose.onNodeWithText("Изменить").performClick()
        compose.onNodeWithTag("edit-reps").performTextClearance()
        compose.onNodeWithTag("edit-reps").performTextInput("9")
        compose.onNodeWithTag("save-edit").performClick()
        compose.onNodeWithTag("save-edit").assertIsDisplayed()
        compose.onNodeWithTag("edit-reps").assertIsDisplayed()
        assertEquals(listOf(true), interactions)
    }

    @Test fun deleteCommitClosesDialogReleasesInteractionAndReturnsSlot() {
        var ui by mutableStateOf(workout(saved = listOf(result())))
        val interactions = mutableListOf<Boolean>()
        compose.setContent { content(ui, { ui = it }, interactions) }
        compose.onNodeWithTag("set-actions-set-1").performClick()
        compose.onNodeWithText("Удалить").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()
        compose.onNodeWithTag("confirm-delete").assertDoesNotExist()
        compose.onNodeWithText("1. 100 кг × 5 · RIR 2").assertDoesNotExist()
        compose.onNodeWithText("Подход 1 · work").assertIsDisplayed()
        assertEquals(listOf(true, false), interactions)
    }

    @Test fun skipCommitAdvancesSlotClosesDialogAndReleasesInteraction() {
        var ui by mutableStateOf(workout(current = 1))
        val interactions = mutableListOf<Boolean>()
        compose.setContent { content(ui, { ui = it }, interactions) }
        compose.onNodeWithTag("skip-instance").performClick()
        compose.onNodeWithTag("skip-reason-equipment_busy").performClick()
        compose.onNodeWithTag("confirm-skip").performClick()
        compose.onNodeWithTag("confirm-skip").assertDoesNotExist()
        compose.onNodeWithText("1. Пропущено · Оборудование занято").assertIsDisplayed()
        compose.onNodeWithText("Подход 2 · work").assertIsDisplayed()
        assertEquals(listOf(true, false), interactions)
    }

    @Test fun skippingLastSlotStillClosesDialogAndReleasesInteraction() {
        var ui by mutableStateOf(workout(current = 2))
        val interactions = mutableListOf<Boolean>()
        compose.setContent { content(ui, { ui = it }, interactions) }
        compose.onNodeWithTag("skip-instance").performClick()
        compose.onNodeWithTag("confirm-skip").performClick()
        compose.onNodeWithTag("confirm-skip").assertDoesNotExist()
        compose.onNodeWithText("Все подходы записаны").assertIsDisplayed()
        assertEquals(listOf(true, false), interactions)
    }

    @Test fun restoreRemovesSkippedFactAndMakesSlotCurrent() {
        val skipped = SkippedSetEntity("workout", "instance", 1, "2026-09-08T12:00:00Z", "equipment_busy", null)
        var ui by mutableStateOf(workout(current = 2, skipped = listOf(skipped)))
        compose.setContent { content(ui, { ui = it }, mutableListOf()) }
        compose.onNodeWithTag("restore-instance-1").performClick()
        compose.onNodeWithText("1. Пропущено · Оборудование занято").assertDoesNotExist()
        compose.onNodeWithText("Подход 1 · work").assertIsDisplayed()
    }

    @Composable
    private fun content(state: WorkoutUiState, update: (WorkoutUiState) -> Unit, interactions: MutableList<Boolean>) {
        SportzalTheme { WorkoutScreen(state, { "0:00" }, { _, _, _, _, _ -> }, { _, _ -> },
            { _, active -> interactions += active }, {},
            onEdit = { id, weight, reps, rir, deviations, note, completed ->
                val card = state.card()
                update(state.withCard(card.copy(saved = card.saved.map { if (it.setResultId == id) it.copy(
                    weightKg = weight, reps = reps, rir = rir, deviationsJson = if ("technique_changed" in deviations) "[\"technique_changed\"]" else "[]",
                    note = note) else it })))
                completed(true)
            },
            onDelete = { id, completed ->
                val card = state.card()
                update(state.withCard(card.copy(currentSlot = slot(1), draft = draft(1), saved = card.saved.filterNot { it.setResultId == id })))
                completed(true)
            },
            onSkip = { _, setNo, reason, note, completed ->
                val card = state.card()
                val skipped = SkippedSetEntity("workout", "instance", setNo, "2026-09-08T12:00:00Z", reason, note)
                val next = if (setNo == 1) 2 else null
                update(state.withCard(card.copy(currentSlot = next?.let(::slot), draft = next?.let(::draft), skipped = card.skipped + skipped)))
                completed(true)
            },
            onRestore = { _, setNo ->
                val card = state.card()
                update(state.withCard(card.copy(currentSlot = slot(setNo), draft = draft(setNo),
                    skipped = card.skipped.filterNot { it.plannedSetNo == setNo })))
            }) }
    }

    private fun workout(current: Int? = null, saved: List<SetResultEntity> = emptyList(), skipped: List<SkippedSetEntity> = emptyList()): WorkoutUiState {
        val card = ExerciseUiState(exercise(), current?.let(::slot), current?.let(::draft), saved, skipped = skipped)
        return WorkoutUiState("workout", "Тренировка", listOf(BlockUiState("block", "Straight", "straight", listOf(card))),
            ClockReading(Instant.parse("2026-09-08T12:00:00Z"), "boot", 42))
    }
    private fun WorkoutUiState.card() = blocks.single().cards.single()
    private fun WorkoutUiState.withCard(card: ExerciseUiState) = copy(blocks = listOf(blocks.single().copy(cards = listOf(card))))
    private fun exercise() = ExerciseDocument("instance", "squat", "Присед", "rack", null, "external", "bilateral", 1,
        "all_work_sets", listOf(PlannedSetDocument(1, "work", 100.0, 5, 8, 2, 60), PlannedSetDocument(2, "work", 100.0, 5, 8, 2, 60)))
    private fun slot(no: Int) = PlannedSlot(no, "work", 100.0, 5, 8, 2, 60,
        ActualContext("squat", "rack", null, "external", "bilateral"))
    private fun draft(no: Int) = SetDraft(100.0, 5, 2, slot(no).plannedContext, true)
    private fun result() = SetResultEntity("set-1", "workout", 1, "instance", 1, "work", "squat", "Присед",
        "rack", "Rack", null, "external", "bilateral", 100.0, 5, 2, "2026-09-08T12:00:00Z", "boot", 42,
        null, "[]", null)
}
