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
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import ru.sportzal.app.ui.theme.SportzalTheme
import ru.sportzal.app.ui.today.TodayScreen
import ru.sportzal.app.ui.today.TodayViewModel
import ru.sportzal.app.ui.workout.WorkoutScreen
import ru.sportzal.app.ui.workout.WorkoutViewModel

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
                val workoutViewModel = remember { WorkoutViewModel(container.repository, container.clockProvider) }
                val workoutState by workoutViewModel.state.collectAsState()
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, workoutViewModel) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) workoutViewModel.onForegroundReturn()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                var activeWorkoutId by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) { viewModel.refresh() }
                LaunchedEffect(state.selection) {
                    val id = (state.selection as? ru.sportzal.app.domain.TodaySelection.Resume)?.workout?.workoutId
                    if (id != null) { activeWorkoutId = id; workoutViewModel.open(id) }
                }
                LaunchedEffect(pickedUri) {
                    pickedUri?.let { viewModel.preview(it) }
                }
                if (activeWorkoutId != null) WorkoutScreen(
                    state = workoutState,
                    elapsedFor = { workoutViewModel.elapsedText(it) },
                    onDraft = { id, weight, reps, rir, answered -> scope.launch { workoutViewModel.updateDraft(id, weight, reps, rir, answered) } },
                    onSave = { id, setNo -> scope.launch { workoutViewModel.saveSet(id, setNo) } },
                    onInteraction = workoutViewModel::setInteraction,
                    onTick = workoutViewModel::tick,
                ) else TodayScreen(
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
                    onResume = {
                        val id = (state.selection as? ru.sportzal.app.domain.TodaySelection.Resume)?.workout?.workoutId
                        if (id != null) { activeWorkoutId = id; scope.launch { workoutViewModel.open(id) } }
                    },
                )
            }
        }
    }
}
