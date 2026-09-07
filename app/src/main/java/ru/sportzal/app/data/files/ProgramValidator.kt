package ru.sportzal.app.data.files

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import ru.sportzal.app.model.ProgramDocument
import ru.sportzal.app.model.StrictJson

sealed interface ValidationResult<out T> {
    data class Success<T>(val value: T, val canonicalJson: String, val canonicalHash: String) : ValidationResult<T>
    data class Failure(val code: String, val message: String) : ValidationResult<Nothing>
}

class ProgramValidator(private val json: Json = StrictJson) {
    fun validate(source: String, knownEquipmentIds: Set<String> = emptySet()): ValidationResult<ProgramDocument> = try {
        require(source.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "JSON exceeds 10 MiB" }
        JsonPreflight(source).check()
        val root = json.parseToJsonElement(source).jsonObject
        rejectSchemaStringNulls(root)
        val document = json.decodeFromString<ProgramDocument>(source)
        validateSemantics(document)
        val equipment = knownEquipmentIds + document.equipmentUpserts.map { it.equipmentId }
        require(document.workouts.flatMap { it.blocks }.flatMap { it.exercises }.all {
            it.equipmentId == null || it.equipmentId in equipment
        }) { "unknown equipment reference" }
        val canonical = canonicalize(root)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
        ValidationResult.Success(document, canonical, hash)
    } catch (exception: Exception) {
        ValidationResult.Failure("INVALID_PROGRAM", exception.message ?: "Invalid program")
    }

    private fun rejectSchemaStringNulls(root: JsonObject) {
        listOf("athlete_context", "coach_notes").forEach { key ->
            require(root[key] !is JsonNull) { "$key cannot be null" }
        }
        root["workouts"]?.jsonArray?.forEach { workout ->
            require(workout.jsonObject["notes"] !is JsonNull) { "workout notes cannot be null" }
            workout.jsonObject["blocks"]?.jsonArray?.forEach { block ->
                require(block.jsonObject["notes"] !is JsonNull) { "block notes cannot be null" }
                block.jsonObject["exercises"]?.jsonArray?.forEach { exercise ->
                    require(exercise.jsonObject["notes"] !is JsonNull) { "exercise notes cannot be null" }
                }
            }
        }
    }

    private fun validateSemantics(program: ProgramDocument) {
        require(program.schema == "sportzal.program" && program.schemaVersion == 1) { "unsupported schema" }
        require(program.programId.isNotBlank() && program.programVersion >= 1) { "invalid program identity" }
        Instant.parse(program.generatedAt)
        require(program.equipmentUpserts.map { it.equipmentId }.distinct().size == program.equipmentUpserts.size) {
            "duplicate equipment_id"
        }
        program.equipmentUpserts.forEach { equipment ->
            require(equipment.equipmentId.isNotBlank() && equipment.name.isNotBlank()) { "blank equipment identity" }
            equipment.weightStepKg?.let { require(it > 0 && it.isFinite()) }
            equipment.availableWeightsKg?.let { weights ->
                require(weights.isNotEmpty() && weights.all { it >= 0 && it.isFinite() } &&
                    weights.zipWithNext().all { (first, second) -> first < second }) {
                    "available weights must be strictly ascending"
                }
            }
        }
        require(program.workouts.isNotEmpty())
        require(program.workouts.map { it.workoutInstanceId }.distinct().size == program.workouts.size) {
            "duplicate workout_instance_id"
        }
        program.workouts.forEach { workout ->
            require(workout.workoutInstanceId.isNotBlank()) { "blank workout_instance_id" }
            require(workout.templateId.isNotBlank()) { "blank template_id" }
            require(workout.title.isNotBlank()) { "blank workout title" }
            LocalDate.parse(workout.plannedDate)
            require(workout.blocks.isNotEmpty())
            require(workout.blocks.map { it.blockId }.distinct().size == workout.blocks.size) { "duplicate block_id" }
            val exercises = workout.blocks.flatMap { it.exercises }
            require(exercises.map { it.exerciseInstanceId }.distinct().size == exercises.size) {
                "duplicate exercise_instance_id"
            }
            workout.blocks.forEach { block ->
                require(block.blockId.isNotBlank()) { "blank block_id" }
                require(block.title.isNotBlank()) { "blank block title" }
                require((block.mode == "straight" && block.exercises.size == 1) ||
                    (block.mode == "rotation" && block.exercises.size >= 2)) { "invalid block size" }
                require(block.exercises.map { it.plannedOrder } == (1..block.exercises.size).toList()) {
                    "planned_order must be ordered and contiguous"
                }
                block.exercises.forEach { exercise ->
                    require(exercise.exerciseInstanceId.isNotBlank() && exercise.exerciseId.isNotBlank())
                    require(exercise.title.isNotBlank()) { "blank exercise title" }
                    require(exercise.equipmentId == null || exercise.equipmentId.isNotBlank())
                    require(exercise.loadBasis in LOAD_BASES)
                    require(exercise.side in SIDES)
                    require(exercise.rirCapture in RIR_CAPTURE_MODES)
                    require(exercise.plannedSets.isNotEmpty()) { "planned_sets cannot be empty" }
                    require(exercise.plannedSets.map { it.setNo } == (1..exercise.plannedSets.size).toList()) {
                        "set numbers must be ordered and contiguous"
                    }
                    exercise.plannedSets.forEach { set ->
                        require(set.setType in SET_TYPES)
                        require(set.repsMin >= 1 && set.repsMax >= set.repsMin)
                        require(set.targetWeightKg >= 0 && set.targetWeightKg.isFinite())
                        require(set.restTargetSec >= 0 && (set.targetRir == null || set.targetRir in 0..4))
                        require(exercise.loadBasis != "bodyweight" || set.targetWeightKg == 0.0) {
                            "bodyweight target must be zero"
                        }
                    }
                }
            }
        }
    }

