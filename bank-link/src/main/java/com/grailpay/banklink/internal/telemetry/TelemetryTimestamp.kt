package com.grailpay.banklink.internal.telemetry

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Server-bound telemetry timestamp: ISO 8601 with millis in UTC, matching web's toISOString().
// DateTimeFormatter (not SimpleDateFormat) because SdkLogger shares this across concurrent IO
// coroutines and needs a thread-safe formatter.
internal object TelemetryTimestamp {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .withZone(ZoneOffset.UTC)

    fun nowIso(): String = formatter.format(Instant.now())
}
