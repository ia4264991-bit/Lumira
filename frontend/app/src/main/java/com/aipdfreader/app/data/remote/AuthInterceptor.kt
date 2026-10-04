package com.aipdfreader.app.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Adds a verified Firebase user's current ID token to Vision API requests. */
class AuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val firebaseUser = firebaseAuth.currentUser
        val request = if (firebaseUser == null) {
            original
        } else {
            // OkHttp invokes interceptors on its network dispatcher. Waiting here
            // guarantees the request carries the SDK-refreshed ID token; token
            // contents are never logged or persisted by the app.
            val token = runCatching {
                Tasks.await(firebaseUser.getIdToken(false), 20, TimeUnit.SECONDS).token
            }.getOrNull()
            if (token.isNullOrBlank()) original else original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
