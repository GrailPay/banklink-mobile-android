package com.grailpay.banklink.ui.internal

internal sealed interface BankLinkUiState {

    // Brief gap between activity onCreate and Quiltt mounting its WebView. We render a plain tinted
    // surface (no spinner) so it doesn't double up with Quiltt's own loading indicator.
    data object Mounting : BankLinkUiState

    data class QuilttLaunching(
        val sessionToken: String,
        val connectorId: String,
        val appLauncherUrl: String,
        val reconnectConnectionId: String?,
    ) : BankLinkUiState

    // Entered after Quiltt's onExitSuccess. The widget owns all UI from here on (account list,
    // selection, default save, success, error recovery); we just listen for grailpay:* events.
    data class WidgetLaunching(
        val connectionId: String,
        val linkToken: String,
        val entityUuid: String,
        val widgetUrl: String,
    ) : BankLinkUiState
}