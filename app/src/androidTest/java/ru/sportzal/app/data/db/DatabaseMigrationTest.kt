package ru.sportzal.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DatabaseMigrationTest {
    @Test fun allMigrationsPreserveV1DataAndAllowExplicitMediaBindings() {
        helper.createDatabase("migration-all", 1).apply {
            execSQL("INSERT INTO equipment VALUES ('e', 'Тяга', 'Сиденье 4', 2.5, '[5,10]', 'note', NULL, 'now')")
            close()
        }
        helper.runMigrationsAndValidate("migration-all", 3, true,
            SportzalDatabase.MIGRATION_1_2, SportzalDatabase.MIGRATION_2_3).apply {
            execSQL("INSERT INTO equipment_visuals VALUES ('e', 'row')")
            execSQL("INSERT INTO exercise_visuals VALUES ('exercise', 'e', 'row')")
            query("SELECT name, setup_hint, weight_step_kg FROM equipment WHERE equipment_id='e'").use {
                it.moveToFirst(); assertEquals("Тяга", it.getString(0)); assertEquals("Сиденье 4", it.getString(1)); assertEquals(2.5, it.getDouble(2), 0.0)
            }
            query("SELECT illustration_key FROM exercise_visuals WHERE equipment_id='e'").use {
                it.moveToFirst(); assertEquals("row", it.getString(0))
            }
            close()
        }
    }

    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), SportzalDatabase::class.java,
    )

    @Test fun v1DraftsKeepFactsAndBackfillOnlyNumericAnswers() {
        helper.createDatabase("migration-rir", 1).apply {
            execSQL("INSERT INTO programs VALUES ('p', 1, 1, '{}', 'hash', 'generated', 'imported')")
            execSQL("INSERT INTO workouts VALUES ('w', 'p', 1, 'instance', 'A', 'started', NULL, 'active', '{}', '[]', 'note')")
            execSQL("INSERT INTO drafts VALUES ('w', 'unanswered', 1, '42.5', '8', NULL, NULL, 'now')")
            execSQL("INSERT INTO drafts VALUES ('w', 'numeric', 1, '20', '10', 2, NULL, 'now')")
            close()
        }
        helper.runMigrationsAndValidate("migration-rir", 2, true, SportzalDatabase.MIGRATION_1_2).apply {
            query("SELECT weight_text, reps_text, rir_answered FROM drafts WHERE exercise_instance_id='unanswered'").use {
                it.moveToFirst(); assertEquals("42.5", it.getString(0)); assertEquals("8", it.getString(1)); assertEquals(0, it.getInt(2))
            }
            query("SELECT rir, rir_answered FROM drafts WHERE exercise_instance_id='numeric'").use {
                it.moveToFirst(); assertEquals(2, it.getInt(0)); assertEquals(1, it.getInt(1))
            }
            query("SELECT notes, completion_status, plan_snapshot_json FROM workouts").use {
                it.moveToFirst(); assertEquals("note", it.getString(0)); assertEquals("active", it.getString(1)); assertEquals("{}", it.getString(2))
            }
            close()
        }
    }
}
