# GrailPay BankLink — Android SDK

Native Kotlin SDK that gives Android merchants the same bank-connection flow as the web SDK:
mint a session, run Quiltt's connect flow, render the GrailPay account-selection UI, return the
linked account UUID via callback.

- **Min SDK**: 26 (Android 8.0)
- **Compile/target SDK**: 36
- **Compose-first**, but provides a `BankLinkActivity` for non-Compose hosts

---

## Quickstart

### 1. Add the dependency

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.grailpay:bank-link:0.1.0")
}

android {
    defaultConfig {
        minSdk = 26
    }
}
```

### 2. Register the OAuth-return App Link

The SDK launches Quiltt's bank-connect flow in a WebView. Many real banks bounce out to the
system browser for OAuth, then redirect to your `appLauncherUrl` to return control to your app.
Your app needs an HTTPS App Link claim for that URL.

**Manifest:**

```xml
<!-- AndroidManifest.xml -->
<application>
    <activity
        android:name=".OAuthReturnActivity"
        android:exported="true"
        android:launchMode="singleTask"
        android:noHistory="true"
        android:theme="@android:style/Theme.NoDisplay">
        <intent-filter android:autoVerify="true">
            <action android:name="android.intent.action.VIEW" />
            <category android:name="android.intent.category.DEFAULT" />
            <category android:name="android.intent.category.BROWSABLE" />
            <data
                android:scheme="https"
                android:host="your-domain.com"
                android:pathPrefix="/bank-link-oauth" />
        </intent-filter>
    </activity>
</application>
```

**Activity:**

```kotlin
class OAuthReturnActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.let { GrailPayBankLink.handleOAuthReturn(it) }
        finish()
    }
}
```

**Digital Asset Links** at `https://your-domain.com/.well-known/assetlinks.json`:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.yourcompany.yourapp",
    "sha256_cert_fingerprints": ["AB:CD:EF:..."]
  }
}]
```

Get the SHA-256 fingerprint with:

```bash
keytool -list -v -keystore <keystore> -alias <alias> | grep SHA256:
```

You can list multiple fingerprints (debug + release + Play App Signing) in the same array.

### 3. Open the SDK

```kotlin
val config = BankLinkConfig.Builder("<MERCHANT_BEARER>")
    .appLauncherUrl("https://your-domain.com/bank-link-oauth")
    .build()

GrailPayBankLink.open(activity, config, object : BankLinkListener {
    override fun onBankConnected(event: BankConnectedEvent) {
        Log.i("BankLink", "${event.bank_accounts.size} accounts")
    }
    override fun onLinkedDefaultAccount(event: LinkedAccountEvent) {
        Log.i("BankLink", "default = ${event.bank_account.account_uuid}")
    }
    override fun onLinkExit(event: LinkExitEvent) {
        if (event.status == LinkExitStatus.COMPLETE) proceedToCheckout()
    }
    override fun onError(event: BankLinkError) {
        Log.e("BankLink", event.error_message)
    }
})
```

That's the full integration.

---

## Configuration reference

```kotlin
BankLinkConfig.Builder(merchantToken: String)
    .appLauncherUrl(url: String)             // required — HTTPS App Link
    .entityUuid(uuid: String)                // existing user — skips entity creation
    .entityType(EntityType.PERSON | EntityType.BUSINESS)  // for new entities
    .clientReferenceId(id: String)           // your internal reference, surfaced in callbacks
    .connectionId(id: String)                // triggers reconnect flow
    .sessionToken(token: String)             // manual override — must pair with entityUuid
    .billing(merchantUuid: String?, processorMid: String?)
    .branding(Branding(
        theme = BrandTheme.GREEN,            // GREEN | TULIP | SAND
        primaryColor = "#15803D",            // overrides theme if set
        logo = "https://...",                // HTTPS only, optional
        companyName = "Acme Corp",           // ≤ 20 chars
    ))
    .build()
```

**Validation rules** (enforced by `Builder.build()`):

- `merchantToken` non-blank
- `appLauncherUrl` non-blank; HTTPS in release builds (HTTP allowed in debug)
- `sessionToken` and `entityUuid` must both be supplied (manual override) or both omitted (normal mint)
- `Branding.logo` must start with `https://`
- `Branding.companyName` ≤ 20 characters

---

## Listener events

