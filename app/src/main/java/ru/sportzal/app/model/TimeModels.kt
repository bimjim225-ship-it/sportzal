package ru.sportzal.app.model

import java.time.Duration
import java.time.Instant

data class ClockAnchor(val wall: Instant, val bootId: String?, val elapsedRealtimeMs: Long?)
data class ElapsedTime(val duration: Duration, val approximate: Boolean)

fun elapsedBetween(anchor: ClockAnchor, current: ClockAnchor): ElapsedTime {
    val sameBoot = anchor.bootId != null && anchor.bootId == current.bootId &&
        anchor.elapsedRealtimeMs != null && current.elapsedRealtimeMs != null
    val millis = if (sameBoot) current.elapsedRealtimeMs!! - anchor.elapsedRealtimeMs!!
    else Duration.between(anchor.wall, current.wall).toMillis()
    return ElapsedTime(Duration.ofMillis(millis.coerceAtLeast(0)), approximate = !sameBoot)
}
