package com.grailpay.banklink.internal.net

import com.grailpay.banklink.BankLinkConfig
import com.grailpay.banklink.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal object NetworkFactory {

    val tokenStore: TokenStore = TokenStore()

    // Resolved at SDK init from BankLinkConfig.baseUrl. Defaults to production so telemetry
    // singletons that spin up before init still target a valid host. One env per process.
    @Volatile
    var baseUrl: String = BankLinkConfig.Environment.PRODUCTION.baseUrl

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val apis = ConcurrentHashMap<String, BankLinkApi>()

    fun api(): BankLinkApi {
        val url = baseUrl
        return apis[url] ?: synchronized(apis) {
            apis[url] ?: createApi(url).also { apis[url] = it }
        }
    }

    private fun createApi(baseUrl: String): BankLinkApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BankLinkApi::class.java)
    }
}