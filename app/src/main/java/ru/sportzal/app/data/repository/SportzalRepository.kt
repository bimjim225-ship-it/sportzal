package ru.sportzal.app.data.repository
import androidx.room.withTransaction
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import ru.sportzal.app.data.db.*
import ru.sportzal.app.model.*

interface SportzalRepository {
 suspend fun importProgram(validated:ProgramDocument,canonicalJson:String,canonicalHash:String):ImportResult
 fun observeTodayState():Flow<TodayData>
 suspend fun startWorkout(programId:String,programVersion:Int,workoutInstanceId:String):String
 suspend fun cancelEmptyWorkout(workoutId:String):CancelEmptyResult
 suspend fun saveSet(command:SaveSetCommand):SaveSetResult
 suspend fun editSet(command:EditSetCommand)
 suspend fun deleteSet(setResultId:String)
 suspend fun skipSet(command:SkipSetCommand)
 suspend fun restoreSkippedSet(workoutId:String,exerciseInstanceId:String,plannedSetNo:Int)
 suspend fun finishWorkout(workoutId:String):CompletionStatus
 fun observeActiveWorkout():Flow<WorkoutRuntime?>
 suspend fun snapshotSource(focusWorkoutId:String?):SnapshotSource
}
class RoomSportzalRepository(private val db:SportzalDatabase,private val now:()->String={Instant.now().toString()},private val newId:()->String={UUID.randomUUID().toString()}):SportzalRepository {
 private val dao=db.dao()
 override suspend fun importProgram(v:ProgramDocument,canonicalJson:String,canonicalHash:String)=db.withTransaction {
  val old=dao.program(v.programId,v.programVersion)
  if(old!=null) return@withTransaction if(old.canonicalHash==canonicalHash) ImportResult.NoOp else ImportResult.Conflict
  dao.insertProgram(ProgramEntity(v.programId,v.programVersion,v.schemaVersion,canonicalJson,canonicalHash,v.generatedAt,now()))
  dao.upsertEquipment(v.equipmentUpserts.map{EquipmentEntity(it.equipmentId,it.name,it.setupHint,it.weightStepKg,it.availableWeightsKg?.let { weights -> StrictJson.encodeToString(weights) },it.notes,null,now())})
  dao.upsertIndex(v.workouts.mapIndexed{i,w->ProgramWorkoutIndexEntity(v.programId,v.programVersion,w.workoutInstanceId,w.templateId,w.title,w.plannedDate,i+1)})
  dao.setAppState(AppStateEntity(activeProgramId=v.programId,activeProgramVersion=v.programVersion)); ImportResult.Imported
 }
 override fun observeTodayState()=dao.observeState().map{TodayData(it?.activeProgramId,it?.activeProgramVersion)}
 override suspend fun startWorkout(programId:String,programVersion:Int,workoutInstanceId:String)=db.withTransaction {
  check(dao.activeWorkout()==null){"An active workout already exists"}; val p=checkNotNull(dao.program(programId,programVersion)); val document=StrictJson.decodeFromString<ProgramDocument>(p.canonicalJson); val workout=checkNotNull(document.workouts.find{it.workoutInstanceId==workoutInstanceId}); val id=newId()
  val referenced=workout.blocks.flatMap{it.exercises}.mapNotNull{it.equipmentId}.toSet(); val equipment=dao.equipment().filter{it.equipmentId in referenced}.map{EquipmentDocument(it.equipmentId,it.name,it.setupHint,it.weightStepKg,it.availableWeightsJson?.let { encoded -> StrictJson.decodeFromString<List<Double>>(encoded) },it.notes)}
  dao.insertWorkout(WorkoutEntity(id,programId,programVersion,workoutInstanceId,workout.templateId,now(),null,"active",StrictJson.encodeToString(workout),StrictJson.encodeToString(equipment),null)); id
 }
 override suspend fun cancelEmptyWorkout(workoutId:String)=db.withTransaction { if(dao.workout(workoutId)==null) CancelEmptyResult.NotFound else if(dao.setCount(workoutId)>0||dao.skipCount(workoutId)>0) CancelEmptyResult.HasFacts else {dao.deleteWorkout(workoutId);CancelEmptyResult.Cancelled} }
 override suspend fun saveSet(c:SaveSetCommand)=db.withTransaction {
  dao.set(c.setResultId)?.let{return@withTransaction SaveSetResult.Saved(it.setResultId,it.sequenceNo,true)}
  if(c.plannedSetNo!=null&&(dao.setInSlot(c.workoutId,c.exerciseInstanceId,c.plannedSetNo)!=null||dao.skipped(c.workoutId,c.exerciseInstanceId,c.plannedSetNo)!=null)) return@withTransaction SaveSetResult.Conflict("Planned slot already contains a fact")
  val sequence=(dao.maxSequence(c.workoutId)?:0)+1; dao.insertSet(SetResultEntity(c.setResultId,c.workoutId,sequence,c.exerciseInstanceId,c.plannedSetNo,c.setType,c.exerciseIdActual,c.titleActual,c.equipmentIdActual,c.equipmentNameActual,c.setupActual,c.loadBasisActual,c.sideActual,c.weightKg,c.reps,c.rir,c.completedAt,c.bootId,c.elapsedRealtimeMs,null,StrictJson.encodeToString(c.deviations),c.note)); c.plannedSetNo?.let{dao.deleteDraft(c.workoutId,c.exerciseInstanceId,it)}; SaveSetResult.Saved(c.setResultId,sequence,false)
 }
 override suspend fun editSet(c:EditSetCommand)=db.withTransaction { val old=checkNotNull(dao.set(c.setResultId)); dao.updateSet(old.copy(weightKg=c.weightKg,reps=c.reps,rir=c.rir,editedAt=c.editedAt,deviationsJson=StrictJson.encodeToString(c.deviations),note=c.note)) }
 override suspend fun deleteSet(setResultId:String)=dao.deleteSet(setResultId)
 override suspend fun skipSet(c:SkipSetCommand)=db.withTransaction { check(dao.setInSlot(c.workoutId,c.exerciseInstanceId,c.plannedSetNo)==null){"Planned slot already contains a set"}; dao.insertSkip(SkippedSetEntity(c.workoutId,c.exerciseInstanceId,c.plannedSetNo,c.recordedAt,c.reason,c.note)); dao.deleteDraft(c.workoutId,c.exerciseInstanceId,c.plannedSetNo) }
 override suspend fun restoreSkippedSet(workoutId:String,exerciseInstanceId:String,plannedSetNo:Int)=dao.deleteSkip(workoutId,exerciseInstanceId,plannedSetNo)
 override suspend fun finishWorkout(workoutId:String):CompletionStatus=db.withTransaction { checkNotNull(dao.workout(workoutId)); val status=if(dao.skipCount(workoutId)>0) CompletionStatus.ENDED_EARLY else CompletionStatus.COMPLETED; dao.finish(workoutId,status.name.lowercase(),now());status }
 override fun observeActiveWorkout()=dao.observeActiveWorkout().map{it?.let{WorkoutRuntime(it.workoutId,it.planSnapshotJson,it.equipmentAtStartJson)}}
 override suspend fun snapshotSource(focusWorkoutId:String?)=SnapshotSource(dao.programs().map{it.canonicalJson},dao.workouts().filter{focusWorkoutId==null||it.workoutId==focusWorkoutId}.map{WorkoutRuntime(it.workoutId,it.planSnapshotJson,it.equipmentAtStartJson)})
}
