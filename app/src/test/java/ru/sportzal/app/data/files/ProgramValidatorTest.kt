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
}
