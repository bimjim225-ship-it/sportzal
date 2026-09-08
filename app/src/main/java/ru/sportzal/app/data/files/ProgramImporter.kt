package ru.sportzal.app.data.files

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
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
    private val resolver: ContentResolver?,
    private val validator: ProgramValidator,
    private val repository: SportzalRepository,
) {
    suspend fun import(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val resolver = checkNotNull(resolver) { "ContentResolver is required for URI imports" }
        val bytes = try {
            resolver.openInputStream(uri)?.use(::readLimited) ?: return@withContext ImportPreview.Error("Не удалось открыть файл")
        } catch (_: FileTooLargeException) {
            return@withContext ImportPreview.Error("Файл больше 10 МиБ")
        } catch (error: Exception) {
            return@withContext ImportPreview.Error("Не удалось прочитать файл: ${error.message ?: "неизвестная ошибка"}")
        }
        val source = try {
            decodeUtf8Strict(bytes)
        } catch (_: CharacterCodingException) {
            return@withContext ImportPreview.Error("Файл не является корректным UTF-8")
        }
        preview(source)
    }

    suspend fun import(stream: InputStream): ImportPreview = withContext(Dispatchers.IO) {
        val bytes = try {
            stream.use(::readLimited)
        } catch (_: FileTooLargeException) {
            return@withContext ImportPreview.Error("Файл больше 10 МиБ")
        }
        val source = try {
            decodeUtf8Strict(bytes)
        } catch (_: CharacterCodingException) {
            return@withContext ImportPreview.Error("Файл не является корректным UTF-8")
        }
        preview(source)
    }

    fun preview(source: String): ImportPreview =
        when (val result = validator.validate(source)) {
            is ValidationResult.Success -> ImportPreview.Valid(result.value, result.canonicalJson, result.canonicalHash)
            is ValidationResult.Failure -> ImportPreview.Error("Программа не импортирована: ${result.message}")
        }

    suspend fun confirm(preview: ImportPreview.Valid): ImportResult = repository.importProgram(
        preview.program, preview.canonicalJson, preview.canonicalHash,
    )

    private fun decodeUtf8Strict(bytes: ByteArray): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

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
