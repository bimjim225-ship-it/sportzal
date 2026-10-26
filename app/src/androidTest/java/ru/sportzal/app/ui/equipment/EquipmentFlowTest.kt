package ru.sportzal.app.ui.equipment

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.data.files.EquipmentPhotoStore
import ru.sportzal.app.model.EquipmentCatalogItem
import ru.sportzal.app.ui.theme.SportzalTheme

class EquipmentFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun catalogHasReadOnlyDetailsAndClearableSearch() {
        val item = EquipmentCatalogItem("machine", "Горизонтальная тяга", "Сиденье 4", 2.5, null, null, null, "row")
        compose.setContent { SportzalTheme { EquipmentScreen(EquipmentUiState(listOf(item), loading = false),
            EquipmentPhotoStore(ApplicationProvider.getApplicationContext<Context>()), {}, { _, done -> done(true) }, { _, _ -> }, {}) } }
        compose.onNodeWithText(item.name).performClick()
        compose.onNodeWithText("Моя настройка").assertExists()
        compose.onNodeWithText("Название").assertDoesNotExist()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText("Изменить настройки"))
        compose.onNodeWithText("Изменить настройки").assertIsDisplayed().performClick()
        compose.onNodeWithText("Название").assertExists()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Найти тренажёр").performTextInput("Несуществующий")
        compose.onNodeWithText("Ничего не найдено").assertExists()
        compose.onNodeWithContentDescription("Очистить поиск").performClick()
        compose.onNodeWithText(item.name).assertExists()
    }
}
