package ru.sportzal.app.platform

import android.content.ContentResolver
import android.net.Uri
import kotlinx.serialization.json.*
import ru.sportzal.app.data.files.ImportPreview
import ru.sportzal.app.data.files.ProgramImporter
import ru.sportzal.app.model.StrictJson

sealed interface FileOpenResult {
    data class Program(val preview: ImportPreview) : FileOpenResult
    data class Error(val message: String) : FileOpenResult
}
class FileIntentHandler(private val resolver: ContentResolver, private val importer: ProgramImporter) {
    suspend fun open(uri: Uri): FileOpenResult {
        val source = runCatching { resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }
            .getOrNull() ?: return FileOpenResult.Error("Не удалось прочитать JSON")
        val root = runCatching { StrictJson.parseToJsonElement(source).jsonObject }.getOrNull()
            ?: return FileOpenResult.Error("Не удалось прочитать JSON")
        return when (root["schema"]?.jsonPrimitive?.contentOrNull) {
            "sportzal.ai_snapshot" -> FileOpenResult.Error("Это файл результатов, а не программа")
            "sportzal.program" -> FileOpenResult.Program(importer.preview(source))
            else -> FileOpenResult.Error("Неподдерживаемый файл Sportzal")
        }
    }
}
