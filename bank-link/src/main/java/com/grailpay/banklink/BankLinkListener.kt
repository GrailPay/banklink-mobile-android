package com.grailpay.banklink

import com.grailpay.banklink.events.BankConnectedEvent
import com.grailpay.banklink.events.BankLinkError
import com.grailpay.banklink.events.EntityCreatedEvent
import com.grailpay.banklink.events.LinkExitEvent
import com.grailpay.banklink.events.LinkedAccountEvent

/**
 * Receives BankLink flow events. All methods have empty defaults, so override only what you need.
 *
 * Threading: every callback is invoked on the **main thread**.
 *
 * Terminal contract: exactly one of [onLinkExit] or [onError] fires per session, and it fires
 * last. Once a session has ended, no further callbacks are delivered for it.
 */
interface BankLinkListener {
    /**
     * A new entity was minted by the SDK. Fires only when the SDK created the entity itself —
     * not when you supply an existing `entityUuid` on the config.
     */
    fun onEntityCreated(event: EntityCreatedEvent) {}

    /** The bank connected successfully; [event] carries the discovered accounts. */
    fun onBankConnected(event: BankConnectedEvent) {}

    /** The user picked (or the flow saved) a default account. */
    fun onLinkedDefaultAccount(event: LinkedAccountEvent) {}

    /**
     * The flow ended without an error. Check [LinkExitEvent.status] for how it ended
     * (`COMPLETE`, `EXITED`, or `ACTION_ABANDONED`). Terminal — see the interface contract.
     */
    fun onLinkExit(event: LinkExitEvent) {}

    /** The flow ended with an error. Terminal — see the interface contract. */
    fun onError(event: BankLinkError) {}
}