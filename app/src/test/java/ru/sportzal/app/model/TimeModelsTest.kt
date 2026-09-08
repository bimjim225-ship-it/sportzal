package ru.sportzal.app.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TimeModelsTest {
    @Test fun sameBootUsesMonotonicClock() {
        val value = elapsedBetween(ClockAnchor(Instant.EPOCH, "boot", 100), ClockAnchor(Instant.EPOCH, "boot", 3100))
        assertEquals(Duration.ofSeconds(3), value.duration); assertFalse(value.approximate)
    }
    @Test fun differentBootUsesApproximateWallClock() {
        val value = elapsedBetween(ClockAnchor(Instant.EPOCH, "one", 100), ClockAnchor(Instant.EPOCH.plusSeconds(4), "two", 200))
        assertEquals(Duration.ofSeconds(4), value.duration); assertTrue(value.approximate)
    }
    @Test fun backwardsWallClockNeverMakesNegativeElapsed() {
        val value = elapsedBetween(ClockAnchor(Instant.EPOCH.plusSeconds(5), null, null), ClockAnchor(Instant.EPOCH, null, null))
        assertEquals(Duration.ZERO, value.duration)
    }
}
