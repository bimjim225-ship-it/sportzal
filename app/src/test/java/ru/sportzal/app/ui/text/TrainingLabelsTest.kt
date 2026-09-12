package ru.sportzal.app.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingLabelsTest {
    @Test fun allContractCodesHaveRussianPresentationLabels() {
        assertEquals("Разминка", setTypeLabel("warmup"))
        assertEquals("Рабочий", setTypeLabel("work"))
        assertEquals("Вес на тренажёре", loadBasisLabel("machine_display"))
        assertEquals("Общий внешний вес", loadBasisLabel("total_external"))
        assertEquals("Вес на одну руку", loadBasisLabel("per_hand"))
        assertEquals("Противовес", loadBasisLabel("assistance"))
        assertEquals("Собственный вес", loadBasisLabel("bodyweight"))
        assertEquals("Обе стороны", sideLabel("bilateral"))
        assertEquals("Левая сторона", sideLabel("left"))
        assertEquals("Правая сторона", sideLabel("right"))
    }

    @Test fun unknownCodesNeverLeakIntoPresentation() {
        assertEquals("Не указано", setTypeLabel("future_type"))
        assertEquals("Не указано", loadBasisLabel("future_basis"))
        assertEquals("Не указано", sideLabel("future_side"))
        assertEquals("Не указано", skipReasonLabel("future_reason"))
        assertEquals("Не указано", deviationLabel("future_deviation"))
    }

    @Test fun repetitionReserveLabelsAreSelfExplanatory() {
        assertEquals("0 — до отказа", rirLabel(0))
        assertEquals("1 повтор в запасе", rirLabel(1))
        assertEquals("2 повтора в запасе", rirLabel(2))
        assertEquals("3 повтора в запасе", rirLabel(3))
        assertEquals("4+ повтора в запасе", rirLabel(4))
    }
}
