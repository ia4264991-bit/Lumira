package com.aipdfreader.app.data.remote

import com.aipdfreader.app.data.local.session.SessionTokenStore
import com.aipdfreader.app.util.Constants
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches OUR backend's bearer session token to every outgoing request
 * except the auth endpoints themselves (login/register/refresh, which must
 * be callable without a token).
 *
 * This is the only credential the Android app ever holds. It has no
 * knowledge of, and never stores, any AI provider's API key.
 */
class AuthInterceptor @Inject constructor(
    private val sessionTokenStore: SessionTokenStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val isAuthEndpoint = original.url.encodedPath.contains(Constants.AUTH_PATH_SEGMENT)
        val token = sessionTokenStore.accessToken

        val request = if (!isAuthEndpoint && !token.isNullOrBlank()) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}
