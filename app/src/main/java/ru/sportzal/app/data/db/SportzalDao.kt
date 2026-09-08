package ru.sportzal.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SportzalDao {
    @Query("SELECT * FROM programs WHERE program_id=:id AND program_version=:version")
    suspend fun program(id: String, version: Int): ProgramEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProgram(value: ProgramEntity)

    @Query("SELECT * FROM equipment WHERE equipment_id=:id")
    suspend fun equipment(id: String): EquipmentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEquipment(value: EquipmentEntity)

    @Update
    suspend fun updateEquipment(value: EquipmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIndex(values: List<ProgramWorkoutIndexEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setAppState(value: AppStateEntity)

    @Query("SELECT * FROM app_state WHERE id=1")
    fun observeState(): Flow<AppStateEntity?>

    @Query("SELECT * FROM program_workout_index WHERE program_id=:programId AND program_version=:version ORDER BY planned_date, planned_order")
    suspend fun plannedWorkouts(programId: String, version: Int): List<ProgramWorkoutIndexEntity>

    @Query("SELECT program_id, workout_instance_id FROM workouts")
    suspend fun consumedWorkouts(): List<ConsumedWorkoutRow>

    @Query("SELECT * FROM equipment ORDER BY equipment_id")
    suspend fun equipment(): List<EquipmentEntity>

    @Query("SELECT * FROM workouts WHERE completion_status='active' LIMIT 1")
    suspend fun activeWorkout(): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE completion_status='active' LIMIT 1")
    fun observeActiveWorkout(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE workout_id=:id")
    suspend fun workout(id: String): WorkoutEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkout(value: WorkoutEntity)

    @Query("UPDATE workouts SET completion_status=:status, finished_at=:at WHERE workout_id=:id")
    suspend fun finish(id: String, status: String, at: String)

    @Query("UPDATE workouts SET completion_status=:status WHERE workout_id=:id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT * FROM set_results WHERE set_result_id=:id")
    suspend fun set(id: String): SetResultEntity?

    @Query("SELECT * FROM set_results WHERE workout_id=:workout AND exercise_instance_id=:exercise AND planned_set_no=:number")
    suspend fun setInSlot(workout: String, exercise: String, number: Int): SetResultEntity?

    @Query("SELECT MAX(sequence_no) FROM set_results WHERE workout_id=:workout")
    suspend fun maxSequence(workout: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(value: SetResultEntity)

    @Update
    suspend fun updateSet(value: SetResultEntity)

    @Query("DELETE FROM set_results WHERE set_result_id=:id")
    suspend fun deleteSet(id: String)

    @Query("SELECT * FROM skipped_sets WHERE workout_id=:workout AND exercise_instance_id=:exercise AND planned_set_no=:number")
    suspend fun skipped(workout: String, exercise: String, number: Int): SkippedSetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSkip(value: SkippedSetEntity)

    @Query("DELETE FROM skipped_sets WHERE workout_id=:workout AND exercise_instance_id=:exercise AND planned_set_no=:number")
    suspend fun deleteSkip(workout: String, exercise: String, number: Int)

    @Query("DELETE FROM drafts WHERE workout_id=:workout AND exercise_instance_id=:exercise AND planned_set_no=:number")
    suspend fun deleteDraft(workout: String, exercise: String, number: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(value: DraftEntity)

    @Query("SELECT COUNT(*) FROM drafts WHERE workout_id=:id")
    suspend fun draftCount(id: String): Int

    @Query("SELECT COUNT(*) FROM set_results WHERE workout_id=:id")
    suspend fun setCount(id: String): Int

    @Query("SELECT COUNT(*) FROM skipped_sets WHERE workout_id=:id")
    suspend fun skipCount(id: String): Int

    @Query("DELETE FROM workouts WHERE workout_id=:id")
    suspend fun deleteWorkout(id: String)

    @Query("SELECT * FROM workouts ORDER BY started_at DESC")
    suspend fun workouts(): List<WorkoutEntity>

    @Query("SELECT * FROM set_results WHERE workout_id=:id ORDER BY sequence_no")
    suspend fun sets(id: String): List<SetResultEntity>

    @Query("SELECT * FROM skipped_sets WHERE workout_id=:id")
    suspend fun skips(id: String): List<SkippedSetEntity>

    @Query("SELECT * FROM programs")
    suspend fun programs(): List<ProgramEntity>
}

data class ConsumedWorkoutRow(
    @androidx.room.ColumnInfo(name = "program_id") val programId: String,
    @androidx.room.ColumnInfo(name = "workout_instance_id") val workoutInstanceId: String,
)
