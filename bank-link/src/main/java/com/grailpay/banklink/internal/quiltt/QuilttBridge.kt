package com.grailpay.banklink.internal.quiltt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.quiltt.connector.ConnectorSDKCallbackMetadata
import app.quiltt.connector.ConnectorSDKEventType
import app.quiltt.connector.QuilttConnector
import app.quiltt.connector.QuilttConnectorConnectConfiguration
import app.quiltt.connector.QuilttConnectorReconnectConfiguration
import app.quiltt.connector.QuilttConnectorWebView
import com.grailpay.banklink.internal.telemetry.SdkLogger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal sealed interface QuilttResult {
    data class Connected(val connectionId: String, val profileId: String?) : QuilttResult
    // Quiltt can fire onExitSuccess with a null connectionId; surface it as a real error (web SDK does too).
    data object MissingConnectionId : QuilttResult
    data object Aborted : QuilttResult
    data class Errored(val connectorId: String) : QuilttResult
}

internal fun interface QuilttResultListener {
    fun onResult(result: QuilttResult)
}

internal class QuilttBridge(
    private val context: Context,
    private val sessionToken: String,
    private val connectorId: String,
    private val appLauncherUrl: String,
    private val reconnectConnectionId: String?,
    private val listener: QuilttResultListener,
) : DefaultLifecycleObserver {

    private var connector: QuilttConnector? = null
    private var webView: QuilttConnectorWebView? = null
    private var attached = false

    // OAuth-return watchdog. Quiltt 5.2.5 occasionally lands on appLauncherUrl with no auth params
    // (channelId lost across the system-browser bounce), leaving a blank merchant page forever. Bail
    // out as Errored after a grace period so the merchant's onError fires and the activity finishes.
    private val mainHandler = Handler(Looper.getMainLooper())
    private val oauthStuckTimeout = object : Runnable {
        override fun run() {
            SdkLogger.get().error("oauth_return_stuck_timeout")
            listener.onResult(QuilttResult.Errored(connectorId = connectorId))
        }
    }

    fun createView(): View {
        check(!attached) { "QuilttBridge.createView() may only be called once per instance" }
        attached = true

        val c = QuilttConnector(context).also { it.authenticate(sessionToken) }
        val view = if (reconnectConnectionId == null) {
            c.connect(
                config = QuilttConnectorConnectConfiguration(
                    connectorId = connectorId,
                    appLauncherUrl = appLauncherUrl,
                ),
                onEvent = ::onQuilttEvent,
                onExit = ::onQuilttExitEvent,
                onExitSuccess = ::onQuilttExitSuccess,
                onExitAbort = ::onQuilttExitAbort,
                onExitError = ::onQuilttExitError,
            )
        } else {
            c.reconnect(
                config = QuilttConnectorReconnectConfiguration(
                    connectorId = connectorId,
                    appLauncherUrl = appLauncherUrl,
                    connectionId = reconnectConnectionId,
                ),
                onEvent = ::onQuilttEvent,
                onExit = ::onQuilttExitEvent,
                onExitSuccess = ::onQuilttExitSuccess,
                onExitAbort = ::onQuilttExitAbort,
                onExitError = ::onQuilttExitError,
            )
        }
        connector = c
        webView = view
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return view
    }

    override fun onDestroy(owner: LifecycleOwner) {
        destroy()
    }

    fun destroy() {
        mainHandler.removeCallbacks(oauthStuckTimeout)
        webView?.destroy()
        webView = null
        connector = null
    }

    fun loadOAuthUrl(@Suppress("UNUSED_PARAMETER") url: String) {
        // Do NOT navigate the WebView. Quiltt's connector resumes via its own polling against
        // callback.quiltt.io once we're back in the foreground; a loadUrl() here would navigate
        // away from the connector page and kill its JS before ExitSuccess fires. The Quiltt sample
        // just logs the URI and does nothing — match that. The timer below is only a safety net.
        mainHandler.removeCallbacks(oauthStuckTimeout)
        mainHandler.postDelayed(oauthStuckTimeout, OAUTH_STUCK_TIMEOUT_MS)
    }

    private fun onQuilttEvent(type: ConnectorSDKEventType, meta: ConnectorSDKCallbackMetadata) {
        mainHandler.removeCallbacks(oauthStuckTimeout)
        SdkLogger.get().info("quiltt_event_${type.name.lowercase()}", meta.toLogMap())
    }

    private fun onQuilttExitEvent(type: ConnectorSDKEventType, meta: ConnectorSDKCallbackMetadata) {
        mainHandler.removeCallbacks(oauthStuckTimeout)
        SdkLogger.get().info("quiltt_exit_${type.name.lowercase()}", meta.toLogMap())
    }

    private fun onQuilttExitSuccess(meta: ConnectorSDKCallbackMetadata) {
        // Cancel the watchdog so it can't fire a spurious Errored after this success — Quiltt
        // doesn't guarantee a single terminal callback, so the timer may still be armed.
        mainHandler.removeCallbacks(oauthStuckTimeout)
        val connectionId = meta.connectionId
        if (connectionId.isNullOrBlank()) {
            SdkLogger.get().error("quiltt_no_connection_id", meta.toLogMap())
            listener.onResult(QuilttResult.MissingConnectionId)
        } else {
            SdkLogger.get().info("quiltt_exit_success", meta.toLogMap())
            listener.onResult(QuilttResult.Connected(connectionId = connectionId, profileId = meta.profileId))
        }
    }

    private fun onQuilttExitAbort(meta: ConnectorSDKCallbackMetadata) {
        mainHandler.removeCallbacks(oauthStuckTimeout)
        SdkLogger.get().warn("quiltt_exit_abort", meta.toLogMap())
        listener.onResult(QuilttResult.Aborted)
    }

    private fun onQuilttExitError(meta: ConnectorSDKCallbackMetadata) {
        mainHandler.removeCallbacks(oauthStuckTimeout)
        SdkLogger.get().error("quiltt_exit_error", meta.toLogMap())
        // meta.connectorId is a Java platform type and can be null; fall back to the known id.
        listener.onResult(QuilttResult.Errored(connectorId = meta.connectorId ?: connectorId))
    }

    private companion object {
        // Quiltt polls after OAuth return; ExitSuccess usually fires within 30-60s. 90s leaves
        // headroom for slow networks before we give up.
        const val OAUTH_STUCK_TIMEOUT_MS = 90_000L
    }
}

private fun ConnectorSDKCallbackMetadata.toLogMap(): Map<String, JsonElement> = buildMap {
    put("connector_id", JsonPrimitive(connectorId))
    profileId?.let { put("profile_id", JsonPrimitive(it)) }
    connectionId?.let { put("connection_id", JsonPrimitive(it)) }
}