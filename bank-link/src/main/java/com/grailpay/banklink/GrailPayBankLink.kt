package com.grailpay.banklink

import android.content.Intent
import androidx.activity.ComponentActivity
import com.grailpay.banklink.internal.BankLinkController

/**
 * Public entry point, matching the web SDK (`window.grailpay`): [init] mints the session and
 * auto-opens the UI on success. Init errors never paint a screen — they go to
 * [BankLinkListener.onError].
 *
 * All entry points must be called from the main thread.
 */
object GrailPayBankLink {

    private val controller = BankLinkController()

    // ---- Internal hooks (BankLinkActivity / BankLinkContent) ----------------------------

    internal fun activeListener(): BankLinkListener? = controller.currentListener()

    internal fun activeSession() = controller.pendingSession()

    internal fun registerActivity(activity: BankLinkActivity) = controller.registerActivity(activity)
    internal fun unregisterActivity(activity: BankLinkActivity) = controller.unregisterActivity(activity)
    internal fun onActivityFinished(activity: BankLinkActivity) = controller.onActivityFinished(activity)

    internal fun registerBridge(bridge: com.grailpay.banklink.internal.quiltt.QuilttBridge) =
        controller.registerBridge(bridge)
    internal fun unregisterBridge(bridge: com.grailpay.banklink.internal.quiltt.QuilttBridge) =
        controller.unregisterBridge(bridge)

    internal fun emitEntityCreated(event: com.grailpay.banklink.events.EntityCreatedEvent) =
        controller.emitEntityCreated(event)
    internal fun emitBankConnected(event: com.grailpay.banklink.events.BankConnectedEvent) =
        controller.emitBankConnected(event)
    internal fun emitLinkedDefaultAccount(event: com.grailpay.banklink.events.LinkedAccountEvent) =
        controller.emitLinkedDefaultAccount(event)
    internal fun emitLinkExit(event: com.grailpay.banklink.events.LinkExitEvent) =
        controller.emitLinkExit(event)
    internal fun emitError(event: com.grailpay.banklink.events.BankLinkError) =
        controller.emitError(event)

    // ---- Public API ---------------------------------------------------------------------

    /**
     * Mint a session and, on success, open the bank-connect UI. On failure, [listener]'s
     * [BankLinkListener.onError] fires and no UI is shown.
     */
    @JvmStatic
    fun init(
        activity: ComponentActivity,
        config: BankLinkConfig,
        listener: BankLinkListener,
    ) {
        controller.init(activity, config, listener)
    }

    /**
     * Open the bank-connect UI for a previously-minted session. Idempotent: a no-op while
     * the activity is already open. If [init] has not yet succeeded, fires `onError`.
     */
    @JvmStatic
    fun open() {
        controller.open()
    }

    /**
     * Cancel an in-flight [init], close an open activity, or both. Fires
     * [BankLinkListener.onLinkExit] with status `EXITED` exactly once.
     */
    @JvmStatic
    fun close() {
        controller.close()
    }

    /**
     * Relink a broken bank connection. Validates `connectionId` on the supplied config and
     * then calls [init].
     */
    @JvmStatic
    fun relink(
        activity: ComponentActivity,
        config: BankLinkConfig,
        listener: BankLinkListener,
    ) {
        controller.relink(activity, config, listener)
    }

    /**
     * Forwards an OAuth-return deep link into the active connector WebView. Returns `false` if
     * no session is running (e.g. process death during the OAuth bounce) — restart the flow if so.
     */
    @JvmStatic
    fun handleOAuthReturn(intent: Intent): Boolean = controller.handleOAuthReturn(intent)

    // ---- Deprecated back-compat ---------------------------------------------------------

    /** Legacy entry point. Same as [init]. */
    @Deprecated(
        message = "Use init() — open() now auto-fires after init succeeds, matching the web SDK.",
        replaceWith = ReplaceWith("GrailPayBankLink.init(activity, config, listener)"),
    )
    @JvmStatic
    fun open(activity: ComponentActivity, config: BankLinkConfig, listener: BankLinkListener) {
        init(activity, config, listener)
    }
}