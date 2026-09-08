package ru.sportzal.app.data.files

import org.junit.Assert.*
import org.junit.Test

class ProgramImporterTest {
    private val validator = ProgramValidator()
    private fun source() = checkNotNull(javaClass.classLoader?.getResource("program.json")).readText()

    @Test fun validImportCreatesPreviewWithoutWriting() {
        val validated = validator.validate(source()) as ValidationResult.Success
        assertEquals("sportzal.program", validated.value.schema)
        assertTrue(validated.canonicalHash.isNotBlank())
    }

    @Test fun malformedImportHasUnderstandableFailure() {
        val result = validator.validate("{not json") as ValidationResult.Failure
        assertEquals("INVALID_PROGRAM", result.code)
        assertTrue(result.message.isNotBlank())
    }

    @Test fun wrongSchemaAndUnsupportedVersionAreDistinct() {
        val wrong = validator.validate(source().replace("sportzal.program", "wrong.program")) as ValidationResult.Failure
        val version = validator.validate(source().replace("\"schema_version\": 1", "\"schema_version\": 99")) as ValidationResult.Failure
        assertTrue(wrong.message.contains("wrong schema"))
        assertTrue(version.message.contains("unsupported schema version"))
    }
}
