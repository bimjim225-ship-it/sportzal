package ru.sportzal.app
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.runner.RunWith
import ru.sportzal.app.ui.theme.SportzalTheme
@RunWith(AndroidJUnit4::class) class ThemeTest {@get:Rule val compose=createComposeRule();@Test fun themeCreatesAccessibleTouchTarget(){compose.setContent{SportzalTheme{Button(onClick={}){Text("Действие")}}};compose.onNodeWithText("Действие").assertHasClickAction().assertHeightIsAtLeast(48.dp)}}