```kotlin
interface BankLinkListener {
    fun onEntityCreated(event: EntityCreatedEvent)         // first-time users only
    fun onBankConnected(event: BankConnectedEvent)         // accounts fetched, before user picks
    fun onLinkedDefaultAccount(event: LinkedAccountEvent)  // user picked + saved
    fun onLinkExit(event: LinkExitEvent)                   // any terminal state
    fun onError(event: BankLinkError)                      // any failure
}
```

All methods have empty default impls — override only what you need.

### Lifecycle

```
GrailPayBankLink.open()
    ↓
[SDK mints session]   → onEntityCreated (only if entity_uuid wasn't supplied)
    ↓
[Quiltt picker, OAuth, return]
    ↓
[SDK fetches accounts via the widget]
    ↓
onBankConnected(BankConnectedEvent { entityUuid, bankAccounts: [...] })
    ↓
[Widget shows AccountSelection — user picks → Save]
    ↓
onLinkedDefaultAccount(LinkedAccountEvent { bankAccount })
    ↓
[Widget shows "All set!" — user taps Close]
    ↓
onLinkExit(LinkExitEvent { status = COMPLETE })
    ↓
BankLinkActivity finishes — control returns to your activity
```

### Exit statuses

| `LinkExitStatus` | When |
|---|---|
| `COMPLETE` | User saved a default account and closed the widget |
| `EXITED` | User dismissed the widget (back press, close button) |
| `ACTION_ABANDONED` | User dismissed Quiltt's bank picker before connecting |

---

## Compose hosts — `BankLinkContent`

If you'd rather embed the flow inside your own Compose tree instead of launching a separate
activity:

```kotlin
class CheckoutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showBankLink by remember { mutableStateOf(false) }
            if (showBankLink) {
                BankLinkContent(
                    config = config,
                    listener = listener,
                    onFinish = { showBankLink = false },
                )
            } else {
                Button(onClick = { showBankLink = true }) { Text("Connect bank") }
            }
        }
    }
}
```

Same callbacks fire. `BankLinkContent` requires a `ComponentActivity` host (it uses the
activity's `Lifecycle` to manage the WebView).

---

## Reconnect flow

```kotlin
val config = BankLinkConfig.Builder(merchantToken)
    .appLauncherUrl("https://your-domain.com/bank-link-oauth")
    .entityUuid(existingEntityUuid)
    .connectionId(existingConnectionId)   // → triggers reconnect instead of new connect
    .build()
GrailPayBankLink.open(activity, config, listener)
```

---

## Debugging

The SDK ships info+ telemetry to `POST /api/bank-link/log` automatically. Key Logcat tags:

- `GrailPay` — SDK lifecycle (`session_created`, `bank_connected`, `widget_event_*`, etc.)
- `OkHttp` — outgoing HTTP requests (debug builds only)
- `chromium` — WebView console output

For Compose hosts, attach to the WebView via `chrome://inspect` (we enable it in debug builds
via `WebView.setWebContentsDebuggingEnabled(true)`).

---

## Architecture

```
[Merchant App]
   ↓ GrailPayBankLink.open(...)
[BankLinkActivity (full-screen)]
   ↓ (tinted Mounting surface — no spinner, see BankLinkUiState.Mounting)
   ↓ POST /api/bank-link/session
[QuilttConnectorWebView — bank picker, Plaid sandbox, OAuth bounce]
   ↓ ExitSuccess(connectionId)
[BankLinkWidgetView — Next.js widget at WIDGET_URL]
   ↓ widget calls /accounts itself
   ↓ user picks account, taps Save
   ↓ widget calls /accounts/default
   ↓ "All set!" → user taps Close
[merchant callbacks fire → activity finishes]
```

**Why two WebViews?** The bank picker (Quiltt's UI) and the GrailPay account-selection UI are
both web. We use Quiltt's native SDK for the first (it owns its own WebView, we hide that
behind `internal class QuilttBridge`). We render the GrailPay widget for the second by loading
your `WIDGET_URL` into a WebView inside `BankLinkActivity`. Both phases are sequential — never
on screen at the same time.

The merchant bearer token never reaches the WebView. It's used once on the SDK side to mint a
session, swapped for an opaque `linkToken` (AES-256-GCM, 30-min TTL), and the linkToken is what
the widget uses to authenticate its own API calls.

---

## License

Proprietary. (Phase 8 publish step will replace this section.)