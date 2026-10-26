package ru.sportzal.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.sportzal.app.AppContainer
import ru.sportzal.app.R
import ru.sportzal.app.domain.TodaySelection
import ru.sportzal.app.model.CancelEmptyResult
import ru.sportzal.app.model.VisualAssignments
import ru.sportzal.app.ui.components.*
import ru.sportzal.app.ui.equipment.*
import ru.sportzal.app.ui.history.*
import ru.sportzal.app.ui.media.*
import ru.sportzal.app.ui.navigation.InactiveDestination
import ru.sportzal.app.ui.today.*
import ru.sportzal.app.ui.workout.*

@Composable
fun SportzalApp(container: AppContainer, incomingUri: Uri?, consumeIncoming: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope { Dispatchers.Main.immediate }
    val today = remember { TodayViewModel(container.database, container.repository, container.programImporter, container.workoutService) }
    val workout = remember { WorkoutViewModel(container.repository, container.clockProvider) }
    val history = remember { HistoryViewModel(container.repository) }
    val equipment = remember { EquipmentViewModel(container.repository, container.equipmentPhotoStore) }
    val todayState by today.state.collectAsState()
    val workoutState by workout.state.collectAsState()
    val historyState by history.state.collectAsState()
    val equipmentState by equipment.state.collectAsState()
    val visualFlow = remember(container.visuals) { container.visuals.observe() }
    val assignments by visualFlow.collectAsState(VisualAssignments())
    val exchange = rememberFileExchangeActions(container.snapshotShareCoordinator)
    val positions = remember { context.getSharedPreferences("workout-position", Context.MODE_PRIVATE) }
    var destinationName by rememberSaveable { mutableStateOf(InactiveDestination.TODAY.name) }
    val destination = InactiveDestination.valueOf(destinationName)
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var finishId by rememberSaveable { mutableStateOf<String?>(null) }
    var planOverview by rememberSaveable { mutableStateOf(false) }
    var cancelEmpty by remember { mutableStateOf(false) }
    var leavingFailed by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var openAttempt by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { today.preview(uri) }
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            workout.onForegroundReturn()
            scope.launch { today.refresh() }
        } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(destination) { when (destination) {
        InactiveDestination.TODAY -> today.refresh()
        InactiveDestination.HISTORY -> history.load()
        InactiveDestination.EQUIPMENT -> equipment.load()
    } }
    LaunchedEffect(activeId, finishId, openAttempt) {
        if (activeId == null && finishId == null) return@LaunchedEffect
        opening = true
        try {
            if (finishId != null) workout.restoreFinish(finishId!!)
            else activeId?.let { workout.open(it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { workout.showError("Не удалось открыть тренировку. Повторите попытку.") }
        finally { opening = false }
    }
    LaunchedEffect(workoutState.finishSummary) {
        if (workoutState.finishSummary != null) finishId = workoutState.workoutId
    }
    LaunchedEffect(incomingUri) { incomingUri?.let {
        val result = container.fileIntentHandler.handle(it)
        withContext(Dispatchers.Main.immediate) {
            today.showFileResult(result)
            consumeIncoming()
        }
    } }
    fun focusExercise(id: String?) {
        focusedId = id
        activeId?.let { positions.edit().putString("exercise-$it", id).apply() }
    }
    fun closeFinish() {
        workout.dismissFinish(); finishId = null; activeId = null; focusedId = null
        destinationName = InactiveDestination.TODAY.name
        scope.launch { today.refresh() }
    }
    fun workoutBack() {
        scope.launch {
            if (workout.flushDrafts()) {
                if (focusedId != null) focusExercise(null)
                else { activeId = null; today.refresh() }
            } else if (!workoutState.saving) leavingFailed = true
        }
    }
    BackHandler(activeId != null || finishId != null || planOverview || todayState.preview != null || todayState.readingFile || historyState.details != null) {
        when {
            todayState.preview != null || todayState.readingFile -> today.dismissPreview()
            finishId != null -> closeFinish()
            activeId != null -> workoutBack()
            planOverview -> planOverview = false
            historyState.details != null -> history.close()
        }
    }
    if (cancelEmpty) AlertDialog(onDismissRequest = { cancelEmpty = false }, title = { Text("Отменить начало?") },
        text = { Text("Пустая попытка будет удалена. Эту тренировку можно будет начать снова.") },
        dismissButton = { TextButton({ cancelEmpty = false }) { Text("Остаться") } },
        confirmButton = { TextButton({ scope.launch {
            runCatching { container.workoutService.cancelAccidentalStart(activeId!!) }.onSuccess { result ->
                when (result) {
                    CancelEmptyResult.Cancelled -> { activeId = null; focusedId = null; today.refresh() }
                    else -> workout.showError("В тренировке уже есть записи. Завершите её обычным способом.")
                }
            }.onFailure { workout.showError("Не удалось отменить начало. Попробуйте ещё раз.") }
            cancelEmpty = false
        } }) { Text("Отменить начало") } })
    if (leavingFailed) AlertDialog(onDismissRequest = { leavingFailed = false }, title = { Text("Ввод не сохранён") },
        text = { Text("Записанные подходы сохранены. Можно остаться и повторить изменение или уйти без последних изменений ввода.") },
        dismissButton = { TextButton({ leavingFailed = false }) { Text("Остаться") } },
        confirmButton = { TextButton({ leavingFailed = false; activeId = null; focusedId = null; scope.launch { today.refresh() } }) { Text("Уйти без изменений") } })

    CompositionLocalProvider(LocalVisualBindings provides VisualBindings(assignments)) {
        Column(Modifier.fillMaxSize()) {
            exchange.error?.let { StatusNotice(it, error = true, actionLabel = "Закрыть сообщение", onAction = exchange.clearError) }
            Box(Modifier.weight(1f)) {
                when {
                    todayState.preview != null || todayState.readingFile -> ProgramImportScreen(todayState.preview, todayState.readingFile,
                        todayState.busy, todayState.message, today::dismissPreview,
                        { picker.launch(arrayOf("application/json", "text/json", "text/plain")) },
                        { valid -> scope.launch { today.confirmImport(valid) } })
                    finishId != null && workoutState.finishSummary != null -> FinishScreen(workoutState.finishSummary!!,
                        workoutState.error, exchange.preparing, workoutState.notes,
                        { text, done -> scope.launch { done(workout.updateNotes(text)) } },
                        { exchange.share(finishId) }, { exchange.save(finishId) }, ::closeFinish)
                    finishId != null -> if (opening || workoutState.error == null) LoadingState("Загрузка итогов")
                        else OpenFailure(workoutState.error!!, { openAttempt++ }, ::closeFinish)
                    activeId != null -> if (workoutState.workoutId != activeId) {
                        if (opening || workoutState.error == null) LoadingState("Открываем тренировку")
                        else OpenFailure(workoutState.error!!, { openAttempt++ }, { activeId = null; focusedId = null; scope.launch { today.refresh() } })
                    } else WorkoutRoute(workout, workoutState, focusedId, ::workoutBack, ::focusExercise, { cancelEmpty = true }, container.visuals)
                    planOverview && todayState.plannedWorkout != null -> PlannedWorkoutScreen(todayState.plannedWorkout!!,
                        todayState.equipment, todayState.busy, { planOverview = false }, { scope.launch {
                            today.start()?.let { id -> planOverview = false; focusedId = null; activeId = id }
                        } }, error = todayState.message)
                    else -> Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { NavigationBar {
                        InactiveDestination.entries.forEach { item -> NavigationBarItem(selected = item == destination,
                            onClick = { destinationName = item.name }, icon = {
                                Icon(painterResource(when (item) {
                                    InactiveDestination.TODAY -> R.drawable.sz_ic_today
                                    InactiveDestination.HISTORY -> R.drawable.sz_ic_history
                                    InactiveDestination.EQUIPMENT -> R.drawable.sz_ic_equipment
                                }), null)
                            }, label = { Text(item.label) }) }
                    } }) { padding -> Box(Modifier.padding(padding)) { when (destination) {
                        InactiveDestination.TODAY -> TodayScreen(todayState.selection, todayState.manualChoices, null,
                            todayState.message, { picker.launch(arrayOf("application/json", "text/json", "text/plain")) },
                            { valid -> scope.launch { today.confirmImport(valid) } },
                            { id -> scope.launch { today.chooseWorkout(id) } }, { planOverview = true },
                            { (todayState.selection as? TodaySelection.Resume)?.workout?.workoutId?.let { id ->
                                focusedId = positions.getString("exercise-$id", null); activeId = id
                            } }, { exchange.share(null) }, exchange.preparing,
                            plannedWorkout = todayState.plannedWorkout, equipment = todayState.equipment,
                            loading = todayState.loading, busy = todayState.busy, onSaveFile = { exchange.save(null) },
                            error = todayState.error, onRetry = { scope.launch { today.refresh() } },
                            onEquipment = { destinationName = InactiveDestination.EQUIPMENT.name })
                        InactiveDestination.HISTORY -> HistoryRoute(history, historyState) { exchange.share(historyState.details?.runtime?.workoutId) }
                        InactiveDestination.EQUIPMENT -> EquipmentScreen(equipmentState, container.equipmentPhotoStore,
                            { scope.launch { equipment.load() } },
                            { command, done -> scope.launch { done(equipment.save(command) != null) } },
                            { item, uri -> scope.launch { equipment.replacePhoto(item, uri) } },
                            { item -> scope.launch { equipment.removePhoto(item) } })
                    } } }
                }
            }
        }
    }
}
