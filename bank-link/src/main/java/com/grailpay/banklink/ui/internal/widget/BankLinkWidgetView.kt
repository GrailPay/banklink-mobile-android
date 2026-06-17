package com.grailpay.banklink.ui.internal.widget

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.grailpay.banklink.BuildConfig
import com.grailpay.banklink.Branding
import com.grailpay.banklink.BrandTheme
import com.grailpay.banklink.internal.telemetry.SdkLogger
import com.grailpay.banklink.ui.internal.BankLinkUiState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal interface WidgetMessageListener {
    fun onWidgetReady()
    fun onBankConnected(payload: WireBankConnected)
    fun onAccountSelected(payload: WireAccountSelected)
    fun onWidgetError(payload: WireError)
    fun onWidgetExit()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BankLinkWidgetView(
    state: BankLinkUiState.WidgetLaunching,
    branding: Branding?,
    clientReferenceId: String?,
    billingMerchantUuid: String?,
    billingProcessorMid: String?,
    listener: WidgetMessageListener,
) {
    val widgetOrigin = state.widgetUrl.trimEnd('/')
    val iframeSrc = remember(state) { "$widgetOrigin?sdkOrigin=$widgetOrigin" }
    val wrapperHtml = remember(iframeSrc, widgetOrigin) { buildWrapperHtml(iframeSrc, widgetOrigin) }
    val bridge = remember { WidgetBridge(listener) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // No file/content access: keep any page that loads here from reaching
                // app-local data via file:// URLs.
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webChromeClient = WebChromeClient()

                val trustedOrigin = Uri.parse(widgetOrigin)
                // scheme://host[:port], no path — the allowlist rule below.
                val originRule = buildString {
                    append(trustedOrigin.scheme).append("://").append(trustedOrigin.host)
                    if (trustedOrigin.port != -1) append(":").append(trustedOrigin.port)
                }

                // addWebMessageListener injects the bridge only into frames matching originRule,
                // and each message carries a WebView-verified sourceOrigin JS can't spoof. Legacy
                // addJavascriptInterface is the fallback for WebViews too old to support it (~pre-2018).
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(
                        this,
                        BRIDGE_NAME,
                        setOf(originRule),
                        object : WebViewCompat.WebMessageListener {
                            override fun onPostMessage(
                                view: WebView,
                                message: WebMessageCompat,
                                sourceOrigin: Uri,
                                isMainFrame: Boolean,
                                replyProxy: JavaScriptReplyProxy,
                            ) {
                                // Origin already enforced by originRule; no extra string-compare
                                // (which could drop valid messages on origin-format edge cases).
                                message.data?.let { bridge.receiveMessage(it) }
                            }
                        },
                    )
                } else {
                    SdkLogger.get().warn("widget_web_message_listener_unsupported")
                    addJavascriptInterface(bridge, BRIDGE_NAME)
                }

                webViewClient = object : WebViewClient() {
                    // Confine the WebView to the widget origin. The bridge is reachable from any
                    // loaded page, so an untrusted one could forge bank/account events. The connect
                    // flow never navigates off-origin (OAuth runs in the separate Quiltt WebView),
                    // so anything else goes to the system browser instead of loading here.
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url
                        if (isTrusted(url)) return false
                        when (url.scheme?.lowercase()) {
                            "http", "https" -> runCatching {
                                view.context.startActivity(
                                    Intent(Intent.ACTION_VIEW, url)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure { SdkLogger.get().warn("widget_external_nav_failed") }
                            else -> SdkLogger.get().warn("widget_blocked_nav")
                        }
                        return true
                    }

                    private fun isTrusted(url: Uri): Boolean {
                        if (url.scheme.equals("about", ignoreCase = true)) return true
                        val host = url.host ?: return false
                        return url.scheme.equals(trustedOrigin.scheme, ignoreCase = true) &&
                            host.equals(trustedOrigin.host, ignoreCase = true) &&
                            url.port == trustedOrigin.port
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            SdkLogger.get().error(
                                "widget_load_error",
                                mapOf(
                                    "code" to JsonPrimitive(error.errorCode),
                                    "description" to JsonPrimitive(error.description.toString()),
                                ),
                            )
                        }
                    }
                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request.isForMainFrame) {
                            SdkLogger.get().error(
                                "widget_load_http_error",
                                mapOf("code" to JsonPrimitive(errorResponse.statusCode)),
                            )
                        }
                    }

                    // The renderer can be reclaimed under memory pressure (often while we're
                    // backgrounded during the OAuth bounce). Returning true keeps the framework
                    // from re-throwing into the host app; we drop the dead view and end the flow
                    // through the normal error path instead of leaving a blank screen.
                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: RenderProcessGoneDetail,
                    ): Boolean {
                        SdkLogger.get().error(
                            "widget_render_process_gone",
                            mapOf("crashed" to JsonPrimitive(detail.didCrash())),
                        )
                        (view.parent as? ViewGroup)?.removeView(view)
                        view.destroy()
                        listener.onWidgetError(
                            WireError(errorMessage = "The bank connection could not be completed. Please try again."),
                        )
                        return true
                    }
                }

                bridge.attach(this, widgetOrigin, state, branding, clientReferenceId, billingMerchantUuid, billingProcessorMid)
                // baseURL makes the wrapper's origin = widgetOrigin, so wrapper and iframe are
                // same-origin and postMessage flows freely between them.
                loadDataWithBaseURL(widgetOrigin, wrapperHtml, "text/html", "UTF-8", null)
            }
        },
        onRelease = { webView ->
            // destroy() is required on dispose — without it the renderer process, JS threads,
            // message listener, and held Activity context leak, so rotations pile up orphaned
            // WebViews. about:blank first to stop loading/JS.
            webView.loadUrl("about:blank")
            webView.destroy()
        },
    )

    DisposableEffect(state) {
        onDispose { bridge.detach() }
    }
}

