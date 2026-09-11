package ru.sportzal.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.data.db.DraftEntity
import ru.sportzal.app.model.BlockDocument
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.model.PlannedSetDocument
import ru.sportzal.app.model.PlannedWorkoutDocument
import ru.sportzal.app.model.ProgramDocument
import ru.sportzal.app.model.SaveSetCommand
import ru.sportzal.app.model.StrictJson
import ru.sportzal.app.model.elapsedBetween
import ru.sportzal.app.model.ClockAnchor

/**
 * Exercises Activity recreation against the application's on-disk Room database. ActivityScenario.recreate()
 * is intentionally not described as Linux process death; the latter remains a real-device acceptance step.
 */
class ProcessDeathRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun clearDatabase() {
        val container = (compose.activity.application as SportzalApplication).container
        container.database.clearAllTables()
    }

    @Test fun activeWorkoutSurvivesActivityRecreation() {
        val fixture = seedActive()
        recreateAndAwaitWorkout(fixture.title)
        val runtime = runBlocking { fixture.container.database.dao().activeWorkout()!! }
        assertEquals(fixture.workoutId, runtime.workoutId)
        assertEquals(fixture.programId, runtime.programId)
        assertEquals(1, runtime.programVersion)
        assertEquals(fixture.snapshot, runtime.planSnapshotJson)
    }

    @Test fun committedSetSurvivesActivityRecreation() {
        val fixture = seedActive()
        runBlocking { fixture.container.repository.saveSet(setCommand(fixture.workoutId)) }
        recreateAndAwaitWorkout(fixture.title)
        compose.onNodeWithText("1. 100 кг × 6 · RIR 2").performScrollTo().assertExists()
        assertEquals(1, runBlocking { fixture.container.database.dao().sets(fixture.workoutId).size })
        assertNull(runBlocking { fixture.container.database.dao().setInSlot(fixture.workoutId, "exercise", 2) })
    }

    @Test fun draftSurvivesActivityRecreation() {
        val fixture = seedActive()
        runBlocking {
            fixture.container.repository.saveDraft(DraftEntity(
                fixture.workoutId, "exercise", 1, "102.5", "7", 2,
                null, "2026-09-11T10:01:00Z",
            ))
        }
        recreateAndAwaitWorkout(fixture.title)
        compose.onNodeWithTag("weight-exercise").assertTextContains("102.5")
        compose.onNodeWithTag("reps-exercise").assertTextContains("7")
        assertEquals(1, runBlocking { fixture.container.database.dao().draftCount(fixture.workoutId) })
        assertEquals(0, runBlocking { fixture.container.database.dao().setCount(fixture.workoutId) })
    }

    @Test fun plannedProgramVersionDoesNotChangeAfterRecreation() {
        val fixture = seedActive()
        runBlocking {
            val v2 = program(fixture.programId, 2, "Новая версия, не активная тренировка")
            fixture.container.repository.importProgram(v2, StrictJson.encodeToString(v2), "v2-${fixture.programId}")
        }
        recreateAndAwaitWorkout(fixture.title)
        val runtime = runBlocking { fixture.container.database.dao().workout(fixture.workoutId)!! }
        assertEquals(1, runtime.programVersion)
        assertEquals(fixture.snapshot, runtime.planSnapshotJson)
        compose.onNodeWithText(fixture.title).assertExists()
    }

    @Test fun timerRestoresFromPersistedAnchors() {
        val fixture = seedActive()
        runBlocking { fixture.container.repository.saveSet(setCommand(fixture.workoutId)) }
        recreateAndAwaitWorkout(fixture.title)
        val fact = runBlocking { fixture.container.database.dao().sets(fixture.workoutId).single() }
        assertEquals("boot-a", fact.bootId)
        assertEquals(10_000L, fact.elapsedRealtimeMs)
        assertEquals(5L, elapsedBetween(
            ClockAnchor(Instant.parse(fact.completedAt), fact.bootId, fact.elapsedRealtimeMs),
            ClockAnchor(Instant.parse(fact.completedAt), "boot-a", 15_000),
        ).duration.seconds)
        assertEquals(60L, elapsedBetween(
            ClockAnchor(Instant.parse(fact.completedAt), fact.bootId, fact.elapsedRealtimeMs),
            ClockAnchor(Instant.parse(fact.completedAt).plusSeconds(60), "boot-b", 1_000),
        ).duration.seconds)
    }

    @Test fun finishScreenRestoresWithoutSecondFinishCommit() {
        val fixture = seedActive()
        runBlocking {
            fixture.container.repository.saveSet(setCommand(fixture.workoutId, 1))
            fixture.container.repository.saveSet(setCommand(fixture.workoutId, 2, "set-2"))
            fixture.container.repository.updateWorkoutNotes(fixture.workoutId, "Заметка сохраняется")
        }
        recreateAndAwaitWorkout(fixture.title)
        // A rendered second fact proves open() reloaded the state needed by requestFinish().
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("2. 100 кг × 6 · RIR 2").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText("Завершить тренировку"))
        compose.onNodeWithText("Завершить тренировку").performClick()

        // Distinguish a committed finish from an unexpected confirmation before testing recovery UI.
        compose.waitUntil(5_000) {
            val committed = runBlocking {
                fixture.container.database.dao().workout(fixture.workoutId)?.completionStatus != "active"
            }
            committed || compose.onAllNodesWithText("Завершить с невыполненными подходами?")
                .fetchSemanticsNodes().isNotEmpty()
        }
        val confirmationVisible = compose.onAllNodesWithText("Завершить с невыполненными подходами?")
            .fetchSemanticsNodes().isNotEmpty()
        assertFalse("Fully completed workout unexpectedly requested finish confirmation", confirmationVisible)
        val committed = runBlocking { fixture.container.database.dao().workout(fixture.workoutId)!! }
        assertNotNull("Finish click did not commit finished_at", committed.finishedAt)
        assertEquals("completed", committed.completionStatus)

        compose.waitUntil(5_000) { compose.onAllNodesWithText("Тренировка завершена").fetchSemanticsNodes().isNotEmpty() }
        val before = runBlocking { fixture.container.database.dao().workout(fixture.workoutId)!! }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Тренировка завершена").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Заметка сохраняется").assertExists()
        val after = runBlocking { fixture.container.database.dao().workout(fixture.workoutId)!! }
        assertEquals(before.finishedAt, after.finishedAt)
        assertEquals(before.completionStatus, after.completionStatus)
        assertEquals(2, runBlocking { fixture.container.database.dao().sets(fixture.workoutId).size })
    }

    private fun seedActive(): Fixture {
        compose.waitForIdle()
        val container = (compose.activity.application as SportzalApplication).container
        val suffix = UUID.randomUUID().toString()
        val id = "program-$suffix"
        val title = "Восстановленная тренировка $suffix"
        return runBlocking {
            container.database.clearAllTables()
            assertNull(container.database.dao().activeWorkout())
            assertEquals(0, container.database.dao().workouts().size)
            val document = program(id, 1, title)
            container.repository.importProgram(document, StrictJson.encodeToString(document), "hash-$suffix")
            val workoutId = container.repository.startWorkout(id, 1, "session-$suffix")
            val snapshot = container.database.dao().workout(workoutId)!!.planSnapshotJson
            Fixture(container, id, workoutId, title, snapshot)
        }
    }

    private fun recreateAndAwaitWorkout(title: String) {
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(title).assertExists()
    }

    private fun program(id: String, version: Int, title: String): ProgramDocument {
        val suffix = id.removePrefix("program-")
        return ProgramDocument(
            "sportzal.program", 1, id, version, "2026-09-11T10:00:00Z", emptyList(),
            listOf(PlannedWorkoutDocument(
                "session-$suffix", "template", title, "2026-09-11", listOf(BlockDocument(
                    "block", "Основной блок", "straight", listOf(ExerciseDocument(
                        "exercise", "squat", "Присед", null, null, "external", "bilateral", 1,
                        "all_work_sets", listOf(
                            PlannedSetDocument(1, "work", 100.0, 5, 8, 2, 120),
                            PlannedSetDocument(2, "work", 100.0, 5, 8, 2, 120),
                        ),
                    )),
                )),
            )),
        )
    }

    private fun setCommand(workoutId: String, planned: Int = 1, id: String = "set-1") = SaveSetCommand(
        id, workoutId, "exercise", planned, "work", "squat", "Присед", loadBasisActual = "external",
        sideActual = "bilateral", weightKg = 100.0, reps = 6, rir = 2,
        completedAt = "2026-09-11T10:01:00Z", bootId = "boot-a", elapsedRealtimeMs = 10_000,
    )

    private data class Fixture(
        val container: AppContainer,
        val programId: String,
        val workoutId: String,
        val title: String,
        val snapshot: String,
    )
}