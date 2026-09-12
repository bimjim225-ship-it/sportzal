package ru.sportzal.app.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
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
        val interactions = mutableListOf<Pair<InteractionSource, Boolean>>()
        var request: EditRequest? = null
        compose.setContent { content(ui, { ui = it }, interactions, onEdit = { id, weight, reps, rir, deviations, note, completed ->
            request = EditRequest(id, weight, reps, rir, deviations, note, completed)
        }) }

        compose.onNodeWithTag("set-actions-set-1").performClick()
        compose.onNodeWithText("Изменить").performClick()
        compose.onNodeWithTag("edit-reps").performTextClearance()
        compose.onNodeWithTag("edit-reps").performTextInput("8")
        compose.onNodeWithText("3 повтора в запасе").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("deviation-technique_changed").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("save-edit").assertIsDisplayed().assertIsEnabled().performClick()

        compose.onNodeWithTag("save-edit").assertIsDisplayed()
        compose.onNodeWithText("1. 100 кг × 5 · Запас: 2").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(InteractionSource.SET_ACTIONS to true), interactions.filter { it.first == InteractionSource.SET_ACTIONS })
            assertEquals(5, ui.card().saved.single().reps)
            val edit = requireNotNull(request)
            assertEquals("set-1", edit.id)
            assertEquals(100.0, edit.weight, 0.0)
            assertEquals(8, edit.reps)
            assertEquals(3, edit.rir)
            assertEquals(true, "technique_changed" in edit.deviations)
            val card = ui.card()
            ui = ui.withCard(card.copy(saved = card.saved.map { if (it.setResultId == edit.id) it.copy(
                weightKg = edit.weight, reps = edit.reps, rir = edit.rir,
                deviationsJson = "[\"technique_changed\"]", note = edit.note) else it }))
            edit.completed(true)
        }
        compose.runOnIdle { assertEquals(8, ui.card().saved.single().reps); assertEquals(3, ui.card().saved.single().rir) }
        compose.onNodeWithTag("save-edit").assertDoesNotExist()
        compose.onNodeWithText("1. 100 кг × 8 · Запас: 3").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(InteractionSource.SET_ACTIONS to true, InteractionSource.SET_ACTIONS to false),
            interactions.filter { it.first == InteractionSource.SET_ACTIONS })
    }

    @Test fun failedEditKeepsDialogAndEnteredValuesForRetry() {
        var ui by mutableStateOf(workout(saved = listOf(result())))
        val interactions = mutableListOf<Pair<InteractionSource, Boolean>>()
        var attempts = 0
        compose.setContent { SportzalTheme { WorkoutScreen(ui, { "0:00" }, { _, _, _, _, _ -> }, { _, _, _, _, _, _ -> },
            { _, source, active -> interactions += source to active }, {}, onEdit = { _, _, _, _, _, _, completed ->
                attempts++
                completed(false)
            }) } }
        compose.onNodeWithTag("set-actions-set-1").performClick()
        compose.onNodeWithText("Изменить").performClick()
        compose.onNodeWithTag("edit-reps").performTextClearance()
        compose.onNodeWithTag("edit-reps").performTextInput("9")
        compose.onNodeWithTag("save-edit").performClick()
        compose.onNodeWithTag("save-edit").assertIsDisplayed()
        compose.onNodeWithTag("edit-reps")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString("9")
                )
            )
        compose.onNodeWithTag("save-edit").performClick()
        compose.onNodeWithTag("edit-reps")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString("9")
                )
            )
        assertEquals(2, attempts)
        assertEquals(listOf(InteractionSource.SET_ACTIONS to true),
            interactions.filter { it.first == InteractionSource.SET_ACTIONS })
    }

    @Test fun deleteCommitClosesDialogReleasesInteractionAndReturnsSlot() {
        var ui by mutableStateOf(workout(saved = listOf(result())))
        val interactions = mutableListOf<Pair<InteractionSource, Boolean>>()
        var request: DeleteRequest? = null
        compose.setContent { content(ui, { ui = it }, interactions, onDelete = { id, completed -> request = DeleteRequest(id, completed) }) }
        compose.onNodeWithTag("set-actions-set-1").performClick()
        compose.onNodeWithText("Удалить").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()
        compose.onNodeWithTag("confirm-delete").assertIsDisplayed()
        compose.onNodeWithText("1. 100 кг × 5 · Запас: 2").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(InteractionSource.SET_ACTIONS to true), interactions.filter { it.first == InteractionSource.SET_ACTIONS })
            assertEquals(1, ui.card().saved.size)
            val delete = requireNotNull(request)
            val card = ui.card()
            ui = ui.withCard(card.copy(currentSlot = slot(1), draft = draft(1), saved = card.saved.filterNot { it.setResultId == delete.id }))
            delete.completed(true)
        }
        compose.onNodeWithTag("confirm-delete").assertDoesNotExist()
        compose.onNodeWithText("1. 100 кг × 5 · Запас: 2").assertDoesNotExist()
        compose.onNodeWithText("Подход 1 · Рабочий").assertIsDisplayed()
        assertEquals(listOf(InteractionSource.SET_ACTIONS to true, InteractionSource.SET_ACTIONS to false),
            interactions.filter { it.first == InteractionSource.SET_ACTIONS })
    }

    @Test fun skipCommitAdvancesSlotClosesDialogAndReleasesInteraction() {
        var ui by mutableStateOf(workout(current = 1))
        val interactions = mutableListOf<Pair<InteractionSource, Boolean>>()
        var request: SkipRequest? = null
        compose.setContent { content(ui, { ui = it }, interactions, onSkip = { _, setNo, reason, note, completed ->
            request = SkipRequest(setNo, reason, note, completed)
        }) }
        compose.onNodeWithTag("skip-instance").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("confirm-skip").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(InteractionSource.SKIP_DIALOG to true), interactions.filter { it.first == InteractionSource.SKIP_DIALOG }) }
        compose.onNodeWithTag("skip-reason-equipment_busy").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("confirm-skip").performClick()
        compose.onNodeWithTag("confirm-skip").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, ui.card().currentSlot?.plannedSetNo)
            val skip = requireNotNull(request)
            assertEquals(1, skip.setNo); assertEquals("equipment_busy", skip.reason)
            val card = ui.card()
            ui = ui.withCard(card.copy(currentSlot = slot(2), draft = draft(2), skipped = card.skipped +
                SkippedSetEntity("workout", "instance", skip.setNo, "2026-09-08T12:00:00Z", skip.reason, skip.note)))
            skip.completed(true)
        }
        compose.onNodeWithTag("confirm-skip").assertDoesNotExist()
        compose.onNodeWithText("1. Пропущено · Оборудование занято").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Подход 2 · Рабочий").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(InteractionSource.SKIP_DIALOG to true, InteractionSource.SKIP_DIALOG to false),
            interactions.filter { it.first == InteractionSource.SKIP_DIALOG })
    }

    @Test fun skippingLastSlotStillClosesDialogAndReleasesInteraction() {
        var ui by mutableStateOf(workout(current = 2, saved = listOf(result())))
        val interactions = mutableListOf<Pair<InteractionSource, Boolean>>()
        var request: SkipRequest? = null
        compose.setContent { content(ui, { ui = it }, interactions, onSkip = { _, setNo, reason, note, completed ->
            request = SkipRequest(setNo, reason, note, completed)
        }) }
        compose.onNodeWithTag("skip-instance").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("confirm-skip").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(InteractionSource.SKIP_DIALOG to true), interactions.filter { it.first == InteractionSource.SKIP_DIALOG }) }
        compose.onNodeWithTag("confirm-skip").performClick()
        compose.onNodeWithTag("confirm-skip").assertIsDisplayed()
        compose.onNodeWithText("1. 100 кг × 5 · Запас: 2").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, ui.card().currentSlot?.plannedSetNo)
            val skip = requireNotNull(request)
            val card = ui.card()
            ui = ui.withCard(card.copy(currentSlot = null, draft = null, skipped = card.skipped +
                SkippedSetEntity("workout", "instance", skip.setNo, "2026-09-08T12:00:00Z", skip.reason, skip.note)))
            skip.completed(true)
        }
        compose.onNodeWithTag("confirm-skip").assertDoesNotExist()
        compose.onNodeWithText("1. 100 кг × 5 · Запас: 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2. Пропущено").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Все подходы записаны").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(InteractionSource.SKIP_DIALOG to true, InteractionSource.SKIP_DIALOG to false),
            interactions.filter { it.first == InteractionSource.SKIP_DIALOG })
    }

    @Test fun skipDialogKeepsActionsAvailableOnSmallViewport() {
        val ui = workout(current = 1)
        compose.setContent { content(ui, {}, mutableListOf()) }

        compose.onNodeWithTag("skip-instance").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("confirm-skip").assertIsDisplayed()
        compose.onNodeWithTag("skip-reason-none").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("skip-reason-equipment_busy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("skip-reason-other").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("confirm-skip").assertIsDisplayed()
    }

    @Test fun restoreRemovesSkippedFactAndMakesSlotCurrent() {
        val skipped = SkippedSetEntity("workout", "instance", 1, "2026-09-08T12:00:00Z", "equipment_busy", null)
        var ui by mutableStateOf(workout(current = 2, skipped = listOf(skipped)))
        compose.setContent { content(ui, { ui = it }, mutableListOf<Pair<InteractionSource, Boolean>>()) }
        compose.onNodeWithTag("restore-instance-1").performClick()
        compose.onNodeWithText("1. Пропущено · Оборудование занято").assertDoesNotExist()
        compose.onNodeWithText("Подход 1 · Рабочий").assertIsDisplayed()
    }

    @Composable
    private fun content(state: WorkoutUiState, update: (WorkoutUiState) -> Unit,
        interactions: MutableList<Pair<InteractionSource, Boolean>>,
        onEdit: ((String, Double, Int, Int?, List<String>, String?, (Boolean) -> Unit) -> Unit)? = null,
        onDelete: ((String, (Boolean) -> Unit) -> Unit)? = null,
        onSkip: ((String, Int, String?, String?, (Boolean) -> Unit) -> Unit)? = null) {
        SportzalTheme { WorkoutScreen(state, { "0:00" }, { _, _, _, _, _ -> }, { _, _, _, _, _, _ -> },
            { _, source, active -> interactions += source to active }, {},
            onEdit = onEdit ?: { id, weight, reps, rir, deviations, note, completed ->
                val card = state.card()
                update(state.withCard(card.copy(saved = card.saved.map { if (it.setResultId == id) it.copy(
                    weightKg = weight, reps = reps, rir = rir, deviationsJson = if ("technique_changed" in deviations) "[\"technique_changed\"]" else "[]",
                    note = note) else it })))
                completed(true)
            },
            onDelete = onDelete ?: { id, completed ->
                val card = state.card()
                update(state.withCard(card.copy(currentSlot = slot(1), draft = draft(1), saved = card.saved.filterNot { it.setResultId == id })))
                completed(true)
            },
            onSkip = onSkip ?: { _, setNo, reason, note, completed ->
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

    private data class EditRequest(val id: String, val weight: Double, val reps: Int, val rir: Int?,
        val deviations: List<String>, val note: String?, val completed: (Boolean) -> Unit)
    private data class DeleteRequest(val id: String, val completed: (Boolean) -> Unit)
    private data class SkipRequest(val setNo: Int, val reason: String?, val note: String?, val completed: (Boolean) -> Unit)
}
