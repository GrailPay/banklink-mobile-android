package com.grailpay.banklink.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grailpay.banklink.GrailPayBankLink
import com.grailpay.banklink.internal.quiltt.QuilttBridge
import com.grailpay.banklink.internal.quiltt.QuilttResult
import com.grailpay.banklink.internal.quiltt.QuilttResultListener
import com.grailpay.banklink.internal.session.ResolvedSession
import com.grailpay.banklink.ui.internal.BankLinkUiState
import com.grailpay.banklink.ui.internal.BankLinkViewModel
import com.grailpay.banklink.ui.internal.BankLinkEvent
import com.grailpay.banklink.ui.internal.widget.WidgetMessageListener
import com.grailpay.banklink.ui.internal.widget.BankLinkWidgetView

@Composable
internal fun BankLinkContent(
    session: ResolvedSession,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BrandingTheme(session.branding) {
        val vm: BankLinkViewModel = viewModel(factory = SessionViewModelFactory(session))
        val state by vm.state.collectAsState()

        LaunchedEffect(vm) {
            vm.events.collect { event ->
                when (event) {
                    is BankLinkEvent.EntityCreated -> GrailPayBankLink.emitEntityCreated(event.payload)
                    is BankLinkEvent.BankConnected -> GrailPayBankLink.emitBankConnected(event.payload)
                    is BankLinkEvent.LinkedDefaultAccount -> GrailPayBankLink.emitLinkedDefaultAccount(event.payload)
                    is BankLinkEvent.LinkExit -> GrailPayBankLink.emitLinkExit(event.payload)
                    is BankLinkEvent.Error -> GrailPayBankLink.emitError(event.payload)
                    BankLinkEvent.Finish -> onFinish()
                }
            }
        }

        // Pause stage watchdogs while backgrounded — otherwise leaving mid-flow returns to a spurious timeout.
        val lifecycleActivity = LocalActivity.current as? ComponentActivity
        DisposableEffect(lifecycleActivity, vm) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> vm.onForeground()
                    Lifecycle.Event.ON_PAUSE -> vm.onBackground()
                    else -> Unit
                }
            }
            lifecycleActivity?.lifecycle?.addObserver(observer)
            onDispose { lifecycleActivity?.lifecycle?.removeObserver(observer) }
        }

        BackHandler { vm.onClose() }

        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                // Activity is edge-to-edge; without the bottom inset the WebView draws behind the
                // nav bar and clips footer buttons. Surface background still bleeds behind both bars.
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            when (val s = state) {
                BankLinkUiState.Mounting -> Unit

                is BankLinkUiState.QuilttLaunching -> QuilttHost(s, vm)

                is BankLinkUiState.WidgetLaunching -> BankLinkWidgetView(
                    state = s,
                    branding = session.branding,
                    clientReferenceId = session.clientReferenceId,
                    billingMerchantUuid = session.billingMerchantUuid,
                    billingProcessorMid = session.billingProcessorMid,
                    // Legacy addJavascriptInterface delivers on a background thread, so marshal every
                    // callback to main before touching the ViewModel. (addWebMessageListener is already
                    // on main — runOnUiThread runs inline there.)
                    listener = remember(vm, lifecycleActivity) {
                        fun onMain(block: () -> Unit) {
                            lifecycleActivity?.runOnUiThread(block) ?: block()
                        }
                        object : WidgetMessageListener {
                            override fun onWidgetReady() = onMain { vm.onWidgetReady() }
                            override fun onBankConnected(payload: com.grailpay.banklink.ui.internal.widget.WireBankConnected) =
                                onMain { vm.onWidgetBankConnected(payload) }
                            override fun onAccountSelected(payload: com.grailpay.banklink.ui.internal.widget.WireAccountSelected) =
                                onMain { vm.onWidgetAccountSelected(payload) }
                            override fun onWidgetError(payload: com.grailpay.banklink.ui.internal.widget.WireError) =
                                onMain { vm.onWidgetError(payload) }
                            override fun onWidgetExit() = onMain { vm.onWidgetExit() }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun QuilttHost(
    state: BankLinkUiState.QuilttLaunching,
    vm: BankLinkViewModel,
) {
    val activity = LocalActivity.current as? ComponentActivity
        ?: error("BankLinkContent must be hosted inside a ComponentActivity")

    val bridge = remember(state) {
        QuilttBridge(
            context = activity,
            sessionToken = state.sessionToken,
            connectorId = state.connectorId,
            appLauncherUrl = state.appLauncherUrl,
            reconnectConnectionId = state.reconnectConnectionId,
            listener = QuilttResultListener { result ->
                activity.runOnUiThread {
                    when (result) {
                        is QuilttResult.Connected -> vm.onQuilttConnected(result.connectionId)
                        QuilttResult.MissingConnectionId -> vm.onQuilttMissingConnectionId()
                        QuilttResult.Aborted -> vm.onQuilttAborted()
                        is QuilttResult.Errored -> vm.onQuilttErrored()
                    }
                }
            },
        )
    }

    DisposableEffect(bridge) {
        activity.lifecycle.addObserver(bridge)
        GrailPayBankLink.registerBridge(bridge)
        onDispose {
            GrailPayBankLink.unregisterBridge(bridge)
            activity.lifecycle.removeObserver(bridge)
            bridge.destroy()
        }
    }

    AndroidView(
        factory = { bridge.createView() },
        modifier = Modifier.fillMaxSize(),
    )
}

private class SessionViewModelFactory(
    private val session: ResolvedSession,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BankLinkViewModel(session) as T
    }
}