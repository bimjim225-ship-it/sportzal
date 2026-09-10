package ru.sportzal.app.data.files

import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.model.*

class SnapshotExporterTest {
    private val workout = SnapshotWorkout("w1", "p", 1, "i", "A", "2026-01-01T00:00:00Z",
        "2026-01-01T01:00:00Z", "completed", "{}", "[]", null,
        listOf(SetResultEntity("s", "w1", 2, "e", null, "work", "ex", "Тяга", null, null,
            null, "external_load", "bilateral", 20.0, 8, 2, "2026-01-01T00:10:00Z", null,
            null, "2026-01-01T00:11:00Z", "[]", null)), emptyList())
    private fun exporter(source: SnapshotSource) = SnapshotExporter({ source },
        { Instant.parse("2026-01-02T00:00:00Z") }, { "export" }, Unit)

    @Test fun exportsValidAiSnapshotFixtureSemantics() = runBlocking {
        val root = exporter(SnapshotSource(emptyList(), listOf(workout))).export(null).jsonObject()
        assertEquals("sportzal.ai_snapshot", root["schema"]!!.jsonPrimitive.content)
        assertEquals(1, root["schema_version"]!!.jsonPrimitive.int)
        assertFalse(root.containsKey("actual_rest_sec")); assertFalse(root.containsKey("export_id"))
    }
    @Test fun extraSetKeepsNullPlannedSetNo() = runBlocking {
        val root = exporter(SnapshotSource(emptyList(), listOf(workout))).export(null).jsonObject()
        assertEquals(JsonNull, root["workouts"]!!.jsonArray[0].jsonObject["sets"]!!.jsonArray[0].jsonObject["planned_set_no"])
    }
    @Test fun editedSetAppearsOnceWithStableIdentity() = runBlocking {
        val sets = exporter(SnapshotSource(emptyList(), listOf(workout))).export(null).jsonObject()["workouts"]!!.jsonArray[0].jsonObject["sets"]!!.jsonArray
        assertEquals(1, sets.size); assertEquals("s", sets[0].jsonObject["set_result_id"]!!.jsonPrimitive.content)
        assertNotEquals(JsonNull, sets[0].jsonObject["edited_at"])
    }
    @Test fun doesNotExportDraftsOrActualRestSec() = runBlocking {
        val json = exporter(SnapshotSource(emptyList(), listOf(workout))).export(null).json
        assertFalse(json.contains("draft")); assertFalse(json.contains("actual_rest_sec"))
    }
    @Test fun serializationOrderingIsDeterministic() = runBlocking {
        val e = exporter(SnapshotSource(emptyList(), listOf(workout)))
        assertEquals(e.export(null).json, e.export(null).json)
    }
    @Test fun rejectsSnapshotOver10MiB() = runBlocking {
        val huge = workout.copy(notes = "x".repeat(SnapshotExporter.MAX_BYTES))
        try { exporter(SnapshotSource(emptyList(), listOf(huge))).export(null); fail() }
        catch (_: SnapshotTooLargeException) { }
    }
    private fun String.jsonObject() = Json.parseToJsonElement(this).jsonObject
}
