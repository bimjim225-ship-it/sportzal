package ru.sportzal.app.data.files

import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.model.ImportResult

class ProgramImporterTest {
    private var writes = 0
    private val saved = mutableMapOf<Pair<String, Int>, String>()
    private val repository = Proxy.newProxyInstance(
        SportzalRepository::class.java.classLoader,
        arrayOf(SportzalRepository::class.java),
    ) { _, method, args ->
        when (method.name) {
            "importProgram" -> {
                writes++
                val program = args[0] as ru.sportzal.app.model.ProgramDocument
                val hash = args[2] as String
                val key = program.programId to program.programVersion
                val previous = saved[key]
                when {
                    previous == null -> { saved[key] = hash; ImportResult.Imported }
                    previous == hash -> ImportResult.NoOp
                    else -> ImportResult.Conflict
                }
            }
            "toString" -> "FakeSportzalRepository"
            "hashCode" -> System.identityHashCode(this)
            "equals" -> false
            else -> error("Unexpected repository call: ${method.name}")
        }
    } as SportzalRepository
    private val importer = ProgramImporter(null, ProgramValidator(), repository)
    private fun source() = checkNotNull(javaClass.classLoader?.getResource("program.json")).readText()

    @Test fun validPreviewDoesNotWriteDatabase() = runBlocking {
        assertTrue(importer.import(ByteArrayInputStream(source().toByteArray())) is ImportPreview.Valid)
        assertEquals(0, writes)
    }

    @Test fun confirmWritesAndRepeatedContentIsNoOp() = runBlocking {
        val preview = importer.import(ByteArrayInputStream(source().toByteArray())) as ImportPreview.Valid
        assertEquals(ImportResult.Imported, importer.confirm(preview))
        assertEquals(ImportResult.NoOp, importer.confirm(preview))
        assertEquals(2, writes)
    }

    @Test fun sameKeyWithDifferentContentConflicts() = runBlocking {
        val first = importer.preview(source()) as ImportPreview.Valid
        val changed = importer.preview(source().replace("Синтетическая тренировка A", "Синтетическая тренировка B")) as ImportPreview.Valid
        assertEquals(ImportResult.Imported, importer.confirm(first))
        assertEquals(ImportResult.Conflict, importer.confirm(changed))
    }

    @Test fun oversizedInputIsRejectedBeforeDatabaseMutation() = runBlocking {
        val oversized = ByteArray(ProgramValidator.MAX_JSON_BYTES + 1) { ' '.code.toByte() }
        val result = importer.import(ByteArrayInputStream(oversized))
        assertTrue(result is ImportPreview.Error)
        assertEquals(0, writes)
    }
}
