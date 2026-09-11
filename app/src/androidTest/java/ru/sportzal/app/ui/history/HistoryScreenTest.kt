package ru.sportzal.app.ui.history

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.serialization.encodeToString
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.model.BlockDocument
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.model.HistoryWorkoutDetails
import ru.sportzal.app.model.PlannedSetDocument
import ru.sportzal.app.model.PlannedWorkoutDocument
import ru.sportzal.app.model.StrictJson
import ru.sportzal.app.model.WorkoutRuntime
import ru.sportzal.app.ui.theme.SportzalTheme

class HistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun detailShowsDurationRirBucketActualContextDeviationAndFactNote() {
        compose.setContent {
            SportzalTheme {
                HistoryDetailScreen(
                    state = historyState(),
                    onBack = {},
                    onNote = { _, done -> done(true) },
                    onEdit = { _, _, _, _, _, done -> done(true) },
                    onDelete = { _, done -> done(true) },
                    onShare = {},
                )
            }
        }

        compose.onNodeWithText("Длительность: 30:00").assertExists()
        compose.onNodeWithText("Факт: 100 кг × 8 · RIR 4+ · 12:15").assertExists()
        compose.onNodeWithText("Факт: Жим ногами · сиденье 4 · machine_display · bilateral").assertExists()
        compose.onNodeWithText("Отклонения: Дискомфорт").assertExists()
        compose.onNodeWithText("Комментарий: Болело колено").assertExists()
    }

    @Test
    fun historicalEditorExposesFullFactualContext() {
        compose.setContent {
            SportzalTheme {
                HistoryDetailScreen(
                    state = historyState(),
                    onBack = {},
                    onNote = { _, done -> done(true) },
                    onEdit = { _, _, _, _, _, done -> done(true) },
                    onDelete = { _, done -> done(true) },
                    onShare = {},
                )
            }
        }

        compose.onNodeWithText("Изменить").performClick()
        compose.onNodeWithText("ID оборудования").assertExists()
        compose.onNodeWithText("Настройка / setup").assertExists()
        compose.onNodeWithText("Отклонения").assertExists()
        compose.onNodeWithText("4+").assertExists()
    }

    private fun historyState(): HistoryUiState {
        val exercise = ExerciseDocument(
            "instance",
            "leg-press",
            "Жим ногами",
            "machine-1",
            "сиденье 3",
            "machine_display",
            "bilateral",
            1,
            "all_work_sets",
            listOf(PlannedSetDocument(1, "work", 90.0, 8, 10, 3, 120)),
        )
        val plan = PlannedWorkoutDocument(
            "session",
            "template",
            "Ноги",
            "2026-09-08",
            listOf(BlockDocument("block", "Основной блок", "straight", listOf(exercise))),
        )
        val runtime = WorkoutRuntime(
            workoutId = "workout",
            planSnapshotJson = StrictJson.encodeToString(plan),
            equipmentAtStartJson = "[]",
            startedAt = "2026-09-08T11:45:00Z",
            finishedAt = "2026-09-08T12:15:00Z",
            completionStatus = "completed",
            notes = "Заметка тренировки",
        )
        val fact = SetResultEntity(
            setResultId = "fact",
            workoutId = "workout",
            sequenceNo = 1,
            exerciseInstanceId = "instance",
            plannedSetNo = 1,
            setType = "work",
            exerciseIdActual = "leg-press",
            titleActual = "Жим ногами",
            equipmentIdActual = "machine-2",
            equipmentNameActual = "Жим ногами",
            setupActual = "сиденье 4",
            loadBasisActual = "machine_display",
            sideActual = "bilateral",
            weightKg = 100.0,
            reps = 8,
            rir = 4,
            completedAt = "2026-09-08T12:15:00Z",
            bootId = "boot",
            elapsedRealtimeMs = 42L,
            editedAt = null,
            deviationsJson = StrictJson.encodeToString(listOf("discomfort")),
            note = "Болело колено",
        )
        return HistoryUiState(
            details = HistoryWorkoutDetails(runtime, plan, emptyList(), listOf(fact), emptyList()),
            loading = false,
        )
    }
}
