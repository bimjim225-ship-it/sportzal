package ru.sportzal.app.data.files
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*
class ProgramValidatorTest {
 private val validator=ProgramValidator(); private val valid=checkNotNull(javaClass.getResource("/program.json")).readText()
 private fun mutate(block:(MutableMap<String,JsonElement>)->Unit):String {val root=Json.parseToJsonElement(valid).jsonObject.toMutableMap();block(root);return JsonObject(root).toString()}
 private fun failure(source:String)=assertTrue(validator.validate(source) is ValidationResult.Failure)
 @Test fun validProgramFixtureIsAccepted(){assertTrue(validator.validate(valid) is ValidationResult.Success)}
 @Test fun unknownFieldIsRejected()=failure(mutate{it["surprise"]=JsonPrimitive(true)})
 @Test fun unsupportedSchemaVersionIsRejected()=failure(mutate{it["schema_version"]=JsonPrimitive(2)})
 @Test fun invalidDateIsRejected(){failure(mutate{r->val ws=r["workouts"]!!.jsonArray.toMutableList();val w=ws[0].jsonObject.toMutableMap();w["planned_date"]=JsonPrimitive("2026-99-99");ws[0]=JsonObject(w);r["workouts"]=JsonArray(ws)})}
 @Test fun duplicateEquipmentIdIsRejected(){failure(mutate{r->val a=r["equipment_upserts"]!!.jsonArray;r["equipment_upserts"]=JsonArray(a+a.first())})}
 @Test fun duplicateWorkoutInstanceIdIsRejected(){failure(mutate{r->val a=r["workouts"]!!.jsonArray;r["workouts"]=JsonArray(a+a.first())})}
 @Test fun semanticMutationsAreRejected(){
  fun exerciseChange(change:(MutableMap<String,JsonElement>)->Unit)=mutate{r->val ws=r["workouts"]!!.jsonArray.toMutableList();val w=ws[0].jsonObject.toMutableMap();val bs=w["blocks"]!!.jsonArray.toMutableList();val b=bs[0].jsonObject.toMutableMap();val es=b["exercises"]!!.jsonArray.toMutableList();val e=es[0].jsonObject.toMutableMap();change(e);es[0]=JsonObject(e);b["exercises"]=JsonArray(es);bs[0]=JsonObject(b);w["blocks"]=JsonArray(bs);ws[0]=JsonObject(w);r["workouts"]=JsonArray(ws)}
  failure(exerciseChange{it["equipment_id"]=JsonPrimitive("missing")}); failure(exerciseChange{it["planned_order"]=JsonPrimitive(2)})
  failure(exerciseChange{e->val sets=e["planned_sets"]!!.jsonArray.toMutableList();val s=sets[0].jsonObject.toMutableMap();s["reps_min"]=JsonPrimitive(20);sets[0]=JsonObject(s);e["planned_sets"]=JsonArray(sets)})
  failure(exerciseChange{e->val sets=e["planned_sets"]!!.jsonArray.toMutableList();val s=sets[0].jsonObject.toMutableMap();s["target_weight_kg"]=JsonPrimitive(-1);sets[0]=JsonObject(s);e["planned_sets"]=JsonArray(sets)})
 }
 @Test fun canonicalizationIgnoresKeyOrderAndWhitespace(){val a=validator.validate(valid) as ValidationResult.Success;val reversed=JsonObject(Json.parseToJsonElement(valid).jsonObject.entries.reversed().associate{it.toPair()}).toString();val b=validator.validate(reversed) as ValidationResult.Success;assertEquals(a.canonicalHash,b.canonicalHash)}

