package ru.sportzal.app.data.files

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonFileReaderTest {
    private val reader = JsonFileReader(resolver = null, maxBytes = 8)

    @Test fun exactlyAtLimitSucceeds() {
        assertEquals(JsonReadResult.Success("12345678"), reader.read(ByteArrayInputStream("12345678".toByteArray())))
    }
    @Test fun overLimitReturnsTooLarge() {
        assertEquals(JsonReadResult.TooLarge, reader.read(ByteArrayInputStream("123456789".toByteArray())))
    }
    @Test fun malformedUtf8ReturnsInvalidUtf8() {
        assertEquals(JsonReadResult.InvalidUtf8, reader.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))))
    }
    @Test fun readFailureReturnsUnreadable() {
        val broken = object : InputStream() { override fun read(): Int = error("disk") }
        assertTrue(reader.read(broken) is JsonReadResult.Unreadable)
    }
}
