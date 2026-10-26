package ru.sportzal.app.ui.today

import org.junit.Assert.*
import org.junit.Test
import ru.sportzal.app.model.*

class PlanSummaryTest {
    @Test fun differentTargetsAreNotMisrepresentedAsOneRepeatedPrescription() {
        val exercise = ExerciseDocument("e", "row", "Тяга", null, null, "machine_display", "bilateral", 1, "none",
            listOf(PlannedSetDocument(1, "warmup", 10.0, 10, 10, null, 30),
                PlannedSetDocument(2, "work", 20.0, 8, 12, 2, 90),
                PlannedSetDocument(3, "work", 25.0, 6, 8, 2, 120)))
        val lines = plannedSetLines(exercise)
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("10 кг"))
        assertTrue(lines[1].contains("20 кг"))
        assertTrue(lines[2].contains("25 кг"))
        assertTrue(lines[2].contains("2:00"))
    }
}
