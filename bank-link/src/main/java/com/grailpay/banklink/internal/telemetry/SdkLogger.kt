package com.grailpay.banklink.internal.telemetry

import android.util.Log
import com.grailpay.banklink.BuildConfig
import com.grailpay.banklink.internal.net.BankLinkApi
import com.grailpay.banklink.internal.net.NetworkFactory
import com.grailpay.banklink.internal.net.TokenStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

internal enum class LogLevel(val wire: String, val priority: Int) {
    DEBUG("debug", 0),
    INFO("info", 1),
    WARN("warn", 2),
    ERROR("error", 3),
}

// Mirrors web's sdkLogger:
// - Console: everything in debug builds, nothing in release.
// - Server: info+ ships to the log endpoint, debug stays local.
// - No linkToken yet (pre-session): skip the network call.
// - Failures swallowed; telemetry must never break the merchant flow.
internal class SdkLogger(
    // Provider, not a fixed instance: the SDK resolves its host at runtime, so the api can
    // change between sessions.
    private val apiProvider: () -> BankLinkApi,
    private val tokens: TokenStore,
) {
    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, _ -> },
    )

    fun debug(event: String, data: Map<String, JsonElement> = emptyMap()) = ship(LogLevel.DEBUG, event, data)
    fun info(event: String, data: Map<String, JsonElement> = emptyMap()) = ship(LogLevel.INFO, event, data)
    fun warn(event: String, data: Map<String, JsonElement> = emptyMap()) = ship(LogLevel.WARN, event, data)
    fun error(event: String, data: Map<String, JsonElement> = emptyMap()) = ship(LogLevel.ERROR, event, data)

    private fun ship(level: LogLevel, event: String, data: Map<String, JsonElement>) {
        if (BuildConfig.DEBUG) {
            val msg = if (data.isEmpty()) event else "$event $data"
            when (level) {
                LogLevel.DEBUG -> Log.d(TAG, msg)
                LogLevel.INFO -> Log.i(TAG, msg)
                LogLevel.WARN -> Log.w(TAG, msg)
                LogLevel.ERROR -> Log.e(TAG, msg)
            }
        }
        if (level.priority < LogLevel.INFO.priority) return
        if (tokens.linkToken == null) return
        scope.launch {
            runCatching {
                val payload = buildJsonObject {
                    put("level", JsonPrimitive(level.wire))
                    put("event", JsonPrimitive(event))
                    put("timestamp", JsonPrimitive(TelemetryTimestamp.nowIso()))
                    data.forEach { (k, v) -> put(k, v) }
                }
                apiProvider().postLog(payload)
            }
        }
    }

    companion object {
        private const val TAG = "GrailPay"

        @Volatile
        private var instance: SdkLogger? = null

        fun get(): SdkLogger = instance ?: synchronized(this) {
            instance ?: SdkLogger({ NetworkFactory.api() }, NetworkFactory.tokenStore).also { instance = it }
        }
    }
}