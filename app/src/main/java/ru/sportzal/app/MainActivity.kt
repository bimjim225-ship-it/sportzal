package ru.sportzal.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import ru.sportzal.app.ui.theme.SportzalTheme
import ru.sportzal.app.ui.today.TodayScreen
import ru.sportzal.app.ui.today.TodayViewModel
import ru.sportzal.app.ui.workout.WorkoutScreen
import ru.sportzal.app.ui.workout.WorkoutViewModel
import ru.sportzal.app.ui.workout.FinishScreen
import ru.sportzal.app.ui.history.*
import ru.sportzal.app.ui.equipment.*
import ru.sportzal.app.ui.navigation.InactiveDestination

class MainActivity : ComponentActivity() {
    private var displayedFinishWorkoutId by mutableStateOf<String?>(null)
    private var exportPreparing by mutableStateOf(false)
    private var incomingUri by mutableStateOf<Uri?>(null)
    private var pickedUri by mutableStateOf<Uri?>(null)
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { pickedUri = it }
    private var saveDestination by mutableStateOf<Uri?>(null)
    private val savePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        exportPreparing = false
        if (it.resultCode == RESULT_OK) saveDestination = it.data?.data
        else (application as SportzalApplication).container.snapshotShareCoordinator.cancelSave()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayedFinishWorkoutId = savedInstanceState?.getString(FINISH_WORKOUT_ID)
        val container = (application as SportzalApplication).container
        if (intent?.action == Intent.ACTION_VIEW) incomingUri = intent.data
        val viewModel = TodayViewModel(container.database, container.repository, container.programImporter, container.workoutService)
        setContent {
            SportzalTheme {
                val scope = rememberCoroutineScope()
                val state by viewModel.state.collectAsState()
                val workoutViewModel = remember { WorkoutViewModel(container.repository, container.clockProvider) }
                val historyViewModel = remember { HistoryViewModel(container.repository) }
                val equipmentViewModel = remember { EquipmentViewModel(container.repository, container.equipmentPhotoStore) }
                val historyState by historyViewModel.state.collectAsState()
                val equipmentState by equipmentViewModel.state.collectAsState()
                var destinationName by rememberSaveable { mutableStateOf(InactiveDestination.TODAY.name) }
                val destination = InactiveDestination.valueOf(destinationName)
                val workoutState by workoutViewModel.state.collectAsState()
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, workoutViewModel) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) workoutViewModel.onForegroundReturn()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                var activeWorkoutId by remember { mutableStateOf(displayedFinishWorkoutId) }
                LaunchedEffect(Unit) { viewModel.refresh(); historyViewModel.load(); equipmentViewModel.load() }
                LaunchedEffect(incomingUri) {
                    incomingUri?.let { destinationName = InactiveDestination.TODAY.name; viewModel.showFileResult(container.fileIntentHandler.handle(it)) }
                }
                LaunchedEffect(saveDestination) { saveDestination?.let {
                    if (!container.snapshotShareCoordinator.writePending(it)) {
                        workoutViewModel.showError("Не удалось сохранить JSON")
                    }
                    saveDestination = null
                } }
                LaunchedEffect(displayedFinishWorkoutId) {
                    displayedFinishWorkoutId?.let { id ->
                        activeWorkoutId = id
                        workoutViewModel.restoreFinish(id)
                    }
                }
                LaunchedEffect(workoutState.finishSummary) {
                    if (workoutState.finishSummary != null) displayedFinishWorkoutId = workoutState.workoutId
                }
                LaunchedEffect(state.selection) {
                    val id = (state.selection as? ru.sportzal.app.domain.TodaySelection.Resume)?.workout?.workoutId
                    if (id != null) { activeWorkoutId = id; workoutViewModel.open(id) }
                }
                LaunchedEffect(pickedUri) {
                    pickedUri?.let { viewModel.preview(it) }
                }
                if (workoutState.finishSummary != null) FinishScreen(
                    summary = workoutState.finishSummary!!,
                    error = workoutState.error,
                    preparing = exportPreparing,
                    onShare = { scope.launch {
                        if (exportPreparing) return@launch
                        exportPreparing = true
                        try {
                            val send = container.snapshotShareCoordinator.shareIntent(activeWorkoutId)
                            startActivity(Intent.createChooser(send, "Отправить JSON"))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            workoutViewModel.showError("Не удалось подготовить JSON")
                        } finally {
                            exportPreparing = false
                        }
                    } },
                    onSave = { scope.launch {
                        if (exportPreparing) return@launch
                        exportPreparing = true
                        try {
                            savePicker.launch(container.snapshotShareCoordinator.createSaveIntent(activeWorkoutId))
                        } catch (cancelled: CancellationException) {
                            exportPreparing = false
                            throw cancelled
                        } catch (_: Exception) {
                            exportPreparing = false
                            workoutViewModel.showError("Не удалось подготовить JSON")
                        }
                    } },
                    onClose = {
                        workoutViewModel.dismissFinish()
                        displayedFinishWorkoutId = null
                        activeWorkoutId = null
                        destinationName = InactiveDestination.TODAY.name
                        scope.launch { viewModel.refresh() }
                    },
                    notes = workoutState.notes,
                    onNote = { text, done -> scope.launch { done(workoutViewModel.updateNotes(text)) } },
                ) else if (activeWorkoutId != null) WorkoutScreen(
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
                    onFinish = { scope.launch { workoutViewModel.requestFinish() } },
                    onConfirmFinish = { scope.launch { workoutViewModel.confirmFinish() } },
                    onCancelFinish = workoutViewModel::cancelFinish,
                    onNote = { text, done -> scope.launch { done(workoutViewModel.updateNotes(text)) } },
                    photoStore = container.equipmentPhotoStore,
                ) else Scaffold(bottomBar = { NavigationBar {
                    InactiveDestination.entries.forEach { item -> NavigationBarItem(selected = item == destination,
                        onClick = { destinationName = item.name }, icon = { Text(when (item) { InactiveDestination.TODAY -> "●"; InactiveDestination.HISTORY -> "≡"; InactiveDestination.EQUIPMENT -> "□" }) },
                        label = { Text(item.label) }) }
                } }) { padding -> androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(padding)) {
                    when (destination) {
                InactiveDestination.TODAY -> TodayScreen(
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
                    onShare = { scope.launch {
                        if (exportPreparing) return@launch
                        exportPreparing = true
                        try { startActivity(Intent.createChooser(container.snapshotShareCoordinator.shareIntent(null), "Отправить JSON")) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { viewModel.showFileResult(ru.sportzal.app.platform.FileIntentResult.Message("Не удалось подготовить JSON")) }
                        finally { exportPreparing = false }
                    } }, preparing = exportPreparing,
                )
                InactiveDestination.HISTORY -> if (historyState.details == null) HistoryScreen(historyState,
                    { scope.launch { historyViewModel.load() } }, { scope.launch { historyViewModel.open(it) } })
                else HistoryDetailScreen(historyState, historyViewModel::close,
                    { text, done -> scope.launch { done(historyViewModel.saveNote(text)) } },
                    { fact, weight, reps, rir, note, done -> scope.launch { done(historyViewModel.editSet(fact, weight, reps, rir, note)) } },
                    { id, done -> scope.launch { done(historyViewModel.deleteSet(id)) } },
                    { scope.launch {
                        if (exportPreparing) return@launch; exportPreparing = true
                        try { startActivity(Intent.createChooser(container.snapshotShareCoordinator.shareIntent(historyState.details!!.runtime.workoutId), "Отправить JSON")) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { historyViewModel.showError("Не удалось подготовить JSON") }
                        finally { exportPreparing = false }
                    } })
                InactiveDestination.EQUIPMENT -> EquipmentScreen(equipmentState, container.equipmentPhotoStore,
                    { scope.launch { equipmentViewModel.load() } }, { command, done -> scope.launch { done(equipmentViewModel.save(command) != null) } },
                    { item, uri -> scope.launch { equipmentViewModel.replacePhoto(item, uri) } },
                    { item -> scope.launch { equipmentViewModel.removePhoto(item) } })
                    }
                } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) incomingUri = intent.data
    }

    override fun onSaveInstanceState(outState: Bundle) {
        displayedFinishWorkoutId?.let { outState.putString(FINISH_WORKOUT_ID, it) }
        super.onSaveInstanceState(outState)
    }

    private companion object { const val FINISH_WORKOUT_ID = "finish_workout_id" }
}
