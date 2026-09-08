package ru.sportzal.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import ru.sportzal.app.ui.theme.SportzalTheme
import ru.sportzal.app.ui.today.TodayScreen
import ru.sportzal.app.ui.today.TodayViewModel

class MainActivity : ComponentActivity() {
    private var pickedUri by mutableStateOf<Uri?>(null)
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { pickedUri = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SportzalApplication).container
        val viewModel = TodayViewModel(container.database, container.repository, container.programImporter, container.workoutService)
        setContent {
            SportzalTheme {
                val scope = rememberCoroutineScope()
                val state by viewModel.state.collectAsState()
                LaunchedEffect(Unit) { viewModel.refresh() }
                LaunchedEffect(pickedUri) {
                    pickedUri?.let { viewModel.preview(it) }
                }
                TodayScreen(
                    selection = state.selection,
                    manualChoices = state.manualChoices,
                    preview = state.preview,
                    message = state.message,
                    onPickProgram = { picker.launch(arrayOf("application/json", "text/json", "text/plain")) },
                    onConfirmImport = { valid ->
                        scope.launch { viewModel.confirmImport(valid) }
                    },
                    onChooseWorkout = { scope.launch { viewModel.chooseWorkout(it) } },
                    onStart = { scope.launch { viewModel.start() } },
                    onResume = viewModel::resume,
                )
            }
        }
    }
}
