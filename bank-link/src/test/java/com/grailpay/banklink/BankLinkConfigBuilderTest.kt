package com.grailpay.banklink

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Unit tests for [BankLinkConfig.Builder] validation. Pure JVM (no Robolectric) — the builder only
 * touches Kotlin + the generated BuildConfig constant.
 *
 * NOTE: these run under `testDebugUnitTest`, where `BuildConfig.DEBUG == true`, so the release-only
 * guards (HTTPS appLauncherUrl, PRODUCTION/SANDBOX host lockdown) are intentionally NOT exercised
 * here — they are skipped in debug by design. Those belong in a release-variant test
 * (`testReleaseUnitTest`); see the `debug build allows…` cases below for the debug-side contract.
 */
class BankLinkConfigBuilderTest {

    private fun validBuilder() =
        BankLinkConfig.Builder(MERCHANT_TOKEN).appLauncherUrl(APP_LAUNCHER)

    // ---- Happy path ----------------------------------------------------------------------

    @Test
    fun `minimal valid config resolves defaults`() {
        val config = validBuilder().build()

        assertThat(config.merchantToken).isEqualTo(MERCHANT_TOKEN)
        assertThat(config.appLauncherUrl).isEqualTo(APP_LAUNCHER)
        // Defaults to PRODUCTION; one host serves API + widget.
        assertThat(config.baseUrl).isEqualTo("https://banklink-embed.grailpay.com")
        assertThat(config.connectorId).isNull()
        assertThat(config.entityType).isNull()
        assertThat(config.branding).isNull()
    }

    @Test
    fun `sandbox environment resolves the sandbox host`() {
        val config = validBuilder()
            .environment(BankLinkConfig.Environment.SANDBOX)
            .build()

        assertThat(config.baseUrl).isEqualTo("https://banklink-embed-sandbox.grailpay.com")
    }

    @Test
    fun `optional fields pass through to the config`() {
        val branding = Branding(companyName = "Acme")
        val config = validBuilder()
            .clientReferenceId("ref-123")
            .entityType(EntityType.BUSINESS)
            .connectionId("conn-1")
            .billing(merchantUuid = "m-uuid", processorMid = "mid-1")
            .branding(branding)
            .build()

        assertThat(config.clientReferenceId).isEqualTo("ref-123")
        assertThat(config.entityType).isEqualTo(EntityType.BUSINESS)
        assertThat(config.connectionId).isEqualTo("conn-1")
        assertThat(config.billingMerchantUuid).isEqualTo("m-uuid")
        assertThat(config.billingProcessorMid).isEqualTo("mid-1")
        assertThat(config.branding).isSameInstanceAs(branding)
    }

    // ---- Always-on validation (fires in debug and release) -------------------------------

    @Test
    fun `blank merchant token is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            BankLinkConfig.Builder("   ").appLauncherUrl(APP_LAUNCHER).build()
        }
        assertThat(ex).hasMessageThat().contains("merchantToken must not be blank")
    }

    @Test
    fun `missing appLauncherUrl is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            BankLinkConfig.Builder(MERCHANT_TOKEN).build()
        }
        assertThat(ex).hasMessageThat().contains("appLauncherUrl is required")
    }

    @Test
    fun `blank appLauncherUrl is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            BankLinkConfig.Builder(MERCHANT_TOKEN).appLauncherUrl("  ").build()
        }
        assertThat(ex).hasMessageThat().contains("appLauncherUrl is required")
    }

    @Test
    fun `blank sessionToken when provided is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            validBuilder().sessionToken("  ").entityUuid("e-1").connectorId("c-1").build()
        }
        assertThat(ex).hasMessageThat().contains("sessionToken must not be blank")
    }

    @Test
    fun `blank entityUuid when provided is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            validBuilder().entityUuid("  ").build()
        }
        assertThat(ex).hasMessageThat().contains("entityUuid must not be blank")
    }

    @Test
    fun `blank connectionId when provided is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            validBuilder().connectionId("  ").build()
        }
        assertThat(ex).hasMessageThat().contains("connectionId must not be blank")
    }

    @Test
    fun `sessionToken without entityUuid is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            validBuilder().sessionToken("s-1").connectorId("c-1").build()
        }
        assertThat(ex).hasMessageThat().contains("sessionToken requires entityUuid")
    }

    @Test
    fun `manual override without connectorId is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            validBuilder().sessionToken("s-1").entityUuid("e-1").build()
        }
        assertThat(ex).hasMessageThat().contains("connectorId is required")
    }

    @Test
    fun `complete manual override is accepted`() {
        val config = validBuilder()
            .sessionToken("s-1")
            .entityUuid("e-1")
            .connectorId("c-1")
            .build()

        assertThat(config.sessionToken).isEqualTo("s-1")
        assertThat(config.entityUuid).isEqualTo("e-1")
        assertThat(config.connectorId).isEqualTo("c-1")
    }

    // ---- Debug-build behavior (these guards are release-only) -----------------------------

    @Test
    fun `debug build allows an http appLauncherUrl`() {
        // Debug-only contract: the release variant enforces HTTPS, so skip there.
        assumeTrue(BuildConfig.DEBUG)
        // Release rejects non-HTTPS; debug allows it so 10.0.2.2 / LAN dev hosts work.
        val config = BankLinkConfig.Builder(MERCHANT_TOKEN)
            .appLauncherUrl("http://10.0.2.2:3000/return")
            .build()

        assertThat(config.appLauncherUrl).isEqualTo("http://10.0.2.2:3000/return")
    }

    @Test
    fun `debug build honors testEnvironmentUrl and trims trailing slash`() {
        // Debug-only contract: the release variant locks the host down, so skip there.
        assumeTrue(BuildConfig.DEBUG)
        val config = validBuilder()
            .testEnvironmentUrl("https://qa.internal.grailpay.com/")
            .build()

        assertThat(config.baseUrl).isEqualTo("https://qa.internal.grailpay.com")
    }

    private companion object {
        const val MERCHANT_TOKEN = "merchant-token"
        const val APP_LAUNCHER = "https://app.example.com/return"
    }
}
