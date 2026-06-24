# How to Integrate the GrailPay Bank Link SDK — Android Application

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)
![minSdk](https://img.shields.io/badge/minSdk-26-blue.svg)
![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)

This guide walks through the **Bank Link workflow** and shows you how to integrate the **GrailPay Bank Link SDK** into an Android application, from adding the dependency to handling a completed bank connection.

The Android SDK mirrors the web SDK end to end: it mints a session, runs the bank-connect flow, presents the GrailPay account-selection UI, and returns the linked account details to your app through a set of callbacks.

## Requirements

- **Bank Link API Key** — your merchant bearer token (sandbox or production).
- **GrailPay Bank Link Android SDK** — the `com.grailpay:bank-link` dependency (a single build serves every environment).
- **An HTTPS domain you control** — required for the OAuth-return App Link (see Step 2).
- **Minimum Android version** — Android 8.0 (API level 26).

## Environment Configuration

There is a single SDK build for all environments. Rather than being fixed at build time, the
environment is selected at **runtime** on the config object. It defaults to **production**, so most
integrations don't need to set it at all.

```kotlin
// Production — the default. Nothing to set.
BankLinkConfig.Builder(token)
    .appLauncherUrl(returnUrl)
    .build()

// Sandbox — opt in explicitly.
BankLinkConfig.Builder(token)
    .appLauncherUrl(returnUrl)
    .environment(BankLinkConfig.Environment.SANDBOX)
    .build()
```

- **Production** — `BankLinkConfig.Environment.PRODUCTION` (default). Use with your **production** Bank Link API key.
- **Sandbox** — `BankLinkConfig.Environment.SANDBOX`. Use with your **sandbox** Bank Link API key.

> Always pair the environment with a matching API key. A sandbox key used against `PRODUCTION`
> (or vice versa) will be rejected by the backend.

## Integration Steps

### Step 1: Add the SDK Dependency

The SDK is published to **GitHub Packages**. Although this repository is public,
GitHub Packages still requires authentication to download artifacts, so you'll need a GitHub
token in place before Gradle can resolve the dependency.

**1. Create a GitHub Personal Access Token (classic)** with the **`read:packages`** scope.
The `repo` scope is not required, since this repository is public. If your organization uses
SSO, remember to authorize the token for the `GrailPay` organization.

**2. Store the credentials** in your **personal** `~/.gradle/gradle.properties`.
Keep these in your home directory and never commit them to the project:

```properties
gpr.user=<your-github-username>
gpr.key=<your-read:packages-token>
```

**3. Add the GitHub Packages repository** in your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/GrailPay/banklink-mobile-android")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
```

**4. Declare the dependency** in your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.grailpay:bank-link:0.1.0")
}

android {
    defaultConfig {
        minSdk = 26
    }
}
```

### Step 2: Register the OAuth-Return App Link

Many banks complete authentication in the system browser and then redirect back to your app. To hand control back cleanly after that redirect, the SDK relies on an HTTPS **App Link**.

**1. Declare the return activity in `AndroidManifest.xml`:**

```xml
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
```

**2. Create the return activity** — it forwards the deep link into the SDK and finishes:

```kotlin
class OAuthReturnActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.let { GrailPayBankLink.handleOAuthReturn(it) }
        finish()
    }
}
```

**3. Host a Digital Asset Links file** at `https://your-domain.com/.well-known/assetlinks.json`:

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

### Step 3: Initialize the Bank Link SDK

Build a `BankLinkConfig`, then call `GrailPayBankLink.init(...)` with an activity, the config, and a `BankLinkListener`. `init()` mints a session and **automatically opens** the Bank Link Widget on success.

```kotlin
val config = BankLinkConfig.Builder("YOUR_BANK_LINK_TOKEN")
    // Required — the HTTPS App Link registered in Step 2
    .appLauncherUrl("https://your-domain.com/bank-link-oauth")

    // Environment (optional) — defaults to PRODUCTION; set SANDBOX for sandbox testing
    .environment(BankLinkConfig.Environment.SANDBOX)

    // Entity management (optional)
    .entityUuid("EXISTING_ENTITY_UUID")          // existing merchant / business / person
    .entityType(EntityType.PERSON)               // PERSON or BUSINESS — for new entities
    .clientReferenceId("YOUR_REF_ID")

    // Branding (optional)
    .branding(
        Branding(
            primaryColor = "#4F46E5",
            logo = "https://yourapp.com/logo.png",
            companyName = "Your Company",
        )
    )

    // Billing (optional)
    .billing(
        merchantUuid = "EXISTING_MERCHANT_UUID",
        processorMid = "EXISTING_PROCESSOR_MID",
    )
    .build()

GrailPayBankLink.init(activity, config, object : BankLinkListener {
    override fun onEntityCreated(event: EntityCreatedEvent) {
        Log.d("BankLink", "Entity created: ${event.entity_uuid}")
    }
    override fun onBankConnected(event: BankConnectedEvent) {
        Log.d("BankLink", "Bank connected: ${event.bank_accounts.size} account(s)")
    }
    override fun onLinkedDefaultAccount(event: LinkedAccountEvent) {
        Log.d("BankLink", "Default account linked: ${event.bank_account.account_uuid}")
    }
    override fun onLinkExit(event: LinkExitEvent) {
        Log.d("BankLink", "User exited: ${event.status}")
    }
    override fun onError(event: BankLinkError) {
        Log.e("BankLink", "Error: ${event.error_message}")
    }
})
```

> **Note** — Unlike the web SDK, `GrailPayBankLink.init()` does **not** return a value to await. It returns immediately; the session is minted on a background thread and **all results are delivered through the `BankLinkListener` callbacks**.

> **Threading** — all four entry points (`init`, `open`, `close`, `relink`) must be called from the main thread.

**Initialization Parameters**

- **merchantToken** *(required)*

    Your GrailPay Bank Link API key — passed to `BankLinkConfig.Builder(...)`.

- **appLauncherUrl** *(required)*

    The HTTPS App Link registered in Step 2; used for the OAuth-return redirect. Must be HTTPS in release builds.

- **environment** *(optional)*

    `BankLinkConfig.Environment.PRODUCTION` (default) or `BankLinkConfig.Environment.SANDBOX`. Selects the backend the SDK talks to; pair it with the matching API key.

- **entityUuid** *(optional)*

    UUID of an existing person, merchant, or business already onboarded with GrailPay. Omit to create a new entity.

- **entityType** *(optional)*

    `EntityType.PERSON` or `EntityType.BUSINESS` — used when creating a new entity.

- **clientReferenceId** *(optional)*

    A client-defined identifier associated with the created entity and connected bank account. Surfaced in every callback.

- **connectionId** *(optional)*

    Supply an existing connection ID to trigger the reconnect (relink) flow instead of a new connection.

- **sessionToken** *(optional)*

    A pre-minted session token (manual session override). Must be paired with `entityUuid`.

- **branding.companyName** *(optional)*

    Display name shown as the widget title (max 20 characters).

- **branding.logo** *(optional)*

    Public HTTPS URL of your brand logo displayed in the widget.

- **branding.primaryColor** *(optional)*

    Theme color (hex) to match your application.

- **billing.merchantUuid** *(optional)*

    UUID of a specific merchant to whom billing should be attributed.

- **billing.processorMid** *(optional)*

    MID of a specific merchant to whom billing should be attributed.

**Callback Events** — `BankLinkListener`

All callbacks have empty default implementations — override only the ones you need.

- **onError**

    Triggered when an error occurs during the flow, including integration issues, authorization failures, or widget launch errors.

- **onLinkedDefaultAccount**

    Triggered when the user selects and saves a default bank account. Includes account details and status (`pending` or `connected`).

- **onEntityCreated**

    Triggered when a new entity (person or business) is successfully created. Fires only when the SDK mints the entity itself (i.e. `entityUuid` was not supplied).

- **onBankConnected**

    Triggered after a successful bank account connection. Returns all linked accounts with their respective statuses.

- **onLinkExit**

    Triggered when the user cancels or exits the bank link process. The `status` field is one of:

    - `COMPLETE` — user saved a default account and closed the widget.
    - `EXITED` — user dismissed the widget (back press / close button).
    - `ACTION_ABANDONED` — user dismissed the bank picker before connecting.

> **Property naming** — event fields use `snake_case` in Kotlin (`entity_uuid`, `bank_accounts`, `error_message`) to match the web SDK's JSON payload shape. Java callers get idiomatic camelCase getters (`getEntityUuid()`, `getBankAccounts()`).

**Other Entry Points**

- `GrailPayBankLink.close()` — programmatically close the widget; fires `onLinkExit` with status `EXITED`.
- `GrailPayBankLink.relink(activity, config, listener)` — relink a broken connection; requires `connectionId` on the config.
- `GrailPayBankLink.handleOAuthReturn(intent)` — forwards an OAuth-return deep link into the SDK (called from `OAuthReturnActivity`, see Step 2).

## Bank Link Flow

Once initialized, the SDK opens the Bank Link Widget as a full-screen activity and guides the user through four stages.

### Finder Screen

The initial screen where users select their bank from the list.

> _Screenshot: Finder Screen — add here_

### OAuth Screen

Users authenticate securely using their bank credentials via the bank's OAuth flow. Real banks may bounce to the system browser and return to your app via the App Link registered in Step 2.

> _Screenshot: OAuth Screen — add here_

### Processing Screen

The system processes the selected accounts and connects them to GrailPay.

> _Screenshot: Processing Screen — add here_

### Default Account Selection Screen

After a successful connection, the user selects a default account for payments.

> _Screenshot: Default Account Selection Screen — add here_

### Callback Lifecycle

```
GrailPayBankLink.init(...)
   ↓  [SDK mints session]      → onEntityCreated   (only if entityUuid was not supplied)
   ↓  [Finder → OAuth → return]
   ↓  [SDK fetches accounts]   → onBankConnected
   ↓  [user picks an account]  → onLinkedDefaultAccount
   ↓  ["All set!" → Close]     → onLinkExit (status = COMPLETE)
```

## API Reference

### `GrailPayBankLink`

All entry points must be called from the **main thread**.

| Method | Description |
|---|---|
| `init(activity, config, listener)` | Mints a session and, on success, **auto-opens** the Bank Link Widget. Init failures route to `onError` — no screen is shown. |
| `open()` | Opens the widget for an already-minted session. Idempotent — a no-op while the widget is open. Fires `onError` if `init()` has not succeeded. |
| `close()` | Cancels an in-flight `init()` or closes the open widget. Fires `onLinkExit` with status `EXITED` exactly once. |
| `relink(activity, config, listener)` | Repairs a broken bank connection. Requires `connectionId` on the config, then behaves like `init()`. |
| `handleOAuthReturn(intent): Boolean` | Forwards an OAuth-return deep link into the SDK (call this from your return activity). Returns `false` if no session is live — restart the flow. |

### `BankLinkListener`

Every callback runs on the **main thread** and has an empty default, so override only what you need.

| Callback | Fires when | Payload |
|---|---|---|
| `onEntityCreated` | The SDK mints a new entity (only when no `entityUuid` was supplied) | `EntityCreatedEvent` |
| `onBankConnected` | The bank connects; carries the discovered accounts | `BankConnectedEvent` |
| `onLinkedDefaultAccount` | A default account is selected/saved | `LinkedAccountEvent` |
| `onLinkExit` | The flow ends without an error — `status` is `COMPLETE`, `EXITED`, or `ACTION_ABANDONED` | `LinkExitEvent` |
| `onError` | The flow ends with an error | `BankLinkError` |

**Terminal contract:** exactly one of `onLinkExit` or `onError` fires per session, and it fires last. No further callbacks are delivered once a session has ended.

## Branding

GrailPay allows you to customize the Bank Link Widget with your own branding via the `Branding` object:

```kotlin
.branding(
    Branding(
        companyName = "XYZ, LLC",
        logo = "https://your-domain.com/logo.png",
        primaryColor = "#ff3902",
    )
)
```

`Branding` fields:

1. **companyName** — widget title (≤ 20 characters).
2. **logo** — public HTTPS URL of your logo.
3. **primaryColor** — hex theme color.
4. **theme** *(optional)* — a preset palette: `BrandTheme.GREEN`, `BrandTheme.TULIP`, or `BrandTheme.SAND`. `primaryColor` overrides the theme color when both are set.

> _Screenshot: Branded widget — add here_

## Reference Recording

> _To be added._

## Troubleshooting

### The OAuth redirect doesn't return to your app

This almost always means the **App Link isn't verified**. Check its status:

```bash
adb shell pm get-app-links <your.package.name>
# your domain should show "verified"
```

If it shows `none` / `1024` (unverified), force re-verification:

```bash
adb shell pm verify-app-links --re-verify <your.package.name>
```

Common causes:

- `assetlinks.json` must be served over **HTTPS at the domain root** — `https://your-domain.com/.well-known/assetlinks.json`, **not** under a subpath.
- The **SHA-256 fingerprint** in `assetlinks.json` must match the cert that signed the installed APK (debug keystore for sideloaded debug builds, release keystore for production).
- The manifest `android:host` / `android:pathPrefix` must match the `appLauncherUrl` exactly.
- If hosting on GitHub Pages, add an empty `.nojekyll` file at the repo root, or the `.well-known` folder won't be published.

Confirm the file is reachable and well-formed:

```bash
curl -i https://your-domain.com/.well-known/assetlinks.json   # expect 200, content-type: application/json
```

### `onError` with "Invalid token."

The Bank Link token doesn't match the selected environment. Pair a **sandbox** token with `Environment.SANDBOX` and a **production** token with `Environment.PRODUCTION`.

### `handleOAuthReturn(intent)` returns `false`

No session is live — typically the app process was killed during the OAuth bounce. Restart the flow with a fresh `init()`.

## License

Released under the [MIT License](LICENSE). Copyright (c) 2026 GrailPay.
