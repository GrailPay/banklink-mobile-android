package com.grailpay.banklink.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.grailpay.banklink.BrandTheme
import com.grailpay.banklink.Branding

internal data class BankLinkBrandingValues(
    val primary: Color,
    val onPrimary: Color,
    val brand500: Color,
    val logoUrl: String?,
    val companyName: String?,
)

internal val LocalBankLinkBranding = staticCompositionLocalOf {
    BankLinkBrandingValues(
        primary = THEME_GREEN_PRIMARY,
        onPrimary = Color.White,
        brand500 = THEME_GREEN_500,
        logoUrl = null,
        companyName = null,
    )
}

@Composable
fun BrandingTheme(
    branding: Branding?,
    content: @Composable () -> Unit,
) {
    val values = resolve(branding)
    val colors = lightColorScheme(
        primary = values.primary,
        onPrimary = values.onPrimary,
    )
    CompositionLocalProvider(LocalBankLinkBranding provides values) {
        MaterialTheme(colorScheme = colors) {
            Surface(color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}

private fun resolve(branding: Branding?): BankLinkBrandingValues {
    val themePrimary = when (branding?.theme) {
        BrandTheme.TULIP -> THEME_TULIP_PRIMARY
        BrandTheme.SAND -> THEME_SAND_PRIMARY
        BrandTheme.GREEN, null -> THEME_GREEN_PRIMARY
    }
    val theme500 = when (branding?.theme) {
        BrandTheme.TULIP -> THEME_TULIP_500
        BrandTheme.SAND -> THEME_SAND_500
        BrandTheme.GREEN, null -> THEME_GREEN_500
    }
    val primary = branding?.primaryColor?.let(::parseHexOrNull) ?: themePrimary
    return BankLinkBrandingValues(
        primary = primary,
        onPrimary = Color.White,
        brand500 = theme500,
        logoUrl = branding?.logo,
        companyName = branding?.companyName,
    )
}

// Tailwind-derived defaults; palette tracks the web SDK design tokens.
private val THEME_GREEN_PRIMARY = Color(0xFF15803D)
private val THEME_GREEN_500 = Color(0xFF16A34A)
private val THEME_TULIP_PRIMARY = Color(0xFFBE185D)
private val THEME_TULIP_500 = Color(0xFFDB2777)
private val THEME_SAND_PRIMARY = Color(0xFFB45309)
private val THEME_SAND_500 = Color(0xFFD97706)

private fun parseHexOrNull(hex: String): Color? {
    val cleaned = hex.removePrefix("#")
    return runCatching {
        when (cleaned.length) {
            6 -> Color(cleaned.toLong(16) or 0xFF000000)
            8 -> Color(cleaned.toLong(16))
            else -> null
        }
    }.getOrNull()
}