package ru.sportzal.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.data.files.EquipmentPhotoStore
import ru.sportzal.app.data.files.ImportPreview
import ru.sportzal.app.domain.*
import ru.sportzal.app.model.*
import ru.sportzal.app.ui.components.RirSelector
import ru.sportzal.app.ui.components.AppSafeArea
import ru.sportzal.app.ui.equipment.*
import ru.sportzal.app.ui.media.*
import ru.sportzal.app.ui.theme.SportzalTheme
import ru.sportzal.app.ui.today.*
import ru.sportzal.app.ui.workout.*

/** Regressions for tasks which were possible but hidden behind long content or unexplained terms. */
class UxClarityTest {
    @get:Rule val compose = createComposeRule()

    private fun show(fontScale: Float = 1f, content: @Composable () -> Unit) = compose.setContent {
        SportzalTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Box(Modifier.size(320.dp, 640.dp)) { content() }
            }
        }
    }

    @Test fun firstLaunchStartsWithEquipmentAndStillAllowsAnExistingFile() {
        var equipment = 0
        var imports = 0
        show {
            TodayScreen(TodaySelection.NoProgram, emptyList(), null, null, { imports++ }, {}, {}, {}, {},
                loading = false, onEquipment = { equipment++ }, onAiHelp = {})
        }
        compose.onNodeWithTag("first-plan-action").assertIsDisplayed()
            .assert(hasText("Добавить тренажёры")).performClick()
        assertEquals(1, equipment)
        assertEquals(0, imports)
        scrollTo("Загрузить план от ИИ")
        compose.onNodeWithText("Загрузить план от ИИ").performClick()
        assertEquals(1, imports)
    }

    @Test fun existingEquipmentMakesPreparingTheAiRequestTheNextAction() {
        var help = 0
        var imports = 0
        show {
            TodayScreen(TodaySelection.NoProgram, emptyList(), null, null, { imports++ }, {}, {}, {}, {},
                equipment = listOf(EquipmentDocument("machine", "Тяга", null)), loading = false,
                onEquipment = {}, onAiHelp = { help++ })
        }
        compose.onNodeWithTag("first-plan-action").assertIsDisplayed()
            .assert(hasText("Получить план с ИИ")).performClick()
        assertEquals(1, help)
        assertEquals(0, imports)
    }

    @Test fun startStaysVisibleAcrossALongPlanAtLargeFont() {
        var starts = 0
        show(2f) { PlannedWorkoutScreen(longPlan(), emptyList(), false, {}, { starts++ }) }
        compose.onNodeWithText("Начать тренировку").assertIsDisplayed()
        scrollTo("Упражнение 20")
        compose.onNodeWithText("Упражнение 20").assertIsDisplayed()
        compose.onNodeWithText("Начать тренировку").assertIsDisplayed().performClick()
        assertEquals(1, starts)
    }

    @Test fun acceptingALongProgramDoesNotRequireScrollingToItsEnd() {
        var accepts = 0
        val program = ProgramDocument("sportzal.program", 1, "long", 1, "2026-10-26T10:00:00Z",
            emptyList(), listOf(longPlan()))
        val preview = ImportPreview.Valid(program, "{}", "hash")
        show { ProgramImportScreen(preview, false, false, null, {}, {}, { accepts++ }) }
        compose.onNodeWithText("Принять план").assertIsDisplayed()
        scrollTo("Упражнение 20")
        compose.onNodeWithText("Принять план").assertIsDisplayed().performClick()
        assertEquals(1, accepts)
    }

    @Test fun finishShareStaysVisibleWithManyExercisesAndCommitsTheVisibleNote() {
        var sent = 0
        var committed: String? = null
        show {
            FinishScreen(FinishSummary(3600, 40, (1..20).map { ExerciseFinishSummary("Упражнение $it", 2) }, false),
                null, onNote = { text, done -> committed = text; done(true) },
                onShare = { sent++ }, onSave = {}, onClose = {})
        }
        compose.onNodeWithText("Передать отчёт ИИ").assertIsDisplayed()
        compose.onNodeWithText("Как прошла тренировка").performScrollTo().performTextInput("Устал к концу")
        compose.onNodeWithText("Передать отчёт ИИ").assertIsDisplayed().performClick()
        assertEquals("Устал к концу", committed)
        assertEquals(1, sent)
    }

    @Test fun failedFinishNoteRemainsVisibleAndPreventsSharing() {
        val error = mutableStateOf<String?>(null)
        var sent = 0
        show {
            FinishScreen(FinishSummary(3600, 40, (1..20).map { ExerciseFinishSummary("Упражнение $it", 2) }, false),
                error.value, onNote = { _, done -> error.value = "Не удалось сохранить заметку"; done(false) },
                onShare = { sent++ }, onSave = {}, onClose = {})
        }
        compose.onNodeWithText("Как прошла тренировка").performScrollTo().performTextInput("Мой комментарий")
        compose.onNodeWithText("Передать отчёт ИИ").assertIsDisplayed().performClick()
        compose.onNodeWithText("Не удалось сохранить заметку").assertIsDisplayed()
        assertEquals(0, sent)
        compose.onNodeWithText("Мой комментарий").assertExists()
    }

    @Test fun catalogAddRemainsVisibleAfterScrollingALongList() {
        val items = (1..60).map { EquipmentCatalogItem("e$it", "Тренажёр $it", null, null, null, null, null) }
        show {
            EquipmentScreen(EquipmentUiState(items, loading = false),
                EquipmentPhotoStore(ApplicationProvider.getApplicationContext<Context>()), {}, { _, done -> done(true) }, { _, _ -> }, {})
        }
        compose.onNodeWithText("Добавить тренажёр").assertIsDisplayed()
        scrollTo("Тренажёр 60")
        compose.onNodeWithText("Тренажёр 60").assertIsDisplayed()
        compose.onNodeWithText("Добавить тренажёр").assertIsDisplayed().performClick()
        compose.onNodeWithText("Название").assertIsDisplayed()
    }

    @Test fun reserveHelpExplainsTheScaleWithoutAnsweringForTheUser() {
        val answer = mutableStateOf<Int?>(null)
        val answered = mutableStateOf(false)
        show { RirSelector(answer.value, answered.value) { answer.value = it; answered.value = true } }
        compose.onNodeWithText("Что это?").assertIsDisplayed().performClick()
        compose.onNodeWithText("0 — ни одного").assertIsDisplayed()
        compose.onNodeWithText("4+ — четыре или больше").assertIsDisplayed()
        compose.onNodeWithText("Понятно").performClick()
        assertFalse(answered.value)
        compose.onNodeWithTag("rir-2").performClick().assertIsSelected()
        assertTrue(answered.value)
        assertEquals(2, answer.value)
    }

    @Test fun setupDiagramExplainsTheMarkedPartsAndKeepsPersonalPositionsAsText() {
        show {
            CompositionLocalProvider(LocalVisualBindings provides VisualBindings(equipment = mapOf("machine" to "row"))) {
                ExerciseMediaPanel("row", "machine", "Сиденье 4 · Упор 2", null, false) { _, done -> done(true) }
            }
        }
        compose.onNodeWithText("А — Сиденье").assertExists()
        compose.onNodeWithText("Б — Упор").assertExists()
        compose.onNodeWithText("В — Выбор веса").assertExists()
        compose.onNodeWithText("Сиденье 4 · Упор 2").assertExists()
    }

    @Test fun differingPrescriptionsRemainAvailableBehindAnExplicitDisclosure() {
        val exercise = longPlan().blocks.first().exercises.single().copy(plannedSets = listOf(
            PlannedSetDocument(1, "warmup", 20.0, 12, 12, null, 60),
            PlannedSetDocument(2, "work", 35.0, 8, 10, 2, 90),
            PlannedSetDocument(3, "work", 40.0, 6, 8, 1, 120)))
        val plan = longPlan().copy(blocks = listOf(BlockDocument("one", "Блок", "straight", listOf(exercise))))
        show { PlannedWorkoutScreen(plan, emptyList(), false, {}, {}) }
        scrollTo("Все подходы (3)")
        compose.onNodeWithText("Все подходы (3)").performClick()
        listOf("Разминка 1: 20 кг · 12–12 · Пауза 1:00",
            "Рабочий 2: 35 кг · 8–10 · Запас: 2 · Пауза 1:30",
            "Рабочий 3: 40 кг · 6–8 · Запас: 1 · Пауза 2:00").forEach {
            scrollTo(it)
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("Начать тренировку").assertIsDisplayed()
    }

    @Test fun quickDiagramUsesTheActualEquipmentAfterReplacement() {
        val exercise = longPlan().blocks.first().exercises.single()
        val actual = ActualContext("lat_pulldown", "other", "Упор для ног 3", "machine_display", "bilateral", title = "Тяга верхнего блока")
        val slot = PlannedSlot(1, "work", 35.0, 10, 12, 2, 90,
            ActualContext(exercise.exerciseId, null, null, "machine_display", "bilateral"))
        val state = WorkoutUiState("active", "План", listOf(BlockUiState("block", "Блок", "straight",
            listOf(ExerciseUiState(exercise, slot, SetDraft(null, null, null, actual), emptyList())))))
        show {
            CompositionLocalProvider(LocalVisualBindings provides VisualBindings(equipment = mapOf("other" to "lat_pulldown"))) {
                WorkoutScreen(state, { "" }, { _, _, _, _, _ -> }, { _, _, _, _, _, _ -> }, { _, _, _ -> }, {}, focusedExerciseId = exercise.exerciseInstanceId)
            }
        }
        compose.onNodeWithText("Схема").assertIsDisplayed().performClick()
        compose.onNodeWithText("Упор для ног 3").assertExists()
        compose.onNodeWithText("Б — Упор для ног").assertExists()
        compose.onNodeWithText("Закрыть").performClick()
        scrollTo("Записать подход")
        compose.onNodeWithTag("save-${exercise.exerciseInstanceId}").assertIsDisplayed()
    }

    @Test fun longNoteErrorKeepsShareAndEditableNoteReachableWithImeAndLargeFont() {
        val error = mutableStateOf<String?>(null)
        val message = "Не удалось сохранить заметку. ".repeat(40) + "Конец сообщения"
        var sends = 0
        showWithKeyboard {
            FinishScreen(FinishSummary(3600, 2, emptyList(), false), error.value,
                onNote = { _, done -> error.value = message; done(false) },
                onShare = { sends++ }, onSave = {}, onClose = {})
        }
        compose.onNodeWithText("Как прошла тренировка").performScrollTo().performTextInput("Моя заметка")
        compose.onNodeWithText("Передать отчёт ИИ").assertIsDisplayed().performClick()
        compose.onNodeWithText("Передать отчёт ИИ").assertIsDisplayed()
        compose.onNodeWithText("Моя заметка").performScrollTo().assertIsDisplayed()
        assertEquals(0, sends)
        compose.onNodeWithText(message).performClick()
        compose.onNodeWithText("Ошибка").assertIsDisplayed()
        compose.onNodeWithTag("action-error-detail").assertTextEquals(message)
        compose.onNodeWithText("Закрыть").assertIsDisplayed().performClick()
        compose.onNodeWithText("Передать отчёт ИИ").assertIsDisplayed()
    }

    @Test fun catalogSearchAndAddSurviveNavigationImeAndLargeFontTogether() {
        val items = (1..60).map { EquipmentCatalogItem("e$it", "Тренажёр $it", null, null, null, null, null) }
        showWithKeyboard {
            Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
                NavigationBar {
                    listOf("Сегодня", "История", "Тренажёры").forEach { title ->
                        NavigationBarItem(title == "Тренажёры", {}, icon = { Box(Modifier.size(24.dp)) }, label = { Text(title) })
                    }
                }
            }) { padding -> Box(Modifier.padding(padding)) {
                EquipmentScreen(EquipmentUiState(items, loading = false),
                    EquipmentPhotoStore(ApplicationProvider.getApplicationContext<Context>()), {}, { _, done -> done(true) }, { _, _ -> }, {})
            } }
        }
        val query = compose.onNode(hasSetTextAction())
        query.performScrollTo().assertIsDisplayed()
        val clipped = query.getBoundsInRoot()
        val full = query.getUnclippedBoundsInRoot()
        assertTrue("Search input must not collapse behind the fixed header",
            clipped.bottom - clipped.top + 1.dp >= full.bottom - full.top)
        query.performTextInput("60")
        scrollTo("Тренажёр 60")
        compose.onNodeWithText("Тренажёр 60").assertIsDisplayed()
        compose.onNodeWithText("Добавить тренажёр").assertIsDisplayed()
    }

    @Test fun longExerciseTitleDoesNotCollapseTheFormWithImeAndLargeFont() {
        val title = "Тяга верхнего блока широким нейтральным хватом к верхней части груди"
        val exercise = longPlan().blocks.first().exercises.single().copy(title = title, rirCapture = "none")
        val context = ActualContext("row", null, "Сиденье 4", "machine_display", "bilateral")
        val slot = PlannedSlot(1, "work", 35.0, 10, 12, 2, 90, context)
        val state = WorkoutUiState("active", "План", listOf(BlockUiState("block", "Блок", "straight",
            listOf(ExerciseUiState(exercise, slot, SetDraft(35.0, 10, null, context), emptyList())))))
        var saves = 0
        showWithKeyboard {
            WorkoutScreen(state, { "" }, { _, _, _, _, _ -> }, { _, _, weight, _, _, _ ->
                assertEquals(37.5, weight!!, 0.0); saves++
            }, { _, _, _ -> }, {}, focusedExerciseId = exercise.exerciseInstanceId)
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasTestTag("weight-${exercise.exerciseInstanceId}"))
        compose.onNodeWithTag("weight-${exercise.exerciseInstanceId}")
            .performScrollTo().assertIsDisplayed().performTextReplacement("37,5")
        compose.onNodeWithTag("save-${exercise.exerciseInstanceId}").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, saves)
        compose.onNodeWithText("Схема").assertIsDisplayed()
        scrollTo(title)
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    private fun showWithKeyboard(content: @Composable () -> Unit) = show(2f) {
        AppSafeArea(safeDrawingInsets = WindowInsets(top = 24.dp, bottom = 24.dp),
            imeInsets = WindowInsets(bottom = 280.dp), content = content)
    }

    private fun scrollTo(label: String) {
        val lazy = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)
        if (compose.onAllNodes(lazy).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(lazy).performScrollToNode(hasText(label))
        }
        compose.onNodeWithText(label).performScrollTo()
    }

    private fun longPlan() = PlannedWorkoutDocument("session", "A", "Тренировка", "2026-10-26", (1..20).map { n ->
        BlockDocument("block-$n", "Блок $n", "straight", listOf(
            ExerciseDocument("exercise-$n", "row", "Упражнение $n", null, "Сиденье 4", "machine_display", "bilateral", 1,
                "all_work_sets", (1..3).map { PlannedSetDocument(it, "work", 35.0, 10, 12, 2, 90) })))
    })
}
