package com.grailpay.banklink.ui.internal

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grailpay.banklink.events.BankAccountInfo
import com.grailpay.banklink.events.BankConnectedEvent
import com.grailpay.banklink.events.BankLinkError
import com.grailpay.banklink.events.LinkExitEvent
import com.grailpay.banklink.events.LinkExitStatus
import com.grailpay.banklink.events.LinkedAccountEvent
import com.grailpay.banklink.internal.session.ResolvedSession
import com.grailpay.banklink.internal.telemetry.SdkLogger
import com.grailpay.banklink.internal.time.MerchantTimestamp
import com.grailpay.banklink.ui.internal.widget.WireAccountSelected
import com.grailpay.banklink.ui.internal.widget.WireBankAccount
import com.grailpay.banklink.ui.internal.widget.WireBankConnected
import com.grailpay.banklink.ui.internal.widget.WireError
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Render-only ViewModel. The session was already created in the controller, so there's no
// Initializing/network or Errored state here. Mid-flow errors (Quiltt failure, widget error)
// route to the merchant via Error + Finish rather than an in-SDK retry screen.
internal class BankLinkViewModel(
    private val session: ResolvedSession,
    private val logger: SdkLogger = SdkLogger.get(),
) : ViewModel() {

    private val _state = MutableStateFlow<BankLinkUiState>(BankLinkUiState.Mounting)
    val state: StateFlow<BankLinkUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BankLinkEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val events: SharedFlow<BankLinkEvent> = _events.asSharedFlow()

    val sessionForView: ResolvedSession = session

    // Safety-net timer, armed only for stages that should resolve quickly: waiting for the widget
    // to load (grailpay:ready) and waiting for account selection after bank_connected. User-driven
    // stages (bank pick, login) are unbounded and not watched. Advancing cancels it; firing means
    // surface an error and finish.
    private var stageWatchdog: Job? = null
    private var watchdogRemainingMs: Long = 0
    private var watchdogDeadlineElapsed: Long = 0
    private var watchdogLogEvent: String? = null
    private var watchdogMessage: String? = null
    private var foreground: Boolean = true

    private fun armStageWatchdog(timeoutMs: Long, logEvent: String, message: String) {
        cancelStageWatchdog()
        watchdogRemainingMs = timeoutMs
        watchdogLogEvent = logEvent
        watchdogMessage = message
        if (foreground) startWatchdogCountdown()
    }

    private fun startWatchdogCountdown() {
        val logEvent = watchdogLogEvent ?: return
        val message = watchdogMessage ?: return
        val remaining = watchdogRemainingMs
        watchdogDeadlineElapsed = SystemClock.elapsedRealtime() + remaining
        stageWatchdog?.cancel()
        stageWatchdog = viewModelScope.launch {
            delay(remaining)
            emitErrorAndFinish(message, logEvent)
        }
    }

    private fun cancelStageWatchdog() {
        stageWatchdog?.cancel()
        stageWatchdog = null
        watchdogLogEvent = null
        watchdogMessage = null
        watchdogRemainingMs = 0
    }

    // The watchdog counts foreground time only — otherwise backgrounding the app mid-stage past the
    // timeout returns the user to a spurious error. On background we bank the remaining time and
    // stop; on return we resume from there.
    fun onForeground() {
        if (foreground) return
        foreground = true
        if (watchdogLogEvent != null && stageWatchdog == null) startWatchdogCountdown()
    }

    fun onBackground() {
        if (!foreground) return
        foreground = false
        val job = stageWatchdog ?: return
        watchdogRemainingMs = (watchdogDeadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        job.cancel()
        stageWatchdog = null
    }

    init {
        // Nothing to load, so go straight to QuilttLaunching.
        _state.value = BankLinkUiState.QuilttLaunching(
            sessionToken = session.sessionToken,
            connectorId = session.connectorId,
            appLauncherUrl = session.appLauncherUrl,
            reconnectConnectionId = session.reconnectConnectionId,
        )
    }

    fun onQuilttConnected(connectionId: String) {
        logger.info("bank_connected")
        _state.value = BankLinkUiState.WidgetLaunching(
            connectionId = connectionId,
            linkToken = session.linkToken,
            entityUuid = session.entityUuid,
            widgetUrl = session.widgetBaseUrl,
        )
        // The widget loads in an iframe, so a failed load never raises a WebView main-frame error.
        // Guard with a timeout: no grailpay:ready means a blank screen, so error out instead of hanging.
        armStageWatchdog(
            WIDGET_LOAD_TIMEOUT_MS,
            "widget_load_timeout",
            "Couldn't load the bank connection screen. Please check your connection and try again.",
        )
    }

    fun onWidgetReady() {
        // Widget owns the UI now (bank pick / login are unbounded), so drop the load watchdog
        // until bank_connected.
        cancelStageWatchdog()
    }

    fun onQuilttMissingConnectionId() {
        emitErrorAndFinish("Quiltt did not return a connectionId", "quiltt_no_connection_id")
    }

    fun onQuilttAborted() {
        cancelStageWatchdog()
        tryEmit(
            BankLinkEvent.LinkExit(
                LinkExitEvent(
                    entity_uuid = session.entityUuid,
                    entity_user_uuid = session.entityUserUuid,
                    client_reference_id = session.clientReferenceId,
                    status = LinkExitStatus.ACTION_ABANDONED,
                    exited_at = MerchantTimestamp.now(),
                ),
            ),
        )
        tryEmit(BankLinkEvent.Finish)
    }

    fun onQuilttErrored() {
        emitErrorAndFinish(
            "Something went wrong while connecting to your bank. Please try again.",
            "quiltt_error",
        )
    }

    fun onWidgetBankConnected(payload: WireBankConnected) {
        // Awaiting the default-account selection/save, which should be quick — watch in case
        // account_selected/error/exit never follows (widget stall, server hang).
        armStageWatchdog(
            ACCOUNT_SELECT_TIMEOUT_MS,
            "account_select_timeout",
            "The bank connection didn't finish. Please try again.",
        )
        // Match web SDK: keep only latest=true accounts, but fall back to all when none are flagged
        // (older widgets omit the flag). latest/isDefault are stripped before the merchant sees them.
        val latestOnly = payload.bankAccounts.filter { it.latest == true }
        val filtered = if (latestOnly.isNotEmpty()) latestOnly else payload.bankAccounts
        tryEmit(
            BankLinkEvent.BankConnected(
                BankConnectedEvent(
                    entity_uuid = payload.entityUuid,
                    entity_user_uuid = session.entityUserUuid,
                    client_reference_id = session.clientReferenceId,
                    bank_accounts = filtered.map { it.toMerchantAccount() },
                ),
            ),
        )
    }

    fun onWidgetAccountSelected(payload: WireAccountSelected) {
        cancelStageWatchdog()
        tryEmit(
            BankLinkEvent.LinkedDefaultAccount(
                LinkedAccountEvent(
                    entity_uuid = payload.entityUuid,
                    entity_user_uuid = session.entityUserUuid,
                    client_reference_id = session.clientReferenceId,
                    bank_account = payload.bankAccount.toMerchantAccount(),
                ),
            ),
        )
        tryEmit(
            BankLinkEvent.LinkExit(
                LinkExitEvent(
                    entity_uuid = payload.entityUuid,
                    entity_user_uuid = session.entityUserUuid,
                    client_reference_id = session.clientReferenceId,
                    status = LinkExitStatus.COMPLETE,
                    exited_at = MerchantTimestamp.now(),
                ),
            ),
        )
        tryEmit(BankLinkEvent.Finish)
    }

    fun onWidgetError(payload: WireError) {
        cancelStageWatchdog()
        tryEmit(
            BankLinkEvent.Error(
                BankLinkError(
                    error_message = payload.errorMessage,
                    entity_uuid = payload.entityUuid ?: session.entityUuid,
                    entity_user_uuid = session.entityUserUuid.takeIf { it.isNotBlank() },
                    client_reference_id = session.clientReferenceId,
                    failed_at = payload.failedAt ?: MerchantTimestamp.now(),
                ),
            ),
        )
        tryEmit(BankLinkEvent.Finish)
    }

    fun onWidgetExit() {
        cancelStageWatchdog()
        tryEmit(
            BankLinkEvent.LinkExit(
                LinkExitEvent(
                    entity_uuid = session.entityUuid,
                    entity_user_uuid = session.entityUserUuid,
                    client_reference_id = session.clientReferenceId,
                    status = LinkExitStatus.EXITED,
                    exited_at = MerchantTimestamp.now(),
                ),
            ),
        )
        tryEmit(BankLinkEvent.Finish)
    }

    fun onClose() = onWidgetExit()

    private fun emitErrorAndFinish(message: String, logEvent: String) {
        cancelStageWatchdog()
        logger.error(logEvent)
        tryEmit(
            BankLinkEvent.Error(
                BankLinkError(
                    error_message = message,
                    entity_uuid = session.entityUuid,
                    entity_user_uuid = session.entityUserUuid.takeIf { it.isNotBlank() },
                    client_reference_id = session.clientReferenceId,
                    failed_at = MerchantTimestamp.now(),
                ),
            ),
        )
        tryEmit(BankLinkEvent.Finish)
    }

    private fun tryEmit(event: BankLinkEvent) {
        // With extraBufferCapacity=8 this only fails under sustained backpressure (8+ queued).
        // The flow emits ~4-5 events total, so a drop is unrealistic — log it rather than swallow.
        if (!_events.tryEmit(event)) {
            logger.warn("merchant_event_dropped")
        }
    }

    private companion object {
        // Generous windows — not normal-path deadlines, only fire when the widget is stuck. Load
        // normally takes a few seconds, the default-account save well under a second.
        const val WIDGET_LOAD_TIMEOUT_MS = 30_000L
        const val ACCOUNT_SELECT_TIMEOUT_MS = 60_000L
    }

}

private fun WireBankAccount.toMerchantAccount(): BankAccountInfo = BankAccountInfo(
    account_uuid = accountUuid,
    account_number_last4 = accountNumberLast4,
    routing_number_last4 = routingNumberLast4,
    account_name = accountName.orEmpty(),
    account_type = accountType.orEmpty(),
    institution_name = institutionName.orEmpty(),
    aggregator = aggregator.orEmpty(),
    status = status.orEmpty(),
    created_at = createdAt.orEmpty(),
    updated_at = updatedAt.orEmpty(),
)