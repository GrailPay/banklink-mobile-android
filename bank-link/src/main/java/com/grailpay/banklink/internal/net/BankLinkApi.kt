package com.grailpay.banklink.internal.net

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

internal interface BankLinkApi {

    // Only this endpoint carries the merchant bearer; AuthInterceptor consumes and strips the
    // scope header. Everything else defaults to the linkToken.
    @Headers("${AuthInterceptor.AUTH_SCOPE_HEADER}: ${AuthInterceptor.SCOPE_MERCHANT}")
    @POST("api/bank-link/session")
    suspend fun createSession(@Body body: SessionRequest): SessionResponse

    @POST("api/bank-link/log")
    suspend fun postLog(@Body body: JsonObject): JsonObject
}