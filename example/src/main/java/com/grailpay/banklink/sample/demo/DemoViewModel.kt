package com.grailpay.banklink.sample.demo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grailpay.banklink.sample.BuildConfig
import com.grailpay.banklink.BankLinkActivity
import com.grailpay.banklink.BankLinkConfig
import com.grailpay.banklink.BankLinkListener
import com.grailpay.banklink.BrandTheme
import com.grailpay.banklink.Branding
import com.grailpay.banklink.EntityType
import com.grailpay.banklink.GrailPayBankLink
import com.grailpay.banklink.events.BankConnectedEvent
import com.grailpay.banklink.events.BankLinkError
import com.grailpay.banklink.events.EntityCreatedEvent
import com.grailpay.banklink.events.LinkExitEvent
import com.grailpay.banklink.events.LinkedAccountEvent
import com.grailpay.banklink.sample.MainActivity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(
    FlowPreview::class,
    kotlinx.serialization.ExperimentalSerializationApi::class,
)
class DemoViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = DemoPreferences(app)
    private val json = Json { prettyPrint = true; prettyPrintIndent = "    " }

    private val _form = MutableStateFlow(DemoFormState())
    val form: StateFlow<DemoFormState> = _form.asStateFlow()

    private val _responses = MutableStateFlow<Map<CallbackKind, String>>(emptyMap())
    val responses: StateFlow<Map<CallbackKind, String>> = _responses.asStateFlow()

    // Mirrors demo.html's button state: disabled + "Initializing..." while
    // `await GrailPay.BankLink.init()` is pending, re-enabled when it resolves.
    // init() returns Unit (nothing to await), so awaitConnectUi() reproduces the
    // same window by watching for the SDK's BankLinkActivity to reach the
    // foreground — see launch() / settleInit().
    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    // Process-wide activity watcher, live only between launch() and settleInit().
    private var connectUiWatcher: Application.ActivityLifecycleCallbacks? = null

    init {
        viewModelScope.launch { _form.value = prefs.observe().first() }
        // Persist form changes 500ms after the last edit.
        viewModelScope.launch {
            _form.drop(1).debounce(500).collect { prefs.save(it) }
        }
    }

    fun updateForm(transform: (DemoFormState) -> DemoFormState) = _form.update(transform)

    fun clearAllResponses() {
        _responses.value = emptyMap()
    }

    fun clearResponse(kind: CallbackKind) {
        _responses.update { it - kind }
    }

    fun clearSavedInputs() {
        viewModelScope.launch {
            prefs.clear()
            _form.value = DemoFormState()
        }
    }

    fun launch(activity: ComponentActivity) {
        val state = _form.value
        if (state.token.isBlank()) return
        val config = buildConfig(state)
        _isInitializing.value = true
        awaitConnectUi()
        GrailPayBankLink.init(activity, config, listener)
    }

    // The faithful Android equivalent of demo.html's `await init()`. init() returns
    // Unit, so there is nothing to await — instead we watch every activity in the
    // process and clear the loader when the SDK's BankLinkActivity reaches the
    // foreground (init succeeded, on any entity-type path). onError covers init
    // failure; MainActivity resuming while still "initializing" is the failsafe for
    // the rare case where Android blocks the background activity start, so the
    // loader can never hang. Backgrounding the demo never trips it — only
    // BankLinkActivity specifically does.
    private fun awaitConnectUi() {
        val app = getApplication<Application>()
        connectUiWatcher?.let(app::unregisterActivityLifecycleCallbacks)
        val watcher = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                when (activity) {
                    is BankLinkActivity -> settleInit()
                    is MainActivity -> if (_isInitializing.value) settleInit()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        connectUiWatcher = watcher
        app.registerActivityLifecycleCallbacks(watcher)
    }

    // init() has resolved (connect UI is up, or it failed) — clear the loader and
    // tear the watcher down so it fires exactly once.
    private fun settleInit() {
        _isInitializing.value = false
        connectUiWatcher?.let { getApplication<Application>().unregisterActivityLifecycleCallbacks(it) }
        connectUiWatcher = null
    }

    // Backend URLs are baked into the SDK at build time (BuildConfig.BANKLINK_API_BASE_URL,
    // BuildConfig.WIDGET_URL) — matching the web SDK's bundle-per-env model. Want to test
    // a different env? Build the demo with that variant: ./gradlew :app:assembleStaging etc.
    private fun buildConfig(state: DemoFormState): BankLinkConfig {
        val builder = BankLinkConfig.Builder(state.token)
            .appLauncherUrl(state.appLauncherUrl)
        // Debug builds point the SDK at the GrailPay-internal QA/staging host (injected from
        // local.properties). Release builds omit it and use the SDK's production default.
        if (BuildConfig.DEBUG && BuildConfig.QA_BASE_URL.isNotBlank()) {
            builder.testEnvironmentUrl(BuildConfig.QA_BASE_URL)
        }
        if (state.entityUuid.isNotBlank()) {
            builder.entityUuid(state.entityUuid)
        } else {
            builder.entityType(state.entityType.toSdk())
        }
        if (state.clientReferenceId.isNotBlank()) builder.clientReferenceId(state.clientReferenceId)
        if (state.billingMerchantUuid.isNotBlank() || state.billingProcessorMid.isNotBlank()) {
            builder.billing(
                merchantUuid = state.billingMerchantUuid.takeIf { it.isNotBlank() },
                processorMid = state.billingProcessorMid.takeIf { it.isNotBlank() },
            )
        }
        val branding = buildBranding(state)
        if (branding != null) builder.branding(branding)
        return builder.build()
    }

    private fun buildBranding(state: DemoFormState): Branding? {
        val name = state.brandingCompanyName.takeIf { it.isNotBlank() }
        val logo = state.brandingLogoUrl.takeIf { it.isNotBlank() }
        val color = state.brandingPrimaryColor.takeIf { it.isNotBlank() }
        if (name == null && logo == null && color == null) return null
        return Branding(theme = null, primaryColor = color, logo = logo, companyName = name)
    }

    private fun EntityTypeChoice.toSdk(): EntityType = when (this) {
        EntityTypeChoice.PERSON -> EntityType.PERSON
        EntityTypeChoice.BUSINESS -> EntityType.BUSINESS
    }

    // Events are @Serializable with snake_case property names, so one call serializes
    // them to the exact JSON shape a merchant receives on web. Mirrors demo.html's
    // `JSON.stringify(data, null, 4)` — same input, same output.
    private val listener = object : BankLinkListener {
        override fun onError(event: BankLinkError) = render(CallbackKind.ERROR, event)
        override fun onEntityCreated(event: EntityCreatedEvent) = render(CallbackKind.ENTITY_CREATED, event)
        override fun onLinkedDefaultAccount(event: LinkedAccountEvent) = render(CallbackKind.LINKED_DEFAULT_ACCOUNT, event)
        override fun onLinkExit(event: LinkExitEvent) = render(CallbackKind.LINK_EXIT, event)
        override fun onBankConnected(event: BankConnectedEvent) = render(CallbackKind.BANK_CONNECTED, event)
    }

    private inline fun <reified T> render(kind: CallbackKind, event: T) {
        Log.d(TAG, "$kind raw=$event")
        val rendered = json.encodeToString(event)
        Log.d(TAG, "[$kind] $rendered")
        _responses.update { it + (kind to rendered) }
        // onError = init failed before any SDK activity opens — settle the loader.
        if (kind == CallbackKind.ERROR) settleInit()
    }

    override fun onCleared() {
        connectUiWatcher?.let { getApplication<Application>().unregisterActivityLifecycleCallbacks(it) }
        connectUiWatcher = null
    }

    private companion object {
        const val TAG = "BankLinkDemo"
    }
}
