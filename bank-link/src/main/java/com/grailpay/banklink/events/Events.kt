package com.grailpay.banklink.events

import com.grailpay.banklink.EntityType
import kotlinx.serialization.Serializable

// Public event types delivered via BankLinkListener. Property names are snake_case to match
// the web SDK's JSON payload, so a merchant on both platforms reads the same fields and can
// reuse one backend handler. @get:JvmName gives Java callers camelCase getters; @Serializable
// lets the payload round-trip identically to web. The @Suppress is for the intentional
// non-camelCase properties.

@Serializable
@Suppress("ConstructorParameterNaming", "PropertyName")
class EntityCreatedEvent(
    @get:JvmName("getEntityUuid") val entity_uuid: String,
    @get:JvmName("getEntityUserUuid") val entity_user_uuid: String,
    @get:JvmName("getEntityType") val entity_type: EntityType,
    @get:JvmName("getClientReferenceId") val client_reference_id: String?,
    @get:JvmName("getCreatedAt") val created_at: String?,
)

@Serializable
@Suppress("ConstructorParameterNaming", "PropertyName")
class BankConnectedEvent(
    @get:JvmName("getEntityUuid") val entity_uuid: String,
    @get:JvmName("getEntityUserUuid") val entity_user_uuid: String,
    @get:JvmName("getClientReferenceId") val client_reference_id: String?,
    @get:JvmName("getBankAccounts") val bank_accounts: List<BankAccountInfo>,
)

@Serializable
@Suppress("ConstructorParameterNaming", "PropertyName")
class LinkedAccountEvent(
    @get:JvmName("getEntityUuid") val entity_uuid: String,
    @get:JvmName("getEntityUserUuid") val entity_user_uuid: String,
    @get:JvmName("getClientReferenceId") val client_reference_id: String?,
    @get:JvmName("getBankAccount") val bank_account: BankAccountInfo,
)

@Serializable
@Suppress("ConstructorParameterNaming", "PropertyName")
class LinkExitEvent(
    @get:JvmName("getEntityUuid") val entity_uuid: String,
    @get:JvmName("getEntityUserUuid") val entity_user_uuid: String,
    @get:JvmName("getClientReferenceId") val client_reference_id: String?,
    val status: LinkExitStatus,
    @get:JvmName("getExitedAt") val exited_at: String,
)

@Serializable
enum class LinkExitStatus {
    COMPLETE,
    EXITED,
    ACTION_ABANDONED,
}

@Serializable
@Suppress("ConstructorParameterNaming", "PropertyName")
class BankLinkError(
    @get:JvmName("getErrorMessage") val error_message: String,
    @get:JvmName("getEntityUuid") val entity_uuid: String?,
    @get:JvmName("getEntityUserUuid") val entity_user_uuid: String?,
    @get:JvmName("getClientReferenceId") val client_reference_id: String?,
    @get:JvmName("getFailedAt") val failed_at: String,
)

@Serializable
@Suppress("ConstructorParameterNaming", "PropertyName")
class BankAccountInfo(
    @get:JvmName("getAccountUuid") val account_uuid: String,
    // String, not Int: preserves leading zeros ("0042") and lets null mean "unknown".
    @get:JvmName("getAccountNumberLast4") val account_number_last4: String?,
    @get:JvmName("getRoutingNumberLast4") val routing_number_last4: String?,
    @get:JvmName("getAccountName") val account_name: String,
    @get:JvmName("getAccountType") val account_type: String,
    @get:JvmName("getInstitutionName") val institution_name: String,
    val aggregator: String,
    val status: String,
    @get:JvmName("getCreatedAt") val created_at: String,
    @get:JvmName("getUpdatedAt") val updated_at: String,
)
