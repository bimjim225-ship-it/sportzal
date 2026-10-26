package ru.sportzal.app.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.sportzal.app.data.files.SnapshotShareCoordinator

data class FileExchangeActions(val preparing: Boolean, val error: String?,
    val share: (String?) -> Unit, val save: (String?) -> Unit, val clearError: () -> Unit)

/** One busy/error model for Today, finish, and history. Opening a chooser never claims delivery. */
@Composable
fun rememberFileExchangeActions(coordinator: SnapshotShareCoordinator): FileExchangeActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        scope.launch {
            try {
                if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
                    if (!coordinator.writePending(result.data?.data)) error = "Не удалось сохранить отчёт. Попробуйте ещё раз."
                } else coordinator.cancelSave()
            } finally { preparing = false }
        }
    }
    return FileExchangeActions(preparing, error,
        share = { focus ->
            if (!preparing) {
                preparing = true; error = null
                scope.launch {
                    try { context.startActivity(Intent.createChooser(coordinator.shareIntent(focus), "Передать отчёт ИИ")) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "Не удалось подготовить отчёт. Попробуйте ещё раз." }
                    finally { preparing = false }
                }
            }
        },
        save = { focus ->
            if (!preparing) {
                preparing = true; error = null
                scope.launch {
                    try { savePicker.launch(coordinator.createSaveIntent(focus)) }
                    catch (cancelled: CancellationException) { preparing = false; throw cancelled }
                    catch (_: Exception) { preparing = false; error = "Не удалось подготовить файл. Попробуйте ещё раз." }
                }
            }
        }, clearError = { error = null })
}
