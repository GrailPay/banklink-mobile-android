package com.grailpay.banklink.ui.internal

import com.grailpay.banklink.events.BankConnectedEvent
import com.grailpay.banklink.events.BankLinkError
import com.grailpay.banklink.events.EntityCreatedEvent
import com.grailpay.banklink.events.LinkExitEvent
import com.grailpay.banklink.events.LinkedAccountEvent

internal sealed interface BankLinkEvent {
    data class EntityCreated(val payload: EntityCreatedEvent) : BankLinkEvent
    data class BankConnected(val payload: BankConnectedEvent) : BankLinkEvent
    data class LinkedDefaultAccount(val payload: LinkedAccountEvent) : BankLinkEvent
    data class LinkExit(val payload: LinkExitEvent) : BankLinkEvent
    data class Error(val payload: BankLinkError) : BankLinkEvent
    data object Finish : BankLinkEvent
}