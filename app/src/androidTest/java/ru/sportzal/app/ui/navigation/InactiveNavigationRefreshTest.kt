package ru.sportzal.app.ui.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.MainActivity
import ru.sportzal.app.SportzalApplication
import ru.sportzal.app.data.db.EquipmentEntity
import ru.sportzal.app.data.db.ProgramEntity
import ru.sportzal.app.data.db.WorkoutEntity
import ru.sportzal.app.model.PlannedWorkoutDocument
import ru.sportzal.app.model.StrictJson

class InactiveNavigationRefreshTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun enteringHistoryReloadsWorkoutsCreatedAfterInitialScreenLoad() = runBlocking {
        compose.waitForIdle()
        val container = (compose.activity.application as SportzalApplication).container
        val suffix = UUID.randomUUID().toString()
        val programId = "program-$suffix"
        val workoutId = "workout-$suffix"
        val title = "Свежая тренировка $suffix"
        val plan = PlannedWorkoutDocument("session-$suffix", "template", title, "2026-09-11", emptyList())
        container.database.dao().insertProgram(
            ProgramEntity(
                programId,
                1,
                1,
                "{}",
                "hash-$suffix",
                "2026-09-11T10:00:00Z",
                "2026-09-11T10:00:00Z",
            ),
        )
        container.database.dao().insertWorkout(
            WorkoutEntity(
                workoutId,
                programId,
                1,
                "session-$suffix",
                "template",
                "2026-09-11T10:00:00Z",
                "2026-09-11T10:30:00Z",
                "completed",
                StrictJson.encodeToString(plan),
                "[]",
                null,
            ),
        )

        compose.onNodeWithText("История").performClick()
        compose.waitForIdle()

        compose.onNodeWithText(title, substring = true).assertExists()
        Unit
    }

    @Test
    fun enteringEquipmentReloadsCatalogChangedAfterInitialScreenLoad() = runBlocking {
        compose.waitForIdle()
        val container = (compose.activity.application as SportzalApplication).container
        val suffix = UUID.randomUUID().toString()
        val name = "Новый тренажёр $suffix"
        container.database.dao().insertEquipment(
            EquipmentEntity(
                equipmentId = "equipment-$suffix",
                name = name,
                setupHint = "сиденье 4",
                weightStepKg = null,
                availableWeightsJson = null,
                notes = null,
                photoPath = null,
                updatedAt = "2026-09-11T10:00:00Z",
            ),
        )

        compose.onNodeWithText("Тренажёры").performClick()
        compose.waitForIdle()

        compose.onNodeWithText(name).assertExists()
        Unit
    }
}
