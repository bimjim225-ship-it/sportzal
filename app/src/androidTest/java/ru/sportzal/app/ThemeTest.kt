package ru.sportzal.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.sportzal.app.ui.components.sportzalMinimumTouchTarget
import ru.sportzal.app.ui.theme.SportzalTheme

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun compactControlKeepsAccessibleTouchTarget() {
        compose.setContent {
            SportzalTheme {
                Box(
                    Modifier
                        .testTag("compact-action")
                        .clickable {}
                        .sportzalMinimumTouchTarget()
                        .size(40.dp),
                )
            }
        }

        compose.onNodeWithTag("compact-action")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }
}
