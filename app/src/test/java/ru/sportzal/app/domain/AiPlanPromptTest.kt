package ru.sportzal.app.domain

import org.junit.Assert.*
import org.junit.Test

class AiPlanPromptTest {
    @Test fun requestContainsExactSchemaAndStableIdentityRules() {
        val schema = "{\"schema-marker\":true}"
        val prompt = AiPlanPrompt.build(schema)
        assertTrue(prompt.contains(schema))
        assertTrue(prompt.contains("program_version"))
        assertTrue(prompt.contains("workout_instance_id"))
        assertTrue(prompt.contains("equipment_id"))
        assertTrue(prompt.contains("ограничения"))
    }
}