 @Test fun oneExerciseRotationIsRejected()=failure(changeBlock { it["mode"]=JsonPrimitive("rotation") })
 @Test fun multipleExerciseStraightIsRejected()=failure(changeBlock { block -> val items=block["exercises"]!!.jsonArray; block["mode"]=JsonPrimitive("straight"); block["exercises"]=JsonArray(items+items.first()) })
 @Test fun duplicateExerciseInstanceIdIsRejected()=failure(changeBlock { block -> val items=block["exercises"]!!.jsonArray; block["exercises"]=JsonArray(items+items.first()) })
 @Test fun unorderedPlannedSetNumbersAreRejected()=failure(changeExercise { exercise -> val sets=exercise["planned_sets"]!!.jsonArray.toMutableList(); sets.reverse(); exercise["planned_sets"]=JsonArray(sets) })
 @Test fun emptyPlannedSetsAreRejected()=failure(changeExercise { it["planned_sets"]=JsonArray(emptyList()) })
 @Test fun bodyweightWithNonZeroTargetIsRejected()=failure(changeExercise { it["load_basis"]=JsonPrimitive("bodyweight") })
 @Test fun unknownEquipmentReferenceIsRejected()=failure(changeExercise { it["equipment_id"]=JsonPrimitive("unknown") })
 @Test fun blankWorkoutInstanceIdIsRejected()=failure(changeWorkout { it["workout_instance_id"]=JsonPrimitive(" ") })
 @Test fun blankTemplateIdIsRejected()=failure(changeWorkout { it["template_id"]=JsonPrimitive(" ") })
 @Test fun blankBlockIdIsRejected()=failure(changeBlock { it["block_id"]=JsonPrimitive(" ") })
 @Test fun blankTitlesAreRejected(){ failure(changeWorkout { it["title"]=JsonPrimitive(" ") }); failure(changeBlock { it["title"]=JsonPrimitive(" ") }); failure(changeExercise { it["title"]=JsonPrimitive(" ") }) }
 @Test fun schemaOptionalStringExplicitNullIsRejected()=failure(mutate { it["coach_notes"]=JsonNull })
 @Test fun duplicateJsonKeyIsRejected()=failure(valid.replaceFirst("\"schema\":", "\"schema\":\"sportzal.program\",\"schema\":"))
 @Test fun excessiveNestingIsRejected()=failure("[".repeat(33)+"0"+"]".repeat(33))
 @Test fun oversizedJsonIsRejected()=failure(" ".repeat(ProgramValidator.MAX_JSON_BYTES+1))
 @Test fun equivalentNumbersHaveSameCanonicalHash(){
  fun withNumber(number:String)=valid.replaceFirst("\"target_weight_kg\": 20", "\"target_weight_kg\": $number")
  val hashes=listOf("40","40.0","4e1").map { (validator.validate(withNumber(it)) as ValidationResult.Success).canonicalHash }
  assertEquals(1,hashes.distinct().size)
 }
 @Test fun absentAndExplicitNullRemainCanonicallyDifferent(){
  val absent=valid.replace(Regex(",?\\s*\"weight_step_kg\"\\s*:\\s*5"), "")
  val explicit=absent.replaceFirst("\"setup_hint\": \"Упор 3\"", "\"setup_hint\": \"Упор 3\", \"weight_step_kg\": null")
  val a=validator.validate(absent) as ValidationResult.Success
  val b=validator.validate(explicit) as ValidationResult.Success
  assertNotEquals(a.canonicalHash,b.canonicalHash)
 }
 private fun changeWorkout(change:(MutableMap<String,JsonElement>)->Unit)=mutate { root -> val workouts=root["workouts"]!!.jsonArray.toMutableList(); val workout=workouts[0].jsonObject.toMutableMap(); change(workout); workouts[0]=JsonObject(workout); root["workouts"]=JsonArray(workouts) }
 private fun changeBlock(change:(MutableMap<String,JsonElement>)->Unit)=changeWorkout { workout -> val blocks=workout["blocks"]!!.jsonArray.toMutableList(); val block=blocks.last().jsonObject.toMutableMap(); change(block); blocks[blocks.lastIndex]=JsonObject(block); workout["blocks"]=JsonArray(blocks) }
 private fun changeExercise(change:(MutableMap<String,JsonElement>)->Unit)=changeBlock { block -> val exercises=block["exercises"]!!.jsonArray.toMutableList(); val exercise=exercises[0].jsonObject.toMutableMap(); change(exercise); exercises[0]=JsonObject(exercise); block["exercises"]=JsonArray(exercises) }
}
