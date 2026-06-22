package com.grailpay.banklink

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class BrandTheme {
    GREEN,
    TULIP,
    SAND,
}

@Parcelize
data class Branding @JvmOverloads constructor(
    val theme: BrandTheme? = null,
    val primaryColor: String? = null,
    val logo: String? = null,
    val companyName: String? = null,
) : Parcelable {

    internal fun validate() {
        if (logo != null) {
            require(logo.startsWith("https://", ignoreCase = true)) {
                "branding.logo must be an HTTPS URL"
            }
        }
        if (companyName != null) {
            require(companyName.length <= MAX_COMPANY_NAME) {
                "branding.companyName has a max length of $MAX_COMPANY_NAME characters"
            }
        }
    }

    companion object {
        private const val MAX_COMPANY_NAME = 20
    }
}