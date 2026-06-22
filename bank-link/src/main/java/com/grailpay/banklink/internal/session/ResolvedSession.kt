package com.grailpay.banklink.internal.session

import android.os.Parcelable
import com.grailpay.banklink.Branding
import com.grailpay.banklink.EntityType
import kotlinx.parcelize.Parcelize

// Everything BankLinkActivity needs to render the Quiltt connector + widget. Built once by
// SessionInitializer (network or manual-override path) and handed to the activity via Intent
// extras. The activity does NO network calls — it is render-only.
@Parcelize
internal data class ResolvedSession(
    val merchantToken: String,
    val sessionToken: String,
    val entityUuid: String,
    val entityUserUuid: String,
    val entityType: EntityType,
    val linkToken: String,
    val connectorId: String,
    val clientReferenceId: String?,
    val createdAt: String?,
    // Re-emitted into the activity-side flow so onEntityCreated is suppressed for manual
    // overrides (web parity: onEntityCreated fires only when the SDK minted the entity).
    val mintedNewEntity: Boolean,
    val reconnectConnectionId: String?,
    val appLauncherUrl: String,
    val branding: Branding?,
    val billingMerchantUuid: String?,
    val billingProcessorMid: String?,
    // Resolved BankLink host (same one serving the API). The render-only activity loads the
    // widget from here instead of a build-time constant, so one artifact serves every env.
    val widgetBaseUrl: String,
) : Parcelable