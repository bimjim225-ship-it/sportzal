package ru.sportzal.app.ui.media

import org.junit.Assert.*
import org.junit.Test

class VisualCatalogTest {
    @Test fun unknownKeyNeverGuessesAMachine() {
        assertNull(VisualCatalog.find(null))
        assertNull(VisualCatalog.find("unknown-model"))
        assertEquals(5, VisualCatalog.all.size)
        assertEquals("row", VisualCatalog.find("row")!!.key)
    }

    @Test fun techniqueRequiresExplicitExerciseAndEquipmentBinding() {
        val bindings = VisualBindings(mapOf("machine-a" to "row"), mapOf(("exercise" to "machine-a") to "row"))
        assertEquals("row", bindings.equipment("machine-a")?.key)
        assertEquals("row", bindings.technique("exercise", "machine-a")?.key)
        assertNull(bindings.technique("other-exercise", "machine-a"))
        assertNull(bindings.technique("exercise", "machine-b"))
        assertNull(bindings.equipment("unknown"))
    }
}
