package ru.sportzal.app.platform

import android.os.SystemClock
import android.content.Context
import android.provider.Settings
import java.time.Instant

interface ClockProvider {
    fun wallNow(): Instant
    fun elapsedRealtimeMs(): Long
    fun bootIdOrNull(): String?
}

class AndroidClockProvider(context: Context) : ClockProvider {
    private val bootId = runCatching {
        "android-boot-${Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)}"
    }.getOrNull()
    override fun wallNow(): Instant = Instant.now()
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun bootIdOrNull(): String? = bootId
}
