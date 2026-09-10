package ru.sportzal.app.data.files

import android.content.*
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SnapshotShareCoordinator {
    suspend fun prepareAndShare(focusWorkoutId: String?)
    suspend fun saveAs(focusWorkoutId: String?)
}

class AndroidSnapshotShareCoordinator(
    private val context: Context, private val exporter: SnapshotExporter,
    private val launchSave: (Intent) -> Unit,
) : SnapshotShareCoordinator {
    private var pending: ExportedSnapshot? = null
    override suspend fun prepareAndShare(focusWorkoutId: String?) {
        val export = exporter.export(focusWorkoutId)
        val file = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > SEVEN_DAYS }?.forEach { it.delete() }
            File(directory, export.filename).apply { writeText(export.json, Charsets.UTF_8) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = shareIntent(uri)
        context.startActivity(Intent.createChooser(send, "Отправить JSON").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    override suspend fun saveAs(focusWorkoutId: String?) {
        val export = exporter.export(focusWorkoutId); pending = export
        launchSave(saveIntent(export.filename))
    }
    suspend fun writePending(uri: Uri?) { if (uri == null) return
        val export = pending ?: return
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(export.json); it.flush() } ?: error("Не удалось открыть файл") }
        pending = null
    }
    companion object {
        const val JSON_MIME = "application/json"; const val SEVEN_DAYS = 7L * 24 * 60 * 60 * 1000
        fun shareIntent(uri: Uri) = Intent(Intent.ACTION_SEND).setType(JSON_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        fun saveIntent(filename: String) = Intent(Intent.ACTION_CREATE_DOCUMENT).setType(JSON_MIME)
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, filename)
    }
}
