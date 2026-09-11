package ru.sportzal.app.data.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.sportzal.app.data.db.SportzalDatabase
import ru.sportzal.app.data.repository.RoomSportzalRepository
import ru.sportzal.app.model.BlockDocument
import ru.sportzal.app.model.EquipmentDocument
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.model.PlannedSetDocument
import ru.sportzal.app.model.PlannedWorkoutDocument
import ru.sportzal.app.model.ProgramDocument
import ru.sportzal.app.model.StrictJson
import ru.sportzal.app.platform.FileIntentHandler
import ru.sportzal.app.platform.FileIntentResult

@RunWith(AndroidJUnit4::class)
class FileRoundTripTest {
    private lateinit var context: Context
    private lateinit var database: SportzalDatabase
    private lateinit var repository: RoomSportzalRepository
    private lateinit var importer: ProgramImporter

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SportzalDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = RoomSportzalRepository(database)
        importer = ProgramImporter(context.contentResolver, ProgramValidator(), repository)
    }

    @After fun tearDown() = database.close()

    @Test fun contentUriProgramReachesPreviewAndDoesNotWriteBeforeConfirmation() = runBlocking {
        val source = StrictJson.encodeToString(program())
        val result = handler().handle(contentUri("program.json", source.toByteArray(Charsets.UTF_8)))

        assertTrue(result is FileIntentResult.Program)
        assertTrue((result as FileIntentResult.Program).preview is ImportPreview.Valid)
        assertNoWrites()
    }

    @Test fun contentUriClassificationAndReadFailuresNeverWrite() = runBlocking {
        val snapshot = handler().handle(contentUri("snapshot.json",
            "{\"schema\":\"sportzal.ai_snapshot\"}".toByteArray()))
        assertEquals("Это файл результатов, а не программа", (snapshot as FileIntentResult.Message).text)

        val unknown = handler().handle(contentUri("unknown.json", "{\"schema\":\"other\"}".toByteArray()))
        assertEquals("Неподдерживаемый файл Sportzal", (unknown as FileIntentResult.Message).text)

        val malformed = handler().handle(contentUri("malformed.json", "{".toByteArray()))
        assertEquals("Не удалось прочитать JSON", (malformed as FileIntentResult.Message).text)

        val invalidUtf8 = handler().handle(contentUri("invalid.json", byteArrayOf(0xc3.toByte(), 0x28)))
        assertEquals("Файл не является корректным UTF-8", (invalidUtf8 as FileIntentResult.Message).text)

        val smallHandler = FileIntentHandler(JsonFileReader(context.contentResolver, 16), importer)
        val oversized = smallHandler.handle(contentUri("large.json", ByteArray(17) { 'x'.code.toByte() }))
        assertEquals("Файл больше 10 МиБ", (oversized as FileIntentResult.Message).text)

        val unreadable = handler().handle(Uri.parse("content://ru.sportzal.app.missing/not-found.json"))
        assertEquals("Не удалось прочитать JSON", (unreadable as FileIntentResult.Message).text)
        assertNoWrites()
    }

    @Test fun shareAndSaveUseExactPendingExporterBytesAndReportFailures() = runBlocking {
        val program = program()
        val json = StrictJson.encodeToString(program)
        repository.importProgram(program, json, "hash")
        val exporter = SnapshotExporter(repository, { "2026-09-11T12:00:00Z" }, { "fixed" })
        val expected = exporter.export().json.toByteArray(Charsets.UTF_8)
        val coordinator = SnapshotShareCoordinator(context, exporter)

        val share = coordinator.shareIntent(null)
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("application/json", share.type)
        assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val sharedUri = share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", sharedUri.scheme)
        val shared = context.contentResolver.openInputStream(sharedUri)!!.use { it.readBytes() }
        assertArrayEquals(expected, shared)
        assertFalse(shared.toString(Charsets.UTF_8).contains("photo_path"))

        val create = coordinator.createSaveIntent(null)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, create.action)
        assertEquals("application/json", create.type)
        assertEquals("sportzal_ai_snapshot_20260911120000_fixed.json",
            create.getStringExtra(Intent.EXTRA_TITLE))
        assertFalse(coordinator.writePending(Uri.parse("content://ru.sportzal.app.missing/failure")))
        val destination = contentUri("destination.json", ByteArray(0))
        assertTrue(coordinator.writePending(destination))
        assertArrayEquals(expected, context.contentResolver.openInputStream(destination)!!.use { it.readBytes() })

        coordinator.createSaveIntent(null)
        coordinator.cancelSave()
        assertFalse(coordinator.writePending(destination))
    }

    private fun handler() = FileIntentHandler(JsonFileReader(context.contentResolver), importer)

    private suspend fun assertNoWrites() {
        assertTrue(database.dao().programs().isEmpty())
        assertTrue(database.dao().equipment().isEmpty())
        assertTrue(database.dao().workouts().isEmpty())
    }

    private fun contentUri(name: String, bytes: ByteArray): Uri {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, name).apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun program() = ProgramDocument("sportzal.program", 1, "program", 1,
        "2026-09-11T10:00:00Z", listOf(EquipmentDocument("rack", "Rack", null)), listOf(
            PlannedWorkoutDocument("session", "template", "Workout", "2026-09-11", listOf(
                BlockDocument("block", "Block", "straight", listOf(
                    ExerciseDocument("exercise", "squat", "Squat", "rack", null, "external", "bilateral",
                        1, "none", listOf(PlannedSetDocument(1, "work", 10.0, 5, 5, null, 60))),
                )),
            )),
        ))
}