private const val BRIDGE_NAME = "AndroidBridge"

// Wrapper HTML hosting an <iframe> for the real widget, so the widget sees window.parent !== window
// and posts grailpay:ready. Iframe is sized in pixels via JS — CSS percent/vh collapse the body to
// height 0 under loadDataWithBaseURL.
private fun buildWrapperHtml(iframeSrc: String, widgetOrigin: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
      <style>
        html, body { margin: 0; padding: 0; background: #fff; overflow: hidden; }
        #grailpay-widget {
          position: fixed; top: 0; left: 0;
          border: 0; display: block; background: transparent;
        }
      </style>
    </head>
    <body>
      <iframe id="grailpay-widget" src="$iframeSrc"></iframe>
      <script>
        (function(){
          var iframe = document.getElementById('grailpay-widget');
          var widgetOrigin = '$widgetOrigin';
          // Native bridge is exposed either as addWebMessageListener (.postMessage, preferred)
          // or legacy addJavascriptInterface (.receiveMessage). Use whichever is present.
          function sendToNative(s) {
            var b = window.$BRIDGE_NAME;
            if (!b) return;
            try {
              if (typeof b.postMessage === 'function') { b.postMessage(s); }
              else if (typeof b.receiveMessage === 'function') { b.receiveMessage(s); }
            } catch (e) {}
          }
          function fitIframe() {
            iframe.style.width = window.innerWidth + 'px';
            iframe.style.height = window.innerHeight + 'px';
          }
          fitIframe();
          window.addEventListener('resize', fitIframe);
          window.addEventListener('orientationchange', fitIframe);
          window.addEventListener('message', function(ev) {
            try {
              if (ev.origin !== widgetOrigin) return;
              if (ev.source !== iframe.contentWindow) return;
              var data = ev && ev.data;
              if (!data || typeof data.type !== 'string') return;
              if (data.type.indexOf('grailpay:') !== 0) return;
              sendToNative(JSON.stringify(data));
            } catch (e) {}
          });
          window.__sendToWidget = function(msgObj) {
            try { iframe.contentWindow.postMessage(msgObj, widgetOrigin); } catch (e) {}
          };
        })();
      </script>
    </body>
    </html>
""".trimIndent()

internal class WidgetBridge(private val listener: WidgetMessageListener) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var webView: WebView? = null
    @Volatile private var widgetOrigin: String = ""
    @Volatile private var pendingState: BankLinkUiState.WidgetLaunching? = null
    @Volatile private var pendingBranding: Branding? = null
    @Volatile private var pendingClientRef: String? = null
    @Volatile private var pendingBillingMerchant: String? = null
    @Volatile private var pendingBillingProcessor: String? = null

    fun attach(
        webView: WebView,
        widgetOrigin: String,
        state: BankLinkUiState.WidgetLaunching,
        branding: Branding?,
        clientReferenceId: String?,
        billingMerchantUuid: String?,
        billingProcessorMid: String?,
    ) {
        this.webView = webView
        this.widgetOrigin = widgetOrigin
        this.pendingState = state
        this.pendingBranding = branding
        this.pendingClientRef = clientReferenceId
        this.pendingBillingMerchant = billingMerchantUuid
        this.pendingBillingProcessor = billingProcessorMid
    }

    fun detach() {
        webView = null
        pendingState = null
        pendingBranding = null
        pendingClientRef = null
        pendingBillingMerchant = null
        pendingBillingProcessor = null
    }

    @JavascriptInterface
    fun receiveMessage(jsonText: String) {
        val obj = runCatching { json.parseToJsonElement(jsonText) as? JsonObject }.getOrNull() ?: return
        val type = (obj["type"] as? JsonPrimitive)?.content ?: return
        val payload = obj["payload"] as? JsonObject

        SdkLogger.get().info("widget_event_${type.removePrefix("grailpay:")}")

        when (type) {
            "grailpay:ready" -> {
                listener.onWidgetReady()
                handleReady()
            }
            "grailpay:bank_connected" -> payload?.let {
                runCatching { json.decodeFromJsonElement(WireBankConnected.serializer(), it) }
                    .onSuccess(listener::onBankConnected)
                    .onFailure { SdkLogger.get().error("widget_bank_connected_parse_failed") }
            }
            "grailpay:account_selected" -> payload?.let {
                runCatching { json.decodeFromJsonElement(WireAccountSelected.serializer(), it) }
                    .onSuccess(listener::onAccountSelected)
                    .onFailure { SdkLogger.get().error("widget_account_selected_parse_failed") }
            }
            // An error event must always reach the merchant and end the flow, so an unparseable
            // payload surfaces a generic error rather than stranding the user with no callback.
            "grailpay:error" -> {
                val parsed = payload?.let {
                    runCatching { json.decodeFromJsonElement(WireError.serializer(), it) }.getOrNull()
                }
                if (parsed != null) {
                    listener.onWidgetError(parsed)
                } else {
                    SdkLogger.get().error("widget_error_unparsable")
                    listener.onWidgetError(
                        WireError(errorMessage = "The bank connection could not be completed. Please try again."),
                    )
                }
            }
            "grailpay:exit" -> listener.onWidgetExit()
        }
    }

    private fun handleReady() {
        val state = pendingState ?: return
        val view = webView ?: return
        val branding = pendingBranding

        // On grailpay:ready, push set_config then set_theme (matches the web SDK's listenToIframe).
        val configJson = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("type", "grailpay:set_config")
                put("token", state.linkToken)
                put("connectionId", state.connectionId)
                put("entityUuid", state.entityUuid)
                pendingClientRef?.let { put("clientReferenceId", it) }
                pendingBillingMerchant?.let { put("billingMerchantUuid", it) }
                pendingBillingProcessor?.let { put("billingProcessorMid", it) }
            },
        )

        val themeJson = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("type", "grailpay:set_theme")
                put(
                    "branding",
                    buildJsonObject {
                        branding?.theme?.let { put("theme", it.wireValue()) }
                        branding?.primaryColor?.let { put("primaryColor", it) }
                        branding?.logo?.let { put("logo", it) }
                        branding?.companyName?.let { put("companyName", it) }
                    },
                )
            },
        )

        view.post {
            view.evaluateJavascript("window.__sendToWidget($configJson);", null)
            if (branding != null) view.evaluateJavascript("window.__sendToWidget($themeJson);", null)
        }
    }
}

private fun BrandTheme.wireValue(): String = when (this) {
    BrandTheme.GREEN -> "green"
    BrandTheme.TULIP -> "tulip"
    BrandTheme.SAND -> "sand"
}