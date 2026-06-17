package com.grailpay.banklink.internal.session

import com.grailpay.banklink.BankLinkConfig
import com.grailpay.banklink.EntityType
import com.grailpay.banklink.internal.net.BankLinkApi
import com.grailpay.banklink.internal.net.SessionRequest
import android.util.Log
import com.grailpay.banklink.internal.telemetry.SdkLogger
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import com.grailpay.banklink.BuildConfig as SdkBuildConfig
import retrofit2.HttpException
import java.io.IOException

// Headless session minting, mirroring the web SDK's init(): either honor a manual
// sessionToken+entityUuid override, or POST /api/bank-link/session for a fresh one. Throws on
// validation or network failure — caller routes that to onError.
//
// Error messages are translated to match what the merchant sees on web. The tricky case:
// Cloudflare/nginx edge errors (502 "error code: 502") reach a browser as CORS-blocked
// responses, so web's fetch() rejects with "Failed to fetch" before its response.ok branch.
// We emit the same string for IOException and any non-JSON/empty body so merchant code can
// pattern-match the same way across platforms. JSON errors{} flatten to "msg1, msg2", a
// message field passes through, and the documented fallback is "Failed to get session token".
internal class SessionInitializer(
    private val api: BankLinkApi,
    private val logger: SdkLogger = SdkLogger.get(),
) {

    suspend fun initialize(config: BankLinkConfig): ResolvedSession {
        // Manual override: caller already has session_token + entity_uuid, so skip the network
        // call and use merchant_token as the linkToken (matches web).
        if (config.sessionToken != null && config.entityUuid != null) {
            logger.debug("using_manual_session_override")
            return ResolvedSession(
                merchantToken = config.merchantToken,
                sessionToken = config.sessionToken,
                entityUuid = config.entityUuid,
                entityUserUuid = "",
                entityType = config.entityType ?: EntityType.PERSON,
                linkToken = config.merchantToken,
                // No server connector on this path; the Builder requires connectorId here.
                connectorId = requireNotNull(config.connectorId) {
                    "connectorId is required for a manual session override"
                },
                clientReferenceId = config.clientReferenceId,
                createdAt = null,
                mintedNewEntity = false,
                reconnectConnectionId = config.connectionId,
                appLauncherUrl = config.appLauncherUrl,
                branding = config.branding,
                billingMerchantUuid = config.billingMerchantUuid,
                billingProcessorMid = config.billingProcessorMid,
                widgetBaseUrl = config.baseUrl,
            )
        }

        logger.info("sdk_init_started")
        val resp = try {
            api.createSession(
                SessionRequest(
                    entityUuid = config.entityUuid,
                    entityType = config.entityType?.wireValue(),
                    clientReferenceId = config.clientReferenceId,
                    billingMerchantUuid = config.billingMerchantUuid,
                    billingProcessorMid = config.billingProcessorMid,
                ),
            )
        } catch (e: CancellationException) {
            // Never swallow cancellation — let the coroutine unwind.
            throw e
        } catch (e: HttpException) {
            throw SessionInitException(translateHttpError(e))
        } catch (_: IOException) {
            // DNS, connection refused, TLS failure, host unreachable. Web's fetch() rejects with
            // "Failed to fetch" for all of these — keep the message verbatim.
            throw SessionInitException(NETWORK_FAILURE_MESSAGE)
        } catch (_: SerializationException) {
            // Malformed/empty body or a missing required field from the converter — surface the documented fallback, not a raw decode error.
            throw SessionInitException(DEFAULT_HTTP_ERROR)
        } catch (_: IllegalArgumentException) {
            // Retrofit/kotlinx can wrap a decode failure as IllegalArgumentException — same fallback.
            throw SessionInitException(DEFAULT_HTTP_ERROR)
        }

        logger.info("session_created")

        val resolvedType = EntityType.fromWire(resp.data.entityType)
            ?: config.entityType
            ?: EntityType.PERSON

        return ResolvedSession(
            merchantToken = config.merchantToken,
            sessionToken = resp.data.sessionToken,
            entityUuid = resp.data.entityUuid,
            entityUserUuid = resp.data.personUuid.orEmpty(),
            entityType = resolvedType,
            linkToken = resp.linkToken,
            // Connector is issued per-merchant by the backend; a missing one is a contract
            // violation, surfaced like any session failure.
            connectorId = resp.data.connectorId
                ?: throw SessionInitException("Session response did not include a connectorId"),
            clientReferenceId = resp.data.clientReferenceId ?: config.clientReferenceId,
            createdAt = resp.data.createdAt,
            // onEntityCreated fires only when the SDK minted the entity itself, same gate web
            // uses (if !config.entityUuid). A merchant-supplied entityUuid means it pre-existed.
            mintedNewEntity = config.entityUuid == null,
            reconnectConnectionId = config.connectionId,
            appLauncherUrl = config.appLauncherUrl,
            branding = config.branding,
            billingMerchantUuid = config.billingMerchantUuid,
            billingProcessorMid = config.billingProcessorMid,
            widgetBaseUrl = config.baseUrl,
        )
    }

    private fun translateHttpError(e: HttpException): String {
        // Empty body — treat as a fetch failure to match browser CORS-blocked behavior.
        val raw = e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
            ?: return NETWORK_FAILURE_MESSAGE
        if (SdkBuildConfig.DEBUG) {
            // Trim to 2KB so HTML error pages don't blow up logcat.
            val preview = raw.trim().take(2048)
            Log.d("GrailPay", "session_http_${e.code()}_body: $preview")
        }
        // Non-JSON body (Cloudflare "error code: 502", HTML pages): browser CORS → "Failed to fetch".
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return NETWORK_FAILURE_MESSAGE

        // Web preference order: errors[*] (flattened) > message > fallback.
        val errorsField = parsed["errors"]
        if (errorsField is JsonObject) {
            val flattened = flattenValidationErrors(errorsField)
            if (flattened.isNotBlank()) return flattened
        }
        val message = (parsed["message"] as? JsonPrimitive)?.contentOrNull()
        if (!message.isNullOrBlank()) return message
        return DEFAULT_HTTP_ERROR
    }

    private fun flattenValidationErrors(errors: JsonObject): String {
        val out = mutableListOf<String>()
        // Rails nests errors like {"errors":{"entity":{"uuid":["..."]}}}, so recurse to pull
        // every leaf string — a flat match would drop the real reason and fall through.
        fun collect(element: JsonElement) {
            when (element) {
                is JsonArray -> element.forEach(::collect)
                is JsonObject -> element.values.forEach(::collect)
                is JsonPrimitive -> element.contentOrNull()?.let(out::add)
            }
        }
        collect(errors)
        return out.joinToString(", ")
    }

    private companion object {
        const val DEFAULT_HTTP_ERROR = "Failed to get session token"
        const val NETWORK_FAILURE_MESSAGE = "Failed to fetch"
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

internal class SessionInitException(message: String) : RuntimeException(message)

private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else null