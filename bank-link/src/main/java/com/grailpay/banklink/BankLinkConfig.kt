package com.grailpay.banklink

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class BankLinkConfig private constructor(
    val merchantToken: String,
    val connectionId: String?,
    val sessionToken: String?,
    val entityUuid: String?,
    val entityType: EntityType?,
    val clientReferenceId: String?,
    val billingMerchantUuid: String?,
    val billingProcessorMid: String?,
    val branding: Branding?,
    val appLauncherUrl: String,
    // Resolved BankLink host (API + widget), chosen via the Builder. One artifact, every environment.
    val baseUrl: String,
    // Per-merchant Quiltt connector. Normally resolved from the session response; only needed on
    // the manual sessionToken+entityUuid override path.
    val connectorId: String?,
) : Parcelable {

    /**
     * BankLink environment. One host serves both the API routes and the widget UI.
     *
     * Merchants use [PRODUCTION] (default) or [SANDBOX]. Internal QA/staging hosts go through
     * [Builder.testEnvironmentUrl], which release builds reject.
     */
    enum class Environment(internal val baseUrl: String) {
        PRODUCTION("https://banklink-embed.grailpay.com"),
        SANDBOX("https://banklink-embed-sandbox.grailpay.com"),
    }

    class Builder(private val merchantToken: String) {
        private var connectionId: String? = null
        private var sessionToken: String? = null
        private var entityUuid: String? = null
        private var entityType: EntityType? = null
        private var clientReferenceId: String? = null
        private var billingMerchantUuid: String? = null
        private var billingProcessorMid: String? = null
        private var branding: Branding? = null
        private var appLauncherUrl: String? = null
        private var environment: Environment = Environment.PRODUCTION
        private var testBaseUrl: String? = null
        private var connectorId: String? = null

        fun connectionId(id: String): Builder = apply { connectionId = id }
        fun sessionToken(token: String): Builder = apply { sessionToken = token }
        fun entityUuid(uuid: String): Builder = apply { entityUuid = uuid }
        fun entityType(type: EntityType): Builder = apply { entityType = type }
        fun clientReferenceId(id: String): Builder = apply { clientReferenceId = id }

        fun billing(merchantUuid: String?, processorMid: String?): Builder = apply {
            billingMerchantUuid = merchantUuid
            billingProcessorMid = processorMid
        }

        fun branding(branding: Branding): Builder = apply { this.branding = branding }
        fun appLauncherUrl(url: String): Builder = apply { appLauncherUrl = url }

        /** Selects the BankLink environment. Defaults to [Environment.PRODUCTION]. */
        fun environment(environment: Environment): Builder = apply { this.environment = environment }

        /**
         * Internal testing only — points the SDK at an arbitrary host (QA, staging, local).
         * Merchants should use [environment]. Only takes effect in debug builds; release builds
         * reject any host other than [Environment.PRODUCTION] or [Environment.SANDBOX].
         */
        fun testEnvironmentUrl(url: String): Builder = apply { testBaseUrl = url }

        /**
         * Quiltt connector id, used only on the manual session override path (pre-minted
         * [sessionToken] + [entityUuid], SDK skips session creation). Ignored otherwise — the
         * session response carries the connector id.
         */
        fun connectorId(id: String): Builder = apply { connectorId = id }

        fun build(): BankLinkConfig {
            require(merchantToken.isNotBlank()) { "merchantToken must not be blank" }
            val launcher = appLauncherUrl
            require(!launcher.isNullOrBlank()) {
                "appLauncherUrl is required (the HTTPS App Link the merchant has registered with autoVerify=true)"
            }
            // HTTPS-only in release; debug allows HTTP so 10.0.2.2 / lan IPs work for local dev.
            // Banks reject non-HTTPS redirect URIs anyway, so this is a DX guardrail, not security.
            if (!BuildConfig.DEBUG) {
                require(launcher.startsWith("https://", ignoreCase = true)) {
                    "appLauncherUrl must be HTTPS in release builds (got: $launcher)"
                }
            }
            // Optional identity fields must carry a real value when supplied — a blank-but-non-null
            // token/uuid would otherwise produce a malformed session instead of failing fast here.
            require(sessionToken.let { it == null || it.isNotBlank() }) {
                "sessionToken must not be blank when provided"
            }
            require(entityUuid.let { it == null || it.isNotBlank() }) {
                "entityUuid must not be blank when provided"
            }
            require(connectionId.let { it == null || it.isNotBlank() }) {
                "connectionId must not be blank when provided"
            }
            // sessionToken implies a specific entity, so it needs entityUuid. entityUuid alone is
            // fine — the SDK mints a fresh session for that entity.
            require(sessionToken == null || entityUuid != null) {
                "sessionToken requires entityUuid (a manual session override must identify its entity)"
            }
            // The override path skips session creation, so there's no server-issued connector to
            // fall back on — the caller must supply one.
            require(sessionToken == null || !connectorId.isNullOrBlank()) {
                "connectorId is required when supplying a manual sessionToken + entityUuid override"
            }
            // Resolve the host. Release builds lock it to a published environment so a shipped app
            // can never point at an internal/test endpoint.
            val resolvedBaseUrl = (testBaseUrl ?: environment.baseUrl).trimEnd('/')
            if (!BuildConfig.DEBUG) {
                require(
                    resolvedBaseUrl == Environment.PRODUCTION.baseUrl ||
                        resolvedBaseUrl == Environment.SANDBOX.baseUrl
                ) {
                    "In release builds the SDK environment must be PRODUCTION or SANDBOX " +
                        "(got: $resolvedBaseUrl). testEnvironmentUrl() only takes effect in debug builds."
                }
            }
            // Branding is merchant-supplied data, validated in controller.init() so failures route
            // through listener.onError (matching web). The wiring checks above throw instead —
            // they're programmer setup errors, not user data.
            return BankLinkConfig(
                merchantToken = merchantToken,
                connectionId = connectionId,
                sessionToken = sessionToken,
                entityUuid = entityUuid,
                entityType = entityType,
                clientReferenceId = clientReferenceId,
                billingMerchantUuid = billingMerchantUuid,
                billingProcessorMid = billingProcessorMid,
                branding = branding,
                appLauncherUrl = launcher,
                baseUrl = resolvedBaseUrl,
                connectorId = connectorId,
            )
        }
    }
}
