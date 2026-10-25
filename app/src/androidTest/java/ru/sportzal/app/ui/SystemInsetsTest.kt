package ru.sportzal.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.sportzal.app.ui.components.AppSafeArea

class SystemInsetsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun globalSafeAreaKeepsContentAboveNavigationAndImeInsetsOnCompactScreen() {
        compose.setContent {
            Box(Modifier.size(360.dp, 480.dp).testTag("window")) {
                AppSafeArea(
                    safeDrawingInsets = WindowInsets(0.dp, 24.dp, 0.dp, 48.dp),
                    imeInsets = WindowInsets(0.dp, 0.dp, 0.dp, 160.dp),
                ) { Box(Modifier.fillMaxSize().testTag("interactive-content")) }
            }
        }

        val window = compose.onNodeWithTag("window").getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag("interactive-content").getUnclippedBoundsInRoot()
        assertEquals(window.top + 24.dp, content.top)
        // Insets are consumed between modifiers, so keyboard and navigation padding do not stack.
        assertEquals(window.bottom - 160.dp, content.bottom)
    }
}
