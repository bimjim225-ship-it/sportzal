package ru.sportzal.app.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.combine
import ru.sportzal.app.data.db.ExerciseVisualEntity
import ru.sportzal.app.data.db.SportzalDatabase
import ru.sportzal.app.model.VisualAssignments
import ru.sportzal.app.model.VisualType

class VisualRepository(private val db: SportzalDatabase) {
    fun observe() = combine(db.visualDao().observeEquipment(), db.visualDao().observeTechniques()) { equipment, techniques ->
        VisualAssignments(equipment.associate { it.equipmentId to it.illustrationKey },
            techniques.associate { (it.exerciseId to it.equipmentId) to it.illustrationKey })
    }

    suspend fun saveTechnique(exerciseId: String, equipmentId: String?, key: String?) = db.withTransaction {
        require(exerciseId.isNotBlank())
        require(key == null || VisualType.fromKey(key) != null)
        if (key == null) db.visualDao().removeTechnique(exerciseId, equipmentId.orEmpty())
        else db.visualDao().saveTechnique(ExerciseVisualEntity(exerciseId, equipmentId.orEmpty(), key))
    }
}