    private fun canonicalize(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries.sortedBy { it.key }.joinToString(",", "{", "}") {
            "${JsonPrimitive(it.key)}:${canonicalize(it.value)}"
        }
        is JsonArray -> element.joinToString(",", "[", "]") { canonicalize(it) }
        is JsonNull -> "null"
        is JsonPrimitive -> when {
            element.isString -> element.toString()
            element.booleanOrNull != null -> element.content
            element.doubleOrNull != null -> BigDecimal(element.content).stripTrailingZeros().toPlainString()
            else -> element.toString()
        }
    }

    companion object {
        const val MAX_JSON_BYTES = 10 * 1024 * 1024
        private val LOAD_BASES = setOf("machine_display", "total_external", "per_hand", "assistance", "bodyweight")
        private val SIDES = setOf("bilateral", "left", "right")
        private val RIR_CAPTURE_MODES = setOf("none", "last_work_set", "all_work_sets")
        private val SET_TYPES = setOf("warmup", "work")
    }
}

/** Checks duplicate object keys and nesting before kotlinx.serialization can discard that information. */
private class JsonPreflight(private val source: String) {
    private var position = 0

    fun check() {
        value(0)
        whitespace()
        require(position == source.length) { "trailing JSON content" }
    }

    private fun value(depth: Int) {
        whitespace()
        require(position < source.length) { "unexpected end of JSON" }
        when (source[position]) {
            '{' -> objectValue(depth + 1)
            '[' -> arrayValue(depth + 1)
            '"' -> stringValue()
            else -> primitive()
        }
    }

    private fun objectValue(depth: Int) {
        require(depth <= 32) { "JSON nesting exceeds 32" }
        position++
        whitespace()
        val keys = mutableSetOf<String>()
        if (take('}')) return
        while (true) {
            whitespace()
            require(position < source.length && source[position] == '"') { "object key expected" }
            val key = stringValue()
            require(keys.add(key)) { "duplicate JSON key: $key" }
            whitespace()
            require(take(':')) { "colon expected" }
            value(depth)
            whitespace()
            if (take('}')) return
            require(take(',')) { "comma expected" }
        }
    }

    private fun arrayValue(depth: Int) {
        require(depth <= 32) { "JSON nesting exceeds 32" }
        position++
        whitespace()
        if (take(']')) return
        while (true) {
            value(depth)
            whitespace()
            if (take(']')) return
            require(take(',')) { "comma expected" }
        }
    }

    private fun stringValue(): String {
        val start = position
        position++
        var escaped = false
        while (position < source.length) {
            val character = source[position++]
            if (escaped) escaped = false
            else if (character == '\\') escaped = true
            else if (character == '"') {
                return Json.parseToJsonElement(source.substring(start, position)).let { (it as JsonPrimitive).content }
            }
        }
        error("unterminated string")
    }

    private fun primitive() {
        val start = position
        while (position < source.length && source[position] !in charArrayOf(',', '}', ']', ' ', '\t', '\r', '\n')) position++
        require(position > start) { "value expected" }
    }

    private fun whitespace() {
        while (position < source.length && source[position].isWhitespace()) position++
    }

    private fun take(character: Char): Boolean =
        if (position < source.length && source[position] == character) {
            position++
            true
        } else {
            false
        }
}
