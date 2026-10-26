package ru.sportzal.app.ui.workout

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.*
import ru.sportzal.app.model.*

class WorkoutNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as SportzalApplication).container

    @After fun cleanup() { runBlocking { container.database.clearAllTables() } }

    @Test fun missingActiveWorkoutShowsRecoveryInsteadOfInfiniteLoading() {
        compose.waitForIdle()
        val id = runBlocking {
            container.database.clearAllTables()
            val exercise = ExerciseDocument("e", "row", "Тяга", null, null, "bodyweight", "bilateral", 1, "none",
                listOf(PlannedSetDocument(1, "work", 0.0, 8, 8, null, 60)))
            val program = ProgramDocument("sportzal.program", 1, "missing", 1, "2026-10-26T10:00:00Z", emptyList(),
                listOf(PlannedWorkoutDocument("missing-session", "A", "Тренировка", "2026-10-26", listOf(BlockDocument("b", "Блок", "straight", listOf(exercise))))))
            container.repository.importProgram(program, StrictJson.encodeToString(program), "missing-hash")
            container.repository.startWorkout("missing", 1, "missing-session")
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Продолжить тренировку").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { container.database.dao().deleteWorkout(id) }
        compose.onNodeWithText("Продолжить тренировку").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("На главный экран").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Повторить").assertExists()
        compose.onNodeWithText("На главный экран").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Открыть тренировку").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun previewDoesNotStartAndBackPreservesDraftAndExplicitUnassessedRir() {
        compose.waitForIdle()
        runBlocking {
            container.database.clearAllTables()
            val program = ProgramDocument("sportzal.program", 1, "navigation", 1, "2026-10-26T10:00:00Z", emptyList(),
                listOf(PlannedWorkoutDocument("session", "A", "Тренировка A", "2026-10-26", listOf(BlockDocument("block", "Основной блок", "straight",
                    listOf(ExerciseDocument("exercise", "row", "Тяга", null, "Упор 2", "total_external", "bilateral", 1, "all_work_sets",
                        listOf(PlannedSetDocument(1, "work", 20.0, 8, 12, 2, 90)))))))))
            container.repository.importProgram(program, StrictJson.encodeToString(program), "navigation-hash")
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Открыть тренировку").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Открыть тренировку").performScrollTo().performClick()
        compose.onNodeWithText("Начать тренировку").performScrollTo().assertExists()
        assertNull(runBlocking { container.database.dao().activeWorkout() })
        compose.onNodeWithText("Начать тренировку").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("workout-overview").fetchSemanticsNodes().isNotEmpty() }
        val id = runBlocking { container.database.dao().activeWorkout()!!.workoutId }
        compose.onNodeWithTag("open-exercise").performScrollTo().performClick()
        compose.onNodeWithTag("weight-exercise").performScrollTo().performTextReplacement("22,5")
        compose.onNodeWithTag("reps-exercise").performScrollTo().performTextReplacement("9")
        compose.onNodeWithText("Не оценил").performScrollTo().performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText("К упражнениям"))
        compose.onNodeWithText("К упражнениям").assertIsDisplayed().performClick()
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("workout-overview").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            println(compose.onRoot().printToString())
            println("After Back active=" + runBlocking { container.database.dao().activeWorkout() })
            val bytes = java.io.ByteArrayOutputStream()
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
            java.io.File(compose.activity.filesDir, "review-navigation-failure.png").writeBytes(bytes.toByteArray())
            throw failure
        }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Продолжить тренировку").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Продолжить тренировку").performScrollTo().performClick()
        compose.onNodeWithTag("open-exercise").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("weight-exercise").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("weight-exercise").performScrollTo().assertTextContains("22,5")
        compose.onNodeWithTag("reps-exercise").performScrollTo().assertTextContains("9")
        compose.onNodeWithText("Не оценил").performScrollTo().assertIsSelected()
        assertEquals(id, runBlocking { container.database.dao().activeWorkout()!!.workoutId })
        assertEquals(0, runBlocking { container.database.dao().setCount(id) })
        compose.onNodeWithTag("save-exercise").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { container.database.dao().setCount(id) == 1 } }
        val fact = runBlocking { container.database.dao().sets(id).single() }
        assertEquals(22.5, fact.weightKg, 0.0)
        assertEquals(9, fact.reps)
        assertNull(fact.rir)
    }
}
