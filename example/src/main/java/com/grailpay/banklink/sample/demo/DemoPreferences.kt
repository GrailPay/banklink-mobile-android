package com.grailpay.banklink.sample.demo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grailpay.banklink.sample.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.demoStore by preferencesDataStore("demo")

class DemoPreferences(private val context: Context) {

    private val store get() = context.demoStore

    suspend fun load(): DemoFormState = store.data
        .map { it.toForm() }
        .let { flow ->
            var first: DemoFormState? = null
            flow.collect { first = it; return@collect }
            first ?: DemoFormState()
        }

    fun observe(): Flow<DemoFormState> = store.data.map { it.toForm() }

    suspend fun save(state: DemoFormState) {
        store.edit { prefs ->
            // Token only persists in debug builds — never leave merchant bearers on shipped devices.
            if (BuildConfig.DEBUG) prefs[K_TOKEN] = state.token else prefs.remove(K_TOKEN)
            prefs[K_ENTITY_UUID] = state.entityUuid
            prefs[K_ENTITY_TYPE] = state.entityType.name
            prefs[K_CLIENT_REF] = state.clientReferenceId
            prefs[K_BRAND_NAME] = state.brandingCompanyName
            prefs[K_BRAND_LOGO] = state.brandingLogoUrl
            prefs[K_BRAND_COLOR] = state.brandingPrimaryColor
            prefs[K_BILL_MERCH] = state.billingMerchantUuid
            prefs[K_BILL_PROC] = state.billingProcessorMid
            prefs[K_APP_LAUNCHER] = state.appLauncherUrl
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun Preferences.toForm(): DemoFormState = DemoFormState(
        token = if (BuildConfig.DEBUG) this[K_TOKEN].orEmpty() else "",
        entityUuid = this[K_ENTITY_UUID].orEmpty(),
        entityType = this[K_ENTITY_TYPE]?.let { runCatching { EntityTypeChoice.valueOf(it) }.getOrNull() }
            ?: EntityTypeChoice.BUSINESS,
        clientReferenceId = this[K_CLIENT_REF].orEmpty(),
        brandingCompanyName = this[K_BRAND_NAME].orEmpty(),
        brandingLogoUrl = this[K_BRAND_LOGO].orEmpty(),
        brandingPrimaryColor = this[K_BRAND_COLOR].orEmpty(),
        billingMerchantUuid = this[K_BILL_MERCH].orEmpty(),
        billingProcessorMid = this[K_BILL_PROC].orEmpty(),
        appLauncherUrl = this[K_APP_LAUNCHER].takeIf { !it.isNullOrBlank() }
            ?: "https://banklink-oauth.grailpay.com/bank-link-oauth",
    )

    private companion object {
        val K_TOKEN = stringPreferencesKey("demo.token")
        val K_ENTITY_UUID = stringPreferencesKey("demo.entity_uuid")
        val K_ENTITY_TYPE = stringPreferencesKey("demo.entity_type")
        val K_CLIENT_REF = stringPreferencesKey("demo.client_reference_id")
        val K_BRAND_NAME = stringPreferencesKey("demo.brand_name")
        val K_BRAND_LOGO = stringPreferencesKey("demo.brand_logo")
        val K_BRAND_COLOR = stringPreferencesKey("demo.brand_color")
        val K_BILL_MERCH = stringPreferencesKey("demo.billing_merchant")
        val K_BILL_PROC = stringPreferencesKey("demo.billing_processor")
        val K_APP_LAUNCHER = stringPreferencesKey("demo.app_launcher_url")
    }
}