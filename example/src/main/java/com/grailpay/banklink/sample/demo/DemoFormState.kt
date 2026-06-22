package com.grailpay.banklink.sample.demo

data class DemoFormState(
    val token: String = "",
    val entityUuid: String = "",
    val entityType: EntityTypeChoice = EntityTypeChoice.BUSINESS,
    val clientReferenceId: String = "",
    val brandingCompanyName: String = "",
    val brandingLogoUrl: String = "",
    val brandingPrimaryColor: String = "",
    val billingMerchantUuid: String = "",
    val billingProcessorMid: String = "",
    val appLauncherUrl: String = "https://banklink-oauth.grailpay.com/bank-link-oauth",
)

enum class EntityTypeChoice(val wire: String, val label: String) {
    PERSON("person", "Person"),
    BUSINESS("business", "Business"),
}

enum class CallbackKind(val title: String) {
    ERROR("onError"),
    ENTITY_CREATED("onEntityCreated"),
    LINKED_DEFAULT_ACCOUNT("onLinkedDefaultAccount"),
    LINK_EXIT("onLinkExit"),
    BANK_CONNECTED("onBankConnected"),
}