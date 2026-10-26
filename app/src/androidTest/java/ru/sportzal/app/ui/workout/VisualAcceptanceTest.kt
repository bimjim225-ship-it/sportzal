package ru.sportzal.app.ui.workout

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.*
import ru.sportzal.app.data.db.EquipmentVisualEntity
import ru.sportzal.app.model.*
import java.io.ByteArrayOutputStream
import java.io.File

/** Real native screens and the complete import/log/export-ready cycle, using synthetic review data. */
class VisualAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as SportzalApplication).container

    @After fun cleanup() { runBlocking { container.database.clearAllTables() } }

    @Test fun nativeCycleRetainsActivePlanDuringNewImportAndCapturesReviewScreens() {
        compose.waitForIdle()
        runBlocking {
            container.database.clearAllTables()
            val initial = program(1)
            container.repository.importProgram(initial, StrictJson.encodeToString(initial), "visual-v1")
            machines.forEach { (key, _, _) ->
                container.database.visualDao().saveEquipment(EquipmentVisualEntity("eq-$key", key))
                container.visuals.saveTechnique(key, "eq-$key", key)
            }
        }
        compose.activityRule.scenario.recreate()
        await("Открыть тренировку")
        capture("01-today")
        compose.onNodeWithText("Открыть тренировку").performScrollTo().performClick()
        capture("02-plan")
        scrollTo("Начать тренировку"); compose.onNodeWithText("Начать тренировку").performClick()
        awaitTag("workout-overview")
        val activeId = runBlocking { container.database.dao().activeWorkout()!!.workoutId }
        capture("03-workout")
        compose.onNodeWithTag("open-row").performScrollTo().performClick()
        awaitTag("exercise-row")
        capture("04-setup")
        scrollTo("Техника"); compose.onNodeWithText("Техника").performClick()
        capture("05-technique")
        scrollTo("Не оценил"); compose.onNodeWithText("Не оценил").performClick()
        compose.onNodeWithTag("save-row").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { container.database.dao().setCount(activeId) == 1 } }
        scrollTo("К упражнениям"); compose.onNodeWithText("К упражнениям").performClick()
        awaitTag("workout-overview")

        val next = program(2)
        val exportDir = File(compose.activity.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "review-next-plan.json").apply { writeText(StrictJson.encodeToString(next)) }
        val uri = FileProvider.getUriForFile(compose.activity, "${compose.activity.packageName}.fileprovider", file)
        compose.activityRule.scenario.onActivity { activity ->
            activity.startActivity(Intent(activity, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW; setDataAndType(uri, "application/json")
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
        await("План готов к принятию")
        capture("06-import")
        assertEquals(1, runBlocking { container.database.dao().workout(activeId)!!.programVersion })
        scrollTo("Принять план"); compose.onNodeWithText("Принять план").performClick()
        awaitTag("workout-overview")
        assertEquals(1, runBlocking { container.database.dao().workout(activeId)!!.programVersion })
        assertEquals(2, runBlocking { container.database.dao().state()!!.activeProgramVersion })

        scrollTo("Завершить тренировку"); compose.onNodeWithText("Завершить тренировку").performClick()
        compose.onNodeWithText("Завершить").performClick()
        await("Тренировка завершена")
        capture("07-finish")
        compose.onNodeWithText("На главный экран").performScrollTo().performClick()
        compose.onNodeWithText("История").performClick()
        await("Тренировка A")
        capture("08-history")
        compose.onNodeWithText("Тренажёры").performClick()
        await("Горизонтальная тяга")
        capture("09-catalog")
        compose.onNodeWithText("Горизонтальная тяга").performScrollTo().performClick()
        await("Тренажёр")
        scrollTo("Моя настройка")
        capture("10-equipment")
        assertEquals(1, runBlocking { container.database.dao().setCount(activeId) })
        assertEquals("ended_early", runBlocking { container.database.dao().workout(activeId)!!.completionStatus })
    }

    private fun await(text: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitTag(tag: String) = compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun scrollTo(text: String) {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bytes = ByteArrayOutputStream()
        compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, bytes)
        val dir = File(compose.activity.filesDir, "review-screens").apply { mkdirs() }
        File(dir, "$name.png").writeBytes(bytes.toByteArray())
    }

    private val machines = listOf(
        Triple("row", "Горизонтальная тяга", "Сиденье 4 · Упор 2"),
        Triple("chest_press", "Жим от груди", "Сиденье 3"),
        Triple("leg_press", "Жим ногами", "Положение спинки 2"),
        Triple("lat_pulldown", "Тяга верхнего блока", "Упор для ног 3"),
        Triple("leg_curl", "Сгибание ног сидя", "Сиденье 2 · Валик 3"),
    )
    private fun program(version: Int): ProgramDocument {
        val equipment = machines.map { (key, title, setup) -> EquipmentDocument("eq-$key", title, setup, 2.5, listOf(10.0, 20.0, 35.0, 40.0)) }
        val exercises = machines.take(3).mapIndexed { index, (key, title, setup) ->
            ExerciseDocument(key, key, title, "eq-$key", setup, "machine_display", "bilateral", 1, "all_work_sets",
                (1..2).map { PlannedSetDocument(it, "work", listOf(35.0, 25.0, 70.0)[index], 10, 12, 2, 90) },
                notes = "Используйте персональную настройку из плана.")
        }
        return ProgramDocument("sportzal.program", 1, "visual-review", version, "2026-10-26T10:00:00Z", equipment,
            listOf(PlannedWorkoutDocument("visual-session-$version", "A", if (version == 1) "Тренировка A" else "Следующая тренировка",
                if (version == 1) "2026-10-26" else "2026-10-28",
                exercises.mapIndexed { index, exercise -> BlockDocument("block-$index", "Блок ${index + 1}", "straight", listOf(exercise)) },
                notes = "План от ИИ. Упражнения можно открыть в удобном порядке.")),
            coachNotes = "Проверьте состав перед принятием.")
    }
}
