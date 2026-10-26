package ru.sportzal.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        EquipmentVisualEntity::class,
        ExerciseVisualEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class SportzalDatabase : RoomDatabase() {
    abstract fun dao(): SportzalDao
    abstract fun visualDao(): VisualDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS equipment_visuals (equipment_id TEXT NOT NULL, illustration_key TEXT NOT NULL, PRIMARY KEY(equipment_id), FOREIGN KEY(equipment_id) REFERENCES equipment(equipment_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS exercise_visuals (exercise_id TEXT NOT NULL, equipment_id TEXT NOT NULL, illustration_key TEXT NOT NULL, PRIMARY KEY(exercise_id, equipment_id))")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE drafts ADD COLUMN rir_answered INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE drafts SET rir_answered = 1 WHERE rir IS NOT NULL")
            }
        }

        fun create(context: Context) =
            Room.databaseBuilder(context, SportzalDatabase::class.java, "sportzal.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
