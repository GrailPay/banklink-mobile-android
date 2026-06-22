package com.grailpay.banklink.internal.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- POST /api/bank-link/session ------------------------------------------------------------

@Serializable
internal data class SessionRequest(
    @SerialName("entity_uuid") val entityUuid: String? = null,
    @SerialName("entity_type") val entityType: String? = null,
    @SerialName("client_reference_id") val clientReferenceId: String? = null,
    @SerialName("billing_merchant_uuid") val billingMerchantUuid: String? = null,
    @SerialName("billing_processor_mid") val billingProcessorMid: String? = null,
)

@Serializable
internal data class SessionResponse(
    val status: Boolean? = null,
    val data: SessionData,
    val linkToken: String,
)

@Serializable
internal data class SessionData(
    @SerialName("session_token") val sessionToken: String,
    @SerialName("entity_uuid") val entityUuid: String,
    @SerialName("person_uuid") val personUuid: String? = null,
    @SerialName("client_reference_id") val clientReferenceId: String? = null,
    @SerialName("entity_type") val entityType: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("connector_id") val connectorId: String? = null,
)

// ---- POST /api/bank-link/log ----------------------------------------------------------------
//
// Wire shape is `{ level, event, timestamp, ...arbitrary }` with arbitrary keys spread at the
// top level, so SdkLogger builds the JsonObject directly instead of a typed DTO.