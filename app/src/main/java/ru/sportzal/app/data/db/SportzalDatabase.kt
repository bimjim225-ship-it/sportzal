package ru.sportzal.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppStateEntity::class,
        EquipmentEntity::class,
        ProgramEntity::class,
        ProgramWorkoutIndexEntity::class,
        WorkoutEntity::class,
        SetResultEntity::class,
        SkippedSetEntity::class,
        DraftEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SportzalDatabase : RoomDatabase() {
    abstract fun dao(): SportzalDao

    companion object {
        fun create(context: Context) =
            Room.databaseBuilder(context, SportzalDatabase::class.java, "sportzal.db").build()
    }
}
