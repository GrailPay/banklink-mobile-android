package com.grailpay.banklink

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EntityType {
    @SerialName("person") PERSON,
    @SerialName("business") BUSINESS;

    internal fun wireValue(): String = when (this) {
        PERSON -> "person"
        BUSINESS -> "business"
    }

    internal companion object {
        internal fun fromWire(s: String?): EntityType? = when (s?.lowercase()) {
            "person" -> PERSON
            "business" -> BUSINESS
            else -> null
        }
    }
}
