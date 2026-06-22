package com.grailpay.banklink.internal.time

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Timestamps in merchant-facing callbacks (failed_at, exited_at): "YYYY-MM-DD HH:mm:ss" UTC,
// matching web's formatTimestamp. Different from the ISO 8601 + millis format SdkLogger uses
// for server telemetry.
internal object MerchantTimestamp {
    private val formatter = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun now(): String = formatter.get()!!.format(Date())
}