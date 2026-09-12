package ru.sportzal.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.Instant
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
import ru.sportzal.app.ui.workout.BlockUiState
import ru.sportzal.app.ui.workout.ExerciseUiState
import ru.sportzal.app.ui.workout.WorkoutScreen
import ru.sportzal.app.ui.workout.WorkoutUiState

/** Automated semantics/layout smoke only; this is not a substitute for a TalkBack device pass. */
class AccessibilitySmokeTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var focusManager: FocusManager

    @Test fun workoutRemainsReachableAt320DpAndTwoHundredPercentFontScale() {
        setWorkoutContent()

        compose.onNodeWithText("Очень длинное название упражнения для проверки переноса на узком экране")
            .assertExists()
        compose.onNodeWithText("Цель: 100 кг · 5–8 · Запас: 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("weight-exercise").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reps-exercise").performScrollTo().performClick().assertIsFocused()
        (0..4).forEach { value ->
            compose.onNodeWithTag("rir-$value").performScrollTo()
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
        }
        compose.onNodeWithTag("rir-not-assessed").performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        compose.onNodeWithTag("save-exercise").performScrollTo().assertIsDisplayed()

        // Save must remain reachable while the IME is active. Finish only needs to be reachable
        // after the user leaves the field, then the lazy container can bring its final item in.
        compose.runOnIdle { focusManager.clearFocus(force = true) }
        compose.waitForIdle()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText("Завершить тренировку"))
        compose.onNodeWithText("Завершить тренировку").assertIsDisplayed()
    }

    @Test fun iconMenusHaveRussianLabelsAndTimerHasStableSemantics() {
        setWorkoutContent()

        compose.onNode(hasContentDescription("Меню упражнения")).assertExists()
        compose.onNode(hasContentDescription("Меню записанного подхода")).assertExists()
        compose.onNode(hasContentDescription("Таймер отдыха")).assertExists()
    }

    private fun setWorkoutContent() {
        val context = ActualContext("squat", "rack", "Высокий гриф", "external", "bilateral")
        val slot = PlannedSlot(2, "work", 100.0, 5, 8, 2, 120, context)
        val exercise = ExerciseDocument(
            "exercise", "squat",
            "Очень длинное название упражнения для проверки переноса на узком экране",
            "rack", "Высокий гриф", "external", "bilateral", 1, "all_work_sets",
            listOf(
                PlannedSetDocument(1, "work", 100.0, 5, 8, 2, 120),
                PlannedSetDocument(2, "work", 100.0, 5, 8, 2, 120),
            ),
        )
        val saved = SetResultEntity(
            "saved", "workout", 1, "exercise", 1, "work", "squat", exercise.title,
            "rack", "Высокий гриф", null, "external", "bilateral", 100.0, 6, 2,
            "2026-09-11T10:00:00Z", "boot", 1_000, null, "[]", null,
        )
        val state = WorkoutUiState(
            "workout", "Тренировка", listOf(BlockUiState("block", "Основной блок", "straight", listOf(
                ExerciseUiState(exercise, slot, SetDraft(100.0, 5, null, context), listOf(saved)),
            ))), ClockReading(Instant.parse("2026-09-11T10:01:00Z"), "boot", 61_000),
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                SportzalTheme {
                    focusManager = LocalFocusManager.current
                    Box(Modifier.width(320.dp)) {
                        WorkoutScreen(state, { "1:00" }, { _, _, _, _, _ -> }, { _, _, _, _, _, _ -> }, { _, _, _ -> }, {})
                    }
                }
            }
        }
    }
}
