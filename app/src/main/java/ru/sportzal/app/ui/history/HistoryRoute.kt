package ru.sportzal.app.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HistoryRoute(vm: HistoryViewModel, state: HistoryUiState, onShare: () -> Unit,
    preparing: Boolean = false, onSaveFile: (() -> Unit)? = null, onAiHelp: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    if (state.details == null) HistoryScreen(state, { scope.launch { vm.load() } }, { scope.launch { vm.open(it) } })
    else HistoryDetailScreen(state, vm::close,
        onNote = { text, done -> scope.launch { done(vm.saveNote(text)) } },
        onEdit = { fact, weight, reps, rir, note, done -> scope.launch { done(vm.editSet(fact, weight, reps, rir, note)) } },
        onDelete = { id, done -> scope.launch { done(vm.deleteSet(id)) } }, onShare = onShare,
        preparing = preparing, onSaveFile = onSaveFile, onAiHelp = onAiHelp,
        onEditActual = { fact, weight, reps, rir, deviations, note, context, done -> scope.launch {
            done(vm.editSet(fact, weight, reps, rir, deviations, note, context))
        } })
}
