package ru.sportzal.app.data.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copies picker content into app-owned storage; only relative managed paths leave this class. */
class EquipmentPhotoStore(
    context: Context,
    private val resolver: ContentResolver = context.contentResolver,
    private val root: File = context.filesDir,
    private val newName: () -> String = { "${UUID.randomUUID()}.img" },
) {
    suspend fun copy(uri: Uri): String = withContext(Dispatchers.IO) {
        val directory = File(root, DIRECTORY).also { check(it.exists() || it.mkdirs()) }
        val destination = File(directory, newName())
        try {
            resolver.openInputStream(uri).use { input ->
                checkNotNull(input) { "Unable to read selected photo" }
                destination.outputStream().use(input::copyTo)
            }
            check(destination.isFile && destination.length() > 0) { "Selected photo is empty" }
            "$DIRECTORY/${destination.name}"
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    fun resolve(relativePath: String): File? {
        if (!relativePath.startsWith("$DIRECTORY/") || relativePath.contains("..")) return null
        return File(root, relativePath).takeIf { it.isFile }
    }

    suspend fun delete(relativePath: String?) = withContext(Dispatchers.IO) {
        relativePath?.let(::resolve)?.delete()
        Unit
    }

    companion object { const val DIRECTORY = "equipment_photos" }
}
