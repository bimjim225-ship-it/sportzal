package ru.sportzal.app.ui.today

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.domain.PlannedWorkout
import ru.sportzal.app.domain.TodaySelection
import ru.sportzal.app.model.WorkoutRuntime

class TodayScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun workout(id: String, title: String, date: String = "2026-09-01") =
        PlannedWorkout("program", 1, id, title, LocalDate.parse(date), 1)

    private fun show(
        selection: TodaySelection,
        choices: List<PlannedWorkout> = emptyList(),
        onChoose: (String) -> Unit = {},
    ) = compose.setContent {
        TodayScreen(selection, choices, null, null, {}, {}, onChoose, {}, {})
    }

    @Test fun noProgramShowsImport() {
        show(TodaySelection.NoProgram)
        compose.onNodeWithText("Импортировать программу").assertIsDisplayed()
    }

    @Test fun exhaustedShowsImport() {
        show(TodaySelection.Exhausted)
        compose.onNodeWithText("План выполнен").assertIsDisplayed()
        compose.onNodeWithText("Импортировать программу").assertIsDisplayed()
    }

    @Test fun readyShowsCorrectPrimaryWorkout() {
        show(TodaySelection.Ready(workout("primary", "Первая тренировка")))
        compose.onNodeWithText("Первая тренировка").assertIsDisplayed()
    }

    @Test fun resumeShowsContinue() {
        show(TodaySelection.Resume(WorkoutRuntime("active", "{}", "[]")))
        compose.onNodeWithText("Продолжить тренировку").assertIsDisplayed()
        compose.onNodeWithText("Импортировать программу").assertIsDisplayed()
    }

    @Test fun manualAvailableWorkoutIsDisplayedAndClickedExactly() {
        var selected: String? = null
        show(
            TodaySelection.Ready(workout("first", "Первая overdue")),
            listOf(workout("second", "Вторая overdue")),
        ) { selected = it }
        compose.onNodeWithText("Вторая overdue").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("second", selected) }
    }
}
