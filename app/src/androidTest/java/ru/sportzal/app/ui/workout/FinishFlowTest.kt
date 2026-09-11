package ru.sportzal.app.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.ui.theme.SportzalTheme

class FinishFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longFinishKeepsEveryActionReachableAtTwoHundredPercentFontScale() {
        var share = 0
        var save = 0
        var close = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                SportzalTheme {
                    Box(Modifier.width(320.dp)) {
                        FinishScreen(
                            FinishSummary(3600, 24, (1..12).map {
                                ExerciseFinishSummary("Очень длинное название упражнения номер $it", 2)
                            }, endedEarly = true),
                            "Подробная ошибка, которая занимает несколько строк на узком экране",
                            onShare = { share++ },
                            onSave = { save++ },
                            onClose = { close++ },
                        )
                    }
                }
            }
        }

        listOf("Отправить JSON", "Сохранить JSON", "Закрыть").forEach { label ->
            compose.onNodeWithText(label).assertExists().performScrollTo().performClick()
        }
        assertEquals(listOf(1, 1, 1), listOf(share, save, close))
    }
}
