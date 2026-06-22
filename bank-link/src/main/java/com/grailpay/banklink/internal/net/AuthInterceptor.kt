package com.grailpay.banklink.internal.net

import okhttp3.Interceptor
import okhttp3.Response

// Bearer-by-scope. The merchant bearer is attached only to requests tagged `merchant` (just
// /session); everything else uses the linkToken. Scope comes in via an internal header that we
// strip before the request leaves the device, so the merchant credential can't leak to another
// endpoint on a URL string match. Absent/unknown scope defaults to `link`.
internal class AuthInterceptor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val scope = original.header(AUTH_SCOPE_HEADER)
        // Strip the internal marker — never sent over the wire.
        val cleaned = original.newBuilder().removeHeader(AUTH_SCOPE_HEADER).build()

        if (cleaned.header("Authorization") != null) {
            return chain.proceed(cleaned)
        }
        val token = when (scope) {
            SCOPE_MERCHANT -> tokens.merchantToken
            else -> tokens.linkToken
        } ?: return chain.proceed(cleaned)
        return chain.proceed(
            cleaned.newBuilder()
                .header("Authorization", "Bearer $token")
                .build(),
        )
    }

    internal companion object {
        const val AUTH_SCOPE_HEADER = "X-GrailPay-Auth-Scope"
        const val SCOPE_MERCHANT = "merchant"
    }
}