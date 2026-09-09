package ru.sportzal.app.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.domain.ActualContext
import ru.sportzal.app.domain.ClockReading
import ru.sportzal.app.domain.ExerciseRuntimeCard
import ru.sportzal.app.domain.PlannedSlot
import ru.sportzal.app.domain.RotationCoordinator
import ru.sportzal.app.domain.SetDraft
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.model.PlannedSetDocument
import ru.sportzal.app.ui.theme.SportzalTheme
import java.time.Instant

class RotationInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun focusedCardDefersReorderAndKeepsDraftWithExercise() {
        val now = ClockReading(Instant.parse("2026-09-08T12:00:00Z"), "boot", 1_000)
        val coordinator = RotationCoordinator(listOf(runtime("a", 1), runtime("b", 2)))
        val exercises = mapOf("a" to exercise("a", 1), "b" to exercise("b", 2))
        val drafts = mutableMapOf("a" to draft(100.0, 5), "b" to draft(80.0, 8))
        var ui by mutableStateOf(state(coordinator, exercises, drafts, now))
        lateinit var focusManager: FocusManager
        fun publish() { ui = state(coordinator, exercises, drafts, now) }

        compose.setContent {
            SportzalTheme {
                focusManager = LocalFocusManager.current
                WorkoutScreen(ui, { "0:00" }, { id, weight, reps, rir, answered ->
                    drafts[id] = SetDraft(weight, reps, rir, drafts.getValue(id).context, answered)
                    publish()
                }, { _, _ -> }, { _, _, focused ->
                    if (focused) coordinator.beginInteraction() else coordinator.endInteraction()
                    publish()
                }, { publish() })
            }
        }

        compose.onNodeWithTag("weight-a").performClick()
        coordinator.update(listOf(runtime("b", 1), runtime("a", 2)), now, committed = true)
        publish()
        assertEquals(listOf("a", "b"), ui.blocks.single().cards.map { it.exercise.exerciseInstanceId })

        compose.onNodeWithTag("reps-a").performClick()
        assertEquals(listOf("a", "b"), ui.blocks.single().cards.map { it.exercise.exerciseInstanceId })
        compose.onNodeWithTag("reps-a").performTextClearance()
        compose.onNodeWithTag("reps-a").performTextInput("7")
        compose.onNodeWithTag("reps-a").assertIsDisplayed()

        compose.runOnIdle { focusManager.clearFocus(force = true) }
        compose.waitForIdle()
        assertEquals(listOf("b", "a"), ui.blocks.single().cards.map { it.exercise.exerciseInstanceId })
        assertEquals(7, ui.blocks.single().cards.last().draft?.reps)

        val afterRelease = ui.blocks.single().cards.map { it.exercise.exerciseInstanceId }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(afterRelease, ui.blocks.single().cards.map { it.exercise.exerciseInstanceId })
    }

    @Test fun overlayOwnerKeepsRotationFrozenAfterFocusOwnerIsReleased() {
        val now = ClockReading(Instant.parse("2026-09-08T12:00:00Z"), "boot", 1_000)
        val coordinator = RotationCoordinator(listOf(runtime("a", 1), runtime("b", 2)))
        val exercises = mapOf("a" to exercise("a", 1), "b" to exercise("b", 2))
        val drafts = mapOf("a" to draft(100.0, 5), "b" to draft(80.0, 8))
        val owners = mutableSetOf<Pair<String, InteractionSource>>()
        var ui by mutableStateOf(state(coordinator, exercises, drafts, now).let { workout ->
            val block = workout.blocks.single()
            workout.copy(blocks = listOf(block.copy(cards = block.cards.map { card ->
                if (card.exercise.exerciseInstanceId == "a") card.copy(saved = listOf(result("a"))) else card
            })))
        })
        lateinit var focusManager: FocusManager
        fun publish() {
            val saved = ui.blocks.single().cards.associate { it.exercise.exerciseInstanceId to it.saved }
            ui = state(coordinator, exercises, drafts, now).let { workout ->
                val block = workout.blocks.single()
                workout.copy(blocks = listOf(block.copy(cards = block.cards.map { card ->
                    card.copy(saved = saved[card.exercise.exerciseInstanceId].orEmpty())
                })))
            }
        }

        compose.setContent {
            SportzalTheme {
                focusManager = LocalFocusManager.current
                WorkoutScreen(ui, { "0:00" }, { _, _, _, _, _ -> }, { _, _ -> },
                    { id, source, active ->
                        val wasActive = owners.isNotEmpty()
                        val owner = id to source
                        if (active) owners += owner else owners -= owner
                        if (!wasActive && owners.isNotEmpty()) coordinator.beginInteraction()
                        if (wasActive && owners.isEmpty()) coordinator.endInteraction()
                        publish()
                    }, { publish() },
                    onEdit = { _, _, _, _, _, _, completed -> completed(true) })
            }
        }

        compose.onNodeWithTag("weight-a").performClick()
        compose.waitForIdle()
        assertTrue("card focus must acquire its own owner", "a" to InteractionSource.FOCUS in owners)
        compose.onNodeWithTag("set-actions-set-a").performClick()
        compose.onNodeWithText("Изменить").performClick()
        compose.runOnIdle { focusManager.clearFocus(force = true) }
        compose.waitForIdle()

        assertTrue("the overlay must remain an independent owner", "a" to InteractionSource.SET_ACTIONS in owners)
        assertTrue("focus release must not clear the block freeze", owners.isNotEmpty())
        coordinator.update(listOf(runtime("b", 1), runtime("a", 2)), now, committed = true)
        publish()
        assertEquals(listOf("a", "b"), ui.blocks.single().cards.map { it.exercise.exerciseInstanceId })

        compose.onNodeWithTag("save-edit").performClick()
        compose.waitForIdle()
        assertTrue("successful overlay close releases the final owner", owners.isEmpty())
        assertEquals(listOf("b", "a"), ui.blocks.single().cards.map { it.exercise.exerciseInstanceId })
    }

    private fun state(
        coordinator: RotationCoordinator,
        exercises: Map<String, ExerciseDocument>,
        drafts: Map<String, SetDraft>,
        now: ClockReading,
    ) = WorkoutUiState("workout", "Rotation", listOf(BlockUiState("rotation", "Rotation", "rotation",
        coordinator.cards.map { ExerciseUiState(exercises.getValue(it.exerciseInstanceId), slot(it.exerciseInstanceId),
            drafts.getValue(it.exerciseInstanceId), emptyList()) })), now)

    private fun exercise(id: String, order: Int) = ExerciseDocument(id, id, id.uppercase(), null, null,
        "external", "bilateral", order, "none", listOf(PlannedSetDocument(1, "work", 100.0, 5, 8, null, 60)))
    private fun slot(id: String) = PlannedSlot(1, "work", 100.0, 5, 8, null, 60,
        ActualContext(id, null, null, "external", "bilateral"))
    private fun draft(weight: Double, reps: Int) = SetDraft(weight, reps, null,
        ActualContext("unused", null, null, "external", "bilateral"))
    private fun runtime(id: String, order: Int) = ExerciseRuntimeCard(id, order)
    private fun result(id: String) = SetResultEntity("set-$id", "workout", 1, id, 1, "work", id, id.uppercase(),
        null, null, null, "external", "bilateral", 100.0, 5, null, "2026-09-08T11:59:00Z", "boot", 0,
        null, "[]", null)
}
