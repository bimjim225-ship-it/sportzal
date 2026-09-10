package ru.sportzal.app.platform

import android.net.Uri
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.sportzal.app.data.files.*
import ru.sportzal.app.model.StrictJson

sealed interface FileIntentResult {
    data class Program(val preview: ImportPreview) : FileIntentResult
    data class Message(val text: String) : FileIntentResult
}
class FileIntentHandler(private val reader: JsonFileReader, private val importer: ProgramImporter) {
    suspend fun handle(uri: Uri): FileIntentResult = when (val read = reader.read(uri)) {
        JsonReadResult.TooLarge -> FileIntentResult.Message("Файл больше 10 МиБ")
        JsonReadResult.InvalidUtf8 -> FileIntentResult.Message("Файл не является корректным UTF-8")
        JsonReadResult.Unreadable -> FileIntentResult.Message("Не удалось прочитать JSON")
        is JsonReadResult.Success -> classify(read.source)
    }
    private fun classify(source: String): FileIntentResult {
        val schema = runCatching { StrictJson.parseToJsonElement(source).jsonObject["schema"]?.jsonPrimitive?.content }
            .getOrNull() ?: return FileIntentResult.Message("Не удалось прочитать JSON")
        return when (schema) {
            "sportzal.program" -> FileIntentResult.Program(importer.preview(source))
            "sportzal.ai_snapshot" -> FileIntentResult.Message("Это файл результатов, а не программа")
            else -> FileIntentResult.Message("Неподдерживаемый файл Sportzal")
        }
    }
}
