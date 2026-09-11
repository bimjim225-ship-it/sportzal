package ru.sportzal.app.data.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SnapshotShareCoordinator(private val context: Context, private val exporter: SnapshotExporter) {
    private var pending: ExportedSnapshot? = null
    suspend fun shareIntent(focusWorkoutId: String?): Intent = withContext(Dispatchers.IO) {
        cleanup(); val snapshot = exporter.export(focusWorkoutId); val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, snapshot.filename).apply { writeText(snapshot.json, Charsets.UTF_8) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    suspend fun createSaveIntent(focusWorkoutId: String?): Intent = withContext(Dispatchers.IO) {
        pending = exporter.export(focusWorkoutId)
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
            putExtra(Intent.EXTRA_TITLE, pending!!.filename) }
    }
    suspend fun writePending(destination: Uri?): Boolean = withContext(Dispatchers.IO) {
        if (destination == null) return@withContext false
        val snapshot = pending ?: return@withContext false
        val success = runCatching { context.contentResolver.openOutputStream(destination)?.use {
            it.write(snapshot.json.toByteArray(Charsets.UTF_8)) } ?: error("Unable to open destination") }.isSuccess
        if (success) pending = null
        success
    }
    fun cancelSave() { pending = null }
    private fun cleanup() { val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        File(context.cacheDir, "exports").listFiles()?.filter { it.lastModified() < cutoff }?.forEach { runCatching { it.delete() } } }
}
