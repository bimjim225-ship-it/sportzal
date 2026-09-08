package ru.sportzal.app.domain

import org.junit.Assert.*
import org.junit.Test

class PrefillResolverTest {
    private val context = ActualContext("squat", "rack", "high bar", "external", "bilateral")
    private fun slot(no: Int, type: String = "work", weight: Double = 100.0, context: ActualContext = this.context) =
        PlannedSlot(no, type, weight, 5, 8, 2, 120, context)
    private fun fact(no: Int, context: ActualContext = this.context) = PreviousSetFact(no, 97.5, 7, 1, context, listOf("pain"))

    @Test fun `draft wins`() {
        val draft = SetDraft(42.5, 11, 3, context)
        assertSame(draft, PrefillResolver.resolve(slot(2), slot(1), fact(1), draft, context))
    }
    @Test fun `new target uses target value`() = assertEquals(105.0,
        PrefillResolver.resolve(slot(2, weight = 105.0), slot(1), fact(1), null, context).weightKg!!, 0.0)
    @Test fun `warmup to work uses work target`() = assertEquals(100.0,
        PrefillResolver.resolve(slot(2), slot(1, "warmup", 50.0), fact(1), null, context).weightKg!!, 0.0)
    @Test fun `identical adjacent target may reuse fact without rir or deviations`() {
        val result = PrefillResolver.resolve(slot(2), slot(1), fact(1), null, context)
        assertEquals(97.5, result.weightKg!!, 0.0); assertEquals(7, result.reps); assertNull(result.rir)
    }
    @Test fun `non adjacent fact is not reused`() = assertEquals(100.0,
        PrefillResolver.resolve(slot(3), slot(1), fact(1), null, context).weightKg!!, 0.0)
    @Test fun `equipment substitution clears weight`() = assertContextReset(context.copy(equipmentId = "dumbbell"))
    @Test fun `exercise substitution clears weight`() = assertContextReset(context.copy(exerciseId = "front-squat"))
    @Test fun `load basis change clears weight`() = assertContextReset(context.copy(loadBasis = "machine_stack"))
    @Test fun `setup and side changes use target and never copy rir`() {
        assertEquals(100.0, PrefillResolver.resolve(slot(2), slot(1), fact(1), null, context.copy(setup = "low bar")).weightKg!!, 0.0)
        assertNull(PrefillResolver.resolve(slot(2), slot(1), fact(1), null, context.copy(side = "left")).rir)
    }
    private fun assertContextReset(actual: ActualContext) = assertNull(
        PrefillResolver.resolve(slot(2), slot(1), fact(1), null, actual).weightKg)
}
