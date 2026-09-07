package ru.sportzal.app.data.db
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao interface SportzalDao {
 @Query("SELECT * FROM programs WHERE program_id=:id AND program_version=:version") suspend fun program(id:String,version:Int):ProgramEntity?
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertProgram(value:ProgramEntity)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertEquipment(values:List<EquipmentEntity>)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertIndex(values:List<ProgramWorkoutIndexEntity>)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun setAppState(value:AppStateEntity)
 @Query("SELECT * FROM app_state WHERE id=1") fun observeState():Flow<AppStateEntity?>
 @Query("SELECT * FROM equipment ORDER BY equipment_id") suspend fun equipment():List<EquipmentEntity>
 @Query("SELECT * FROM program_workout_index WHERE program_id=:id AND program_version=:version AND workout_instance_id=:workout") suspend fun workoutIndex(id:String,version:Int,workout:String):ProgramWorkoutIndexEntity?
 @Query("SELECT * FROM workouts WHERE completion_status='active' LIMIT 1") suspend fun activeWorkout():WorkoutEntity?
 @Query("SELECT * FROM workouts WHERE completion_status='active' LIMIT 1") fun observeActiveWorkout():Flow<WorkoutEntity?>
 @Query("SELECT * FROM workouts WHERE workout_id=:id") suspend fun workout(id:String):WorkoutEntity?
 @Insert suspend fun insertWorkout(value:WorkoutEntity)
 @Query("UPDATE workouts SET completion_status=:status, finished_at=:at WHERE workout_id=:id") suspend fun finish(id:String,status:String,at:String)
 @Query("SELECT * FROM set_results WHERE set_result_id=:id") suspend fun set(id:String):SetResultEntity?
 @Query("SELECT * FROM set_results WHERE workout_id=:workout AND exercise_instance_id=:exercise AND planned_set_no=:number") suspend fun setInSlot(workout:String,exercise:String,number:Int):SetResultEntity?
 @Query("SELECT MAX(sequence_no) FROM set_results WHERE workout_id=:workout") suspend fun maxSequence(workout:String):Int?
 @Insert suspend fun insertSet(value:SetResultEntity)
 @Update suspend fun updateSet(value:SetResultEntity)
 @Query("DELETE FROM set_results WHERE set_result_id=:id") suspend fun deleteSet(id:String)
 @Query("SELECT * FROM skipped_sets WHERE workout_id=:workout AND exercise_instance_id=:exercise AND planned_set_no=:number") suspend fun skipped(workout:String,exercise:String,number:Int):SkippedSetEntity?
 @Insert suspend fun insertSkip(value:SkippedSetEntity)
 @Query("DELETE FROM skipped_sets WHERE workout_id=:workout AND exercise_instance_id=:exercise AND planned_set_no=:number") suspend fun deleteSkip(workout:String,exercise:String,number:Int)
 @Query("DELETE FROM drafts WHERE workout_id=:workout AND exercise_instance_id=:exercise AND planned_set_no=:number") suspend fun deleteDraft(workout:String,exercise:String,number:Int)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertDraft(value:DraftEntity)
 @Query("SELECT COUNT(*) FROM set_results WHERE workout_id=:id") suspend fun setCount(id:String):Int
 @Query("SELECT COUNT(*) FROM skipped_sets WHERE workout_id=:id") suspend fun skipCount(id:String):Int
 @Query("DELETE FROM workouts WHERE workout_id=:id") suspend fun deleteWorkout(id:String)
 @Query("SELECT * FROM workouts ORDER BY started_at DESC") suspend fun workouts():List<WorkoutEntity>
 @Query("SELECT * FROM set_results WHERE workout_id=:id ORDER BY sequence_no") suspend fun sets(id:String):List<SetResultEntity>
 @Query("SELECT * FROM skipped_sets WHERE workout_id=:id") suspend fun skips(id:String):List<SkippedSetEntity>
 @Query("SELECT * FROM programs") suspend fun programs():List<ProgramEntity>
}
