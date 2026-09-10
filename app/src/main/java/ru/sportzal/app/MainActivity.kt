package ru.sportzal.app

import android.net.Uri
import android.os.Bundle
import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.sportzal.app.ui.theme.SportzalTheme
import ru.sportzal.app.ui.today.TodayScreen
import ru.sportzal.app.ui.today.TodayViewModel
import ru.sportzal.app.ui.workout.WorkoutScreen
import ru.sportzal.app.ui.workout.WorkoutViewModel
import ru.sportzal.app.ui.workout.FinishScreen
import ru.sportzal.app.data.files.AndroidSnapshotShareCoordinator
import ru.sportzal.app.platform.FileOpenResult

class MainActivity : ComponentActivity() {
    private var pickedUri by mutableStateOf<Uri?>(null)
    private var openedUri by mutableStateOf<Uri?>(null)
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { pickedUri = it }
    private lateinit var shareCoordinator: AndroidSnapshotShareCoordinator
    private val saver = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { uri -> lifecycleScope.launch { shareCoordinator.writePending(uri) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedUri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        val container = (application as SportzalApplication).container
        shareCoordinator = AndroidSnapshotShareCoordinator(this, container.snapshotExporter) { saver.launch(it) }
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
                LaunchedEffect(openedUri) { openedUri?.let { uri ->
                    when (val result = container.fileIntentHandler.open(uri)) {
                        is FileOpenResult.Program -> viewModel.showPreview(result.preview)
                        is FileOpenResult.Error -> viewModel.showMessage(result.message)
                    }
                } }
                if (workoutState.finished != null) FinishScreen(workoutState.finished!!,
                    onShare = { scope.launch { runCatching { shareCoordinator.prepareAndShare(workoutState.finished!!.workoutId) }
                        .onFailure { workoutViewModel.showError(it.message ?: "Не удалось подготовить JSON") } } },
                    onSave = { scope.launch { runCatching { shareCoordinator.saveAs(workoutState.finished!!.workoutId) }
                        .onFailure { workoutViewModel.showError(it.message ?: "Не удалось подготовить JSON") } } },
                    onClose = { workoutViewModel.dismissFinish(); activeWorkoutId = null; scope.launch { viewModel.refresh() } }, error = workoutState.error)
                else if (activeWorkoutId != null) WorkoutScreen(
                    state = workoutState,
                    elapsedFor = { workoutViewModel.elapsedText(it) },
                    onDraft = { id, weight, reps, rir, answered -> scope.launch { workoutViewModel.updateDraft(id, weight, reps, rir, answered) } },
                    onSave = { id, setNo -> scope.launch { workoutViewModel.saveSet(id, setNo) } },
                    onEdit = { id, weight, reps, rir, deviations, note, completed -> scope.launch {
                        completed(workoutViewModel.editSet(id, weight, reps, rir, deviations, note)) } },
                    onDelete = { id, completed -> scope.launch { completed(workoutViewModel.deleteSet(id)) } },
                    onSkip = { id, setNo, reason, note, completed -> scope.launch {
                        completed(workoutViewModel.skipSet(id, setNo, reason, note)) } },
                    onRestore = { id, setNo -> scope.launch { workoutViewModel.restoreSkippedSet(id, setNo) } },
                    onContext = { id, context, completed -> scope.launch { completed(workoutViewModel.updateActualContext(id, context)) } },
                    onExtra = { id, type, weight, reps, rir, note, completed -> scope.launch {
                        completed(workoutViewModel.saveExtraSet(id, type, weight, reps, rir, note)) } },
                    onEditActual = { id, weight, reps, rir, deviations, note, context, completed -> scope.launch {
                        completed(workoutViewModel.editSet(id, weight, reps, rir, deviations, note, context)) } },
                    onInteraction = workoutViewModel::setInteraction,
                    onTick = workoutViewModel::tick,
                    onFinish = { confirmed -> scope.launch { workoutViewModel.finish(confirmed) } },
                    onCancelFinish = workoutViewModel::cancelFinish,
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openedUri = intent.takeIf { it.action == Intent.ACTION_VIEW }?.data
    }
}
