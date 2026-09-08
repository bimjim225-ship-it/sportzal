package ru.sportzal.app.ui.components

import org.junit.Assert.*
import org.junit.Test

class NumericFieldTest {
    @Test fun `weight accepts both decimal separators and rejects non finite or negative`() {
        assertEquals(12.5, parseNumeric("12,5", NumericKind.WEIGHT))
        assertEquals(12.5, parseNumeric("12.5", NumericKind.WEIGHT))
        assertNull(parseNumeric("NaN", NumericKind.WEIGHT)); assertNull(parseNumeric("Infinity", NumericKind.WEIGHT))
        assertNull(parseNumeric("-1", NumericKind.WEIGHT))
    }
    @Test fun `reps are non negative integers`() {
        assertEquals(12, parseNumeric("12", NumericKind.REPS)); assertNull(parseNumeric("1.5", NumericKind.REPS)); assertNull(parseNumeric("-1", NumericKind.REPS))
    }
}
