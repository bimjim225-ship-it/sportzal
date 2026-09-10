package ru.sportzal.app.data.files

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface JsonReadResult {
    data class Success(val source: String) : JsonReadResult
    data object TooLarge : JsonReadResult
    data object InvalidUtf8 : JsonReadResult
    data object Unreadable : JsonReadResult
}
class JsonFileReader(private val resolver: ContentResolver, private val maxBytes: Int = 10 * 1024 * 1024) {
    suspend fun read(uri: Uri): JsonReadResult = withContext(Dispatchers.IO) {
        val stream = runCatching { resolver.openInputStream(uri) }.getOrNull() ?: return@withContext JsonReadResult.Unreadable
        read(stream)
    }
    fun read(stream: InputStream): JsonReadResult = try {
        stream.use {
            val output = ByteArrayOutputStream(); val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var total = 0
            while (true) { val count = it.read(buffer); if (count < 0) break; total += count
                if (total > maxBytes) return JsonReadResult.TooLarge; output.write(buffer, 0, count) }
            val source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
            JsonReadResult.Success(source)
        }
    } catch (_: CharacterCodingException) { JsonReadResult.InvalidUtf8 }
      catch (_: Exception) { JsonReadResult.Unreadable }
}
