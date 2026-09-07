package ru.sportzal.app.data.files

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import ru.sportzal.app.model.*

sealed interface ValidationResult<out T> { data class Success<T>(val value:T,val canonicalJson:String,val canonicalHash:String):ValidationResult<T>; data class Failure(val code:String,val message:String):ValidationResult<Nothing> }
class ProgramValidator(private val json:Json=StrictJson) {
 fun validate(source:String, knownEquipmentIds:Set<String> = emptySet()):ValidationResult<ProgramDocument> = try {
  val document=json.decodeFromString<ProgramDocument>(source); validateSemantics(document)
  val equipment=knownEquipmentIds+document.equipmentUpserts.map { it.equipmentId }
  require(document.workouts.flatMap{it.blocks}.flatMap{it.exercises}.all{it.equipmentId==null||it.equipmentId in equipment}){"unknown equipment reference"}
  val canonical=canonicalize(json.parseToJsonElement(source)).toString()
  ValidationResult.Success(document,canonical,MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString(""){"%02x".format(it)})
 } catch(e:Exception){ ValidationResult.Failure("INVALID_PROGRAM",e.message?:"Invalid program") }
 private fun validateSemantics(p:ProgramDocument) {
  require(p.schema=="sportzal.program" && p.schemaVersion==1){"unsupported schema"}; require(p.programId.isNotBlank()&&p.programVersion>=1); Instant.parse(p.generatedAt)
  require(p.equipmentUpserts.map{it.equipmentId}.distinct().size==p.equipmentUpserts.size){"duplicate equipment_id"}
  p.equipmentUpserts.forEach { e -> require(e.equipmentId.isNotBlank()&&e.name.isNotEmpty()); e.weightStepKg?.let{require(it>0)}; e.availableWeightsKg?.let{require(it.isNotEmpty()&&it.all{n->n>=0}&&it.zipWithNext().all{(a,b)->a<b}){"available weights must be strictly ascending"}} }
  require(p.workouts.isNotEmpty()&&p.workouts.map{it.workoutInstanceId}.distinct().size==p.workouts.size){"duplicate workout_instance_id"}
  p.workouts.forEach { w -> LocalDate.parse(w.plannedDate); require(w.blocks.isNotEmpty()&&w.blocks.map{it.blockId}.distinct().size==w.blocks.size){"duplicate block_id"}; val exercises=w.blocks.flatMap{it.exercises}; require(exercises.map{it.exerciseInstanceId}.distinct().size==exercises.size){"duplicate exercise_instance_id"}
   w.blocks.forEach { b -> require((b.mode=="straight"&&b.exercises.size==1)||(b.mode=="rotation"&&b.exercises.size>=2)){"invalid block size"}; require(b.exercises.map{it.plannedOrder}.distinct().size==b.exercises.size){"duplicate planned_order"}
    b.exercises.forEach { e -> require(e.exerciseInstanceId.isNotBlank()&&e.exerciseId.isNotBlank()&&e.title.isNotEmpty()&&e.plannedOrder>=1); require(e.loadBasis in setOf("machine_display","total_external","per_hand","assistance","bodyweight")); require(e.side in setOf("bilateral","left","right")); require(e.rirCapture in setOf("none","last_work_set","all_work_sets")); require(e.plannedSets.map{it.setNo}==(1..e.plannedSets.size).toList()){"set numbers must be ordered and contiguous"}; e.plannedSets.forEach{s->require(s.setType in setOf("warmup","work")&&s.repsMin>=1&&s.repsMax>=s.repsMin&&s.targetWeightKg>=0&&s.restTargetSec>=0&&(s.targetRir==null||s.targetRir in 0..4)); require(e.loadBasis!="bodyweight"||s.targetWeightKg==0.0){"bodyweight target must be zero"} }
   }
  }
 }
 private fun canonicalize(e:JsonElement):JsonElement=when(e){is JsonObject->JsonObject(e.entries.sortedBy{it.key}.associate{it.key to canonicalize(it.value)});is JsonArray->JsonArray(e.map(::canonicalize));else->e}
}
