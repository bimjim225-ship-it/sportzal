package ru.sportzal.app.data.files

import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.data.db.SkippedSetEntity
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.model.*

class SnapshotExporterTest {
    @Test fun exportsSportzalAiSnapshotV1() = runBlocking {
        val root = document(exporter(source()).export().json)
        assertEquals("sportzal.ai_snapshot", root["schema"]?.toString()?.trim('"'))
        assertEquals("1", root["schema_version"].toString())
        assertEquals(setOf("schema", "schema_version", "exported_at", "app", "active_program",
            "focus_workout_id", "history_scope", "equipment", "programs", "workouts"), root.keys)
    }

    @Test fun usesCanonicalSnakeCaseFields() = runBlocking {
        val json = exporter(source()).export().json
        assertTrue(json.contains("\"workout_id\""))
        assertFalse(json.contains("workoutId"))
        assertFalse(json.contains("export_id"))
        assertFalse(json.contains("actual_rest_sec"))
        assertFalse(json.contains("photo_path"))
        assertFalse(json.contains("drafts"))
    }

    @Test fun optionalNullFieldsAreOmitted() = runBlocking {
        val root = document(exporter(source()).export().json)
        val program = root.getValue("programs").jsonArray.single().jsonObject
        assertFalse(program.containsKey("athlete_context")); assertFalse(program.containsKey("coach_notes"))
        val workoutPlan = program.getValue("workouts").jsonArray.single().jsonObject
        assertFalse(workoutPlan.containsKey("notes"))
        val block = workoutPlan.getValue("blocks").jsonArray.single().jsonObject
        assertFalse(block.containsKey("notes"))
        val exercise = block.getValue("exercises").jsonArray.single().jsonObject
        assertFalse(exercise.containsKey("notes"))
        val result = root.getValue("workouts").jsonArray.single().jsonObject
        assertFalse(result.containsKey("notes"))
        assertFalse(result.getValue("sets").jsonArray.single().jsonObject.containsKey("note"))
        assertFalse(result.getValue("skipped_sets").jsonArray.single().jsonObject.containsKey("note"))
    }

    @Test fun requiredNullableFieldsRemainPresentAsNull() = runBlocking {
        val root = document(exporter(source()).export().json)
        assertNullKey(root, "active_program"); assertNullKey(root, "focus_workout_id")
        val planned = root["programs"]!!.jsonArray.single().jsonObject["workouts"]!!.jsonArray.single().jsonObject
            ["blocks"]!!.jsonArray.single().jsonObject["exercises"]!!.jsonArray.single().jsonObject
        assertNullKey(planned, "setup_hint")
        assertNullKey(planned["planned_sets"]!!.jsonArray.single().jsonObject, "target_rir")
        val workout = root["workouts"]!!.jsonArray.single().jsonObject
        assertNullKey(workout, "finished_at")
        val set = workout["sets"]!!.jsonArray.single().jsonObject
        listOf("planned_set_no", "equipment_id_actual", "equipment_name_actual", "setup_actual", "rir",
            "clock", "edited_at").forEach { assertNullKey(set, it) }
        assertNullKey(workout["skipped_sets"]!!.jsonArray.single().jsonObject, "reason")
    }

    @Test fun serializationIsDeterministic() = runBlocking {
        val exporter = exporter(source())
        assertEquals(exporter.export().json, exporter.export().json)
    }

    private fun assertNullKey(value: kotlinx.serialization.json.JsonObject, key: String) {
        assertTrue("missing $key", value.containsKey(key)); assertSame(JsonNull, value[key])
    }
    private fun document(json: String) = StrictJson.parseToJsonElement(json).jsonObject
    private fun exporter(source: SnapshotSource) = SnapshotExporter(repository(source),
        now = { "2026-09-10T12:00:00Z" }, newId = { "id" })
    private fun repository(source: SnapshotSource) = Proxy.newProxyInstance(
        SportzalRepository::class.java.classLoader, arrayOf(SportzalRepository::class.java)
    ) { _, method, _ -> if (method.name == "snapshotSource") source else error("Unexpected ${method.name}") } as SportzalRepository

    private fun source(): SnapshotSource {
        val set = SetResultEntity("set", "runtime", 3, "exercise", null, "work", "squat", "Squat",
            null, null, null, "total_external", "bilateral", 100.0, 5, null,
            "2026-09-10T11:10:00Z", null, null, null, "[]", null)
        val skip = SkippedSetEntity("runtime", "exercise", 1, "2026-09-10T11:11:00Z", null, null)
        val exercise = ExerciseDocument("exercise", "squat", "Squat", null, null, "total_external",
            "bilateral", 1, "none", listOf(PlannedSetDocument(1, "work", 100.0, 5, 5, null, 60)))
        val workout = PlannedWorkoutDocument("planned", "template", "Workout", "2026-09-10",
            listOf(BlockDocument("block", "Block", "straight", listOf(exercise))))
        val program = ProgramDocument("sportzal.program", 1, "program", 1, "2026-09-10T10:00:00Z",
            emptyList(), listOf(workout))
        val runtime = WorkoutRuntime("runtime", StrictJson.encodeToString(PlannedWorkoutDocument.serializer(), workout),
            "[]", "program", 1, "planned", "template", "2026-09-10T11:00:00Z")
        return SnapshotSource(listOf(program), listOf(SnapshotWorkout(runtime, listOf(set), listOf(skip))),
            emptyList(), null, null, 1, 0)
    }
}
