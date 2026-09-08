package ru.sportzal.app.data.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.model.ImportResult
import ru.sportzal.app.model.ProgramDocument

sealed interface ImportPreview {
    data class Valid internal constructor(
        val program: ProgramDocument,
        val canonicalJson: String,
        internal val canonicalHash: String,
    ) : ImportPreview
    data class Error(val message: String) : ImportPreview
}

class ProgramImporter(
    private val resolver: ContentResolver,
    private val validator: ProgramValidator,
    private val repository: SportzalRepository,
) {
    suspend fun import(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        if (name != null && name != FILE_NAME) {
            return@withContext ImportPreview.Error("Выберите файл $FILE_NAME")
        }
        val bytes = try {
            resolver.openInputStream(uri)?.use(::readLimited) ?: return@withContext ImportPreview.Error("Не удалось открыть файл")
        } catch (_: FileTooLargeException) {
            return@withContext ImportPreview.Error("Файл больше 10 МиБ")
        } catch (error: Exception) {
            return@withContext ImportPreview.Error("Не удалось прочитать файл: ${error.message ?: "неизвестная ошибка"}")
        }
        preview(bytes.toString(Charsets.UTF_8))
    }

    fun preview(source: String): ImportPreview =
        when (val result = validator.validate(source)) {
            is ValidationResult.Success -> ImportPreview.Valid(result.value, result.canonicalJson, result.canonicalHash)
            is ValidationResult.Failure -> ImportPreview.Error("Программа не импортирована: ${result.message}")
        }

    suspend fun confirm(preview: ImportPreview.Valid): ImportResult = repository.importProgram(
        preview.program, preview.canonicalJson, preview.canonicalHash,
    )

    private fun readLimited(stream: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            if (total > ProgramValidator.MAX_JSON_BYTES) throw FileTooLargeException()
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private class FileTooLargeException : Exception()

    companion object { const val FILE_NAME = "sportzal_program.json" }
}
