package ru.sportzal.app.ui.equipment

import org.junit.Assert.*
import org.junit.Test

class EquipmentWeightsTest {
    @Test fun weightsAcceptRussianDecimalsAndIrregularSteps() {
        assertEquals(listOf(2.5, 5.0, 12.5), parseAvailableWeights("2,5; 5; 12,5"))
        assertNull(parseAvailableWeights(""))
        assertNull(parseAvailableWeights("5; abc"))
        assertNull(parseAvailableWeights("-5; 10"))
        assertNull(parseAvailableWeights("5; 5"))
    }
}
