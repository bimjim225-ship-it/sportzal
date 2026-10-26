package ru.sportzal.app.data.db

import androidx.room.*

@Entity(tableName = "equipment_visuals", foreignKeys = [ForeignKey(
    entity = EquipmentEntity::class, parentColumns = ["equipment_id"], childColumns = ["equipment_id"],
    onDelete = ForeignKey.CASCADE,
)])
data class EquipmentVisualEntity(
    @PrimaryKey @ColumnInfo(name = "equipment_id") val equipmentId: String,
    @ColumnInfo(name = "illustration_key") val illustrationKey: String,
)

/** Technique is explicitly selected for a movement on specific actual equipment. */
@Entity(tableName = "exercise_visuals", primaryKeys = ["exercise_id", "equipment_id"])
data class ExerciseVisualEntity(
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "equipment_id") val equipmentId: String,
    @ColumnInfo(name = "illustration_key") val illustrationKey: String,
)

@Dao
interface VisualDao {
    @Query("SELECT * FROM equipment_visuals") fun observeEquipment(): kotlinx.coroutines.flow.Flow<List<EquipmentVisualEntity>>
    @Query("SELECT * FROM exercise_visuals") fun observeTechniques(): kotlinx.coroutines.flow.Flow<List<ExerciseVisualEntity>>
    @Query("SELECT * FROM equipment_visuals") suspend fun equipment(): List<EquipmentVisualEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveEquipment(value: EquipmentVisualEntity)
    @Query("DELETE FROM equipment_visuals WHERE equipment_id=:id") suspend fun removeEquipment(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveTechnique(value: ExerciseVisualEntity)
    @Query("DELETE FROM exercise_visuals WHERE exercise_id=:exercise AND equipment_id=:equipment")
    suspend fun removeTechnique(exercise: String, equipment: String)
}
