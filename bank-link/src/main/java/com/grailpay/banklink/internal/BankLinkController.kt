package com.grailpay.banklink.internal

import android.content.Intent
import android.os.Looper
import androidx.activity.ComponentActivity
import com.grailpay.banklink.BankLinkActivity
import com.grailpay.banklink.BankLinkConfig
import com.grailpay.banklink.BankLinkListener
import com.grailpay.banklink.events.BankConnectedEvent
import com.grailpay.banklink.events.BankLinkError
import com.grailpay.banklink.events.EntityCreatedEvent
import com.grailpay.banklink.events.LinkExitEvent
import com.grailpay.banklink.events.LinkExitStatus
import com.grailpay.banklink.events.LinkedAccountEvent
import com.grailpay.banklink.internal.net.NetworkFactory
import com.grailpay.banklink.internal.quiltt.QuilttBridge
import com.grailpay.banklink.internal.session.ResolvedSession
import com.grailpay.banklink.internal.session.SessionInitializer
import com.grailpay.banklink.internal.telemetry.SdkLogger
import com.grailpay.banklink.internal.time.MerchantTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.lang.ref.WeakReference

// Holds all SDK runtime state. Everything here runs on the main thread; listener callbacks are
// posted back to main too, since merchants touch UI from inside them.
internal class BankLinkController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val logger get() = SdkLogger.get()

    private sealed class State {
        data object Uninitialized : State()
        data class Initializing(val job: Job, val config: BankLinkConfig) : State()
        data class Ready(val session: ResolvedSession) : State()
        data class ActivityOpen(val session: ResolvedSession) : State()
    }

    private var state: State = State.Uninitialized
    private var listener: BankLinkListener? = null

    // Exactly one of onLinkExit/onError should reach the merchant per session. Quiltt, the OAuth
    // watchdog and the widget can all fire terminal events (sometimes more than once), so we latch
    // here: first one wins, everything after is dropped. Reset per session in rebind().
    private var sessionTerminated = false

    private var hostActivity: WeakReference<ComponentActivity>? = null
    private var bankLinkActivity: WeakReference<BankLinkActivity>? = null
    private var bridge: WeakReference<QuilttBridge>? = null

    // ---- Public API (called from GrailPayBankLink) --------------------------------------

    fun init(activity: ComponentActivity, config: BankLinkConfig, listener: BankLinkListener) {
        ensureMainThread()
        when (state) {
            is State.Initializing -> {
                logger.warn("init_already_in_progress")
                return
            }
            is State.Ready -> {
                // Already minted — a second init() would overwrite the listener and leak the
                // first link token. Reject like the other live states.
                logger.warn("init_while_session_ready")
                return
            }
            is State.ActivityOpen -> {
                logger.warn("init_while_activity_open")
                return
            }
            else -> Unit
        }

        rebind(activity, listener)

        // Validate branding here so failures route through onError instead of throwing in the
        // merchant's setup code.
        val brandingError = runCatching { config.branding?.validate() }.exceptionOrNull()
        if (brandingError != null) {
            val message = brandingError.message?.takeIf { it.isNotBlank() } ?: "Invalid branding"
            logger.error("init_validation_failed")
            emitError(
                message = message,
                entityUuid = null,
                entityUserUuid = null,
                clientReferenceId = config.clientReferenceId,
            )
            resetToUninitialized()
            return
        }

        // Must be set before SessionInitializer/telemetry build their Retrofit clients.
        NetworkFactory.baseUrl = config.baseUrl
        NetworkFactory.tokenStore.merchantToken = config.merchantToken
        // No linkToken until createSession returns; clear any stale one from a prior session.
        NetworkFactory.tokenStore.linkToken = null

        // LAZY + start() after state is set: the non-suspending manual path would otherwise run inline and set Ready before Initializing.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val result = runCatching {
                SessionInitializer(NetworkFactory.api()).initialize(config)
            }
            result.fold(
                onSuccess = { session -> onInitSuccess(session) },
                onFailure = { error -> onInitFailure(error, config) },
            )
        }
        state = State.Initializing(job, config)
        job.start()
    }

    fun open() {
        ensureMainThread()
        val ready = state as? State.Ready ?: run {
            when (state) {
                is State.ActivityOpen -> Unit
                is State.Initializing -> logger.warn("open_during_init")
                else -> {
                    val message = "init() must be called before open()"
                    logger.error("open_before_init")
                    emitError(message, entityUuid = null, entityUserUuid = null, clientReferenceId = null)
                }
            }
            return
        }
        launchActivity(ready.session)
    }

    fun close() {
        ensureMainThread()
        when (val s = state) {
            is State.Initializing -> {
                s.job.cancel()
                emitLinkExit(
                    entityUuid = null,
                    entityUserUuid = null,
                    clientReferenceId = s.config.clientReferenceId,
                    status = LinkExitStatus.EXITED,
                )
                resetToUninitialized()
            }
            is State.Ready -> {
                emitLinkExit(
                    entityUuid = s.session.entityUuid,
                    entityUserUuid = s.session.entityUserUuid,
                    clientReferenceId = s.session.clientReferenceId,
                    status = LinkExitStatus.EXITED,
                )
                resetToUninitialized()
            }
            is State.ActivityOpen -> {
                // finish() doesn't run through the ViewModel's exit path, so emit EXITED here
                // (onActivityFinished only resets state). The terminal latch covers a racing exit.
                emitLinkExit(
                    entityUuid = s.session.entityUuid,
                    entityUserUuid = s.session.entityUserUuid,
                    clientReferenceId = s.session.clientReferenceId,
                    status = LinkExitStatus.EXITED,
                )
                bankLinkActivity?.get()?.finish()
            }
            State.Uninitialized -> Unit
        }
    }

    fun relink(activity: ComponentActivity, config: BankLinkConfig, listener: BankLinkListener) {
        ensureMainThread()
        if (config.connectionId.isNullOrBlank()) {
            // Bind listener so onError is delivered, then surface the validation failure.
            rebind(activity, listener)
            emitError(
                message = "connection_id is required for relink",
                entityUuid = null,
                entityUserUuid = null,
                clientReferenceId = config.clientReferenceId,
            )
            resetToUninitialized()
            return
        }
        init(activity, config, listener)
    }

    fun handleOAuthReturn(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        val live = bridge?.get() ?: run {
            logger.warn("oauth_return_no_active_bridge")
            return false
        }
        logger.info("oauth_return")
        live.loadOAuthUrl(uri.toString())
        return true
    }

    // ---- Hooks consumed by BankLinkActivity / BankLinkContent ---------------------------

    internal fun pendingSession(): ResolvedSession? = when (val s = state) {
        is State.Ready -> s.session
        is State.ActivityOpen -> s.session
        else -> null
    }

    internal fun currentListener(): BankLinkListener? = listener

    internal fun registerActivity(activity: BankLinkActivity) {
        bankLinkActivity = WeakReference(activity)
        state = (state as? State.Ready)?.let { State.ActivityOpen(it.session) } ?: state
    }

    internal fun unregisterActivity(activity: BankLinkActivity) {
        if (bankLinkActivity?.get() === activity) {
            bankLinkActivity = null
        }
    }

    internal fun onActivityFinished(activity: BankLinkActivity) {
        if (bankLinkActivity?.get() !== activity) return
        bankLinkActivity = null
        bridge = null
        if (state is State.ActivityOpen) {
            resetToUninitialized()
        }
    }

    internal fun registerBridge(b: QuilttBridge) {
        bridge = WeakReference(b)
    }

    internal fun unregisterBridge(b: QuilttBridge) {
        if (bridge?.get() === b) bridge = null
    }

    // Merchant callbacks may throw — log and swallow so a bad handler can't break the flow, but
    // rethrow CancellationException so coroutine cancellation isn't masked. The listener is
    // captured here (on main, before the coroutine runs) so a concurrent reset can't drop a
    // callback that's already on its way.
    private fun dispatch(callbackName: String, block: BankLinkListener.() -> Unit) {
        val target = listener ?: run {
            logger.warn("merchant_callback_no_listener", mapOf("callback" to JsonPrimitive(callbackName)))
            return
        }
        // Main (not .immediate) so the callback runs on a fresh loop turn, never re-entrantly
        // inside the state transition that emitted it.
        scope.launch(Dispatchers.Main) {
            try {
                target.block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Exception, not Throwable: a throwing callback shouldn't crash the SDK, but fatal Errors (OOM) still propagate.
                logger.warn("merchant_callback_threw", mapOf("callback" to JsonPrimitive(callbackName)))
            }
        }
    }

    private fun guardTerminated(callbackName: String): Boolean {
        if (sessionTerminated) {
            logger.info("event_suppressed_after_terminal", mapOf("callback" to JsonPrimitive(callbackName)))
            return true
        }
        return false
    }

    internal fun emitEntityCreated(event: EntityCreatedEvent) {
        if (guardTerminated("onEntityCreated")) return
        dispatch("onEntityCreated") { onEntityCreated(event) }
    }

    internal fun emitBankConnected(event: BankConnectedEvent) {
        if (guardTerminated("onBankConnected")) return
        dispatch("onBankConnected") { onBankConnected(event) }
    }

    internal fun emitLinkedDefaultAccount(event: LinkedAccountEvent) {
        if (guardTerminated("onLinkedDefaultAccount")) return
        dispatch("onLinkedDefaultAccount") { onLinkedDefaultAccount(event) }
    }

    internal fun emitLinkExit(event: LinkExitEvent) {
        if (guardTerminated("onLinkExit")) return
        sessionTerminated = true
        dispatch("onLinkExit") { onLinkExit(event) }
    }

    internal fun emitError(event: BankLinkError) {
        if (guardTerminated("onError")) return
        sessionTerminated = true
        dispatch("onError") { onError(event) }
    }

    // ---- Internals ----------------------------------------------------------------------

    private fun onInitSuccess(session: ResolvedSession) {
        NetworkFactory.tokenStore.linkToken = session.linkToken
        state = State.Ready(session)
        logger.info("sdk_init_complete")
        // Auto-open after a successful init (web parity). Hold onEntityCreated until the activity
        // has actually launched, otherwise a dead host would fire both onError and onEntityCreated.
        val launched = launchActivity(session)
        if (launched && session.mintedNewEntity) {
            emitEntityCreated(
                EntityCreatedEvent(
                    entity_uuid = session.entityUuid,
                    entity_user_uuid = session.entityUserUuid,
                    entity_type = session.entityType,
                    client_reference_id = session.clientReferenceId,
                    created_at = session.createdAt,
                ),
            )
        }
    }

    private fun onInitFailure(error: Throwable, config: BankLinkConfig) {
        if (error is CancellationException) {
            // close() during init already emitted EXITED; just reset.
            resetToUninitialized()
            return
        }
        val message = error.message?.takeIf { it.isNotBlank() } ?: "Failed to mint session"
        logger.error("init_failed")
        emitError(
            message = message,
            entityUuid = null,
            entityUserUuid = null,
            clientReferenceId = config.clientReferenceId,
        )
        resetToUninitialized()
    }

    private fun launchActivity(session: ResolvedSession): Boolean {
        val host = hostActivity?.get() ?: run {
            logger.error("open_no_host_activity")
            emitError(
                message = "Host activity is no longer available",
                entityUuid = session.entityUuid,
                entityUserUuid = session.entityUserUuid,
                clientReferenceId = session.clientReferenceId,
            )
            resetToUninitialized()
            return false
        }
        // State is set to ActivityOpen before the activity starts, so the activity reads the live
        // session from pendingSession() instead of an Intent extra. Keeps tokens out of the Intent,
        // which the framework can persist to disk for task/recents state.
        state = State.ActivityOpen(session)
        host.startActivity(Intent(host, BankLinkActivity::class.java))
        return true
    }

    private fun rebind(activity: ComponentActivity, listener: BankLinkListener) {
        this.listener = listener
        this.hostActivity = WeakReference(activity)
        // New session — clear the terminal latch so its first onLinkExit/onError fires.
        sessionTerminated = false
    }

    private fun resetToUninitialized() {
        state = State.Uninitialized
        listener = null
        sessionTerminated = false
        hostActivity = null
        bankLinkActivity = null
        bridge = null
        NetworkFactory.tokenStore.clear()
    }

    private fun emitError(
        message: String,
        entityUuid: String?,
        entityUserUuid: String?,
        clientReferenceId: String?,
    ) {
        emitError(
            BankLinkError(
                error_message = message,
                entity_uuid = entityUuid,
                entity_user_uuid = entityUserUuid?.takeIf { it.isNotBlank() },
                client_reference_id = clientReferenceId,
                failed_at = MerchantTimestamp.now(),
            ),
        )
    }

    private fun emitLinkExit(
        entityUuid: String?,
        entityUserUuid: String?,
        clientReferenceId: String?,
        status: LinkExitStatus,
    ) {
        emitLinkExit(
            LinkExitEvent(
                entity_uuid = entityUuid.orEmpty(),
                entity_user_uuid = entityUserUuid.orEmpty(),
                client_reference_id = clientReferenceId,
                status = status,
                exited_at = MerchantTimestamp.now(),
            ),
        )
    }

    private fun ensureMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "GrailPayBankLink must be called from the main thread"
        }
    }
}
