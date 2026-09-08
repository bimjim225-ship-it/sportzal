package ru.sportzal.app.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.domain.ActualContext
import ru.sportzal.app.domain.ClockReading
import ru.sportzal.app.domain.PlannedSlot
import ru.sportzal.app.domain.SetDraft
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.model.PlannedSetDocument
import ru.sportzal.app.ui.theme.SportzalTheme
import java.time.Instant

class FastSetLoggingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun straightSetCanBeLoggedWithOneEditOneRirChoiceAndOneSave() {
        val exercise = ExerciseDocument("instance", "squat", "Присед", "rack", "Высокий гриф",
            "external", "bilateral", 1, "all_work_sets", listOf(
                PlannedSetDocument(1, "work", 100.0, 5, 8, 2, 120),
                PlannedSetDocument(2, "work", 100.0, 5, 8, 2, 120)))
        fun slot(no: Int) = PlannedSlot(no, "work", 100.0, 5, 8, 2, 120,
            ActualContext("squat", "rack", "Высокий гриф", "external", "bilateral"))
        var saves = 0
        var ui by mutableStateOf(WorkoutUiState("workout", "Тренировка", listOf(
            ExerciseUiState(exercise, slot(1), SetDraft(100.0, 5, null, slot(1).plannedContext), emptyList())),
            ClockReading(Instant.parse("2026-09-08T12:02:00Z"), "boot", 120_000)))
        compose.setContent { SportzalTheme { WorkoutScreen(ui, { "0:00" }, { _, weight, reps, rir ->
            ui = ui.copy(cards = listOf(ui.cards.single().copy(draft = SetDraft(weight, reps, rir, slot(1).plannedContext))))
        }, {
            saves++
            if (saves == 1) {
                val row = result(reps = ui.cards.single().draft!!.reps!!)
                ui = ui.copy(cards = listOf(ExerciseUiState(exercise, slot(2), SetDraft(100.0, 7, null, slot(2).plannedContext), listOf(row))))
            }
        }, {}, {}) } }

        compose.onNodeWithText("Цель: 100 кг · 5–8 · RIR 2").assertIsDisplayed()
        compose.onNodeWithTag("reps-instance").performTextClearance()
        compose.onNodeWithTag("reps-instance").performTextInput("7")
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithTag("save-instance").performClick()
        compose.onNodeWithText("1. 100 кг × 7 · RIR 2").assertIsDisplayed()
        compose.onNodeWithText("Подход 2 · work").assertIsDisplayed()
        compose.onNodeWithText("С записи: 0:00").assertIsDisplayed()
        compose.onNodeWithTag("save-instance").performClick()
        assertEquals("UI exposes only one fact for the planned slot", 1, ui.cards.single().saved.size)
    }

    private fun result(reps: Int) = SetResultEntity("set", "workout", 1, "instance", 1, "work", "squat", "Присед",
        "rack", null, "Высокий гриф", "external", "bilateral", 100.0, reps, 2,
        "2026-09-08T12:02:00Z", "boot", 120_000, null, "[]", null)
}
