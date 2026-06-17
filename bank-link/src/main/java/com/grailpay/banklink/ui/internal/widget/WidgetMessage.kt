package com.grailpay.banklink.ui.internal.widget

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire DTOs for the widget's postMessage payloads (snake_case). The ViewModel maps these to the
// public event types in events/Events.kt before firing merchant callbacks.

@Serializable
internal data class WireBankAccount(
    @SerialName("account_uuid") val accountUuid: String,
    @SerialName("account_number_last4") val accountNumberLast4: String? = null,
    @SerialName("routing_number_last4") val routingNumberLast4: String? = null,
    @SerialName("account_name") val accountName: String? = null,
    @SerialName("account_type") val accountType: String? = null,
    @SerialName("institution_name") val institutionName: String? = null,
    val aggregator: String? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val latest: Boolean? = null,
    @SerialName("is_default") val isDefault: Boolean? = null,
)

@Serializable
internal data class WireBankConnected(
    @SerialName("entity_uuid") val entityUuid: String,
    @SerialName("bank_accounts") val bankAccounts: List<WireBankAccount> = emptyList(),
)

@Serializable
internal data class WireAccountSelected(
    @SerialName("entity_uuid") val entityUuid: String,
    @SerialName("bank_account") val bankAccount: WireBankAccount,
)

@Serializable
internal data class WireError(
    @SerialName("error_message") val errorMessage: String,
    @SerialName("entity_uuid") val entityUuid: String? = null,
    @SerialName("failed_at") val failedAt: String? = null,
)