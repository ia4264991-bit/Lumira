package com.aipdfreader.app.debug

import android.content.Context
import android.util.Log
import com.aipdfreader.app.BuildConfig
import com.aipdfreader.app.data.local.session.SessionTokenStore

/**
 * TEMPORARY DEVELOPMENT-ONLY UTILITY.
 *
 * Seeds a fake, realistic-looking authenticated session on app launch so the
 * Reader and Selection Engine can be exercised on a physical device before a
 * real backend exists to authenticate against. This does not touch
 * authentication, networking, repositories, or navigation code — it works
 * entirely by pre-populating [SessionTokenStore] (using its existing public
 * API) *before* [com.aipdfreader.app.data.repository.AuthRepository] is ever
 * constructed, so that repository's own existing "already authenticated"
 * check — and [com.aipdfreader.app.ui.auth.LoginScreen]'s existing
 * `LaunchedEffect` that skips straight to the Library screen when already
 * authenticated — do the rest, completely unmodified.
 *
 * Gated on [BuildConfig.DEBUG] internally, not by the call site, so this is
 * inert (a single cheap boolean check, no-op otherwise) in release builds
 * regardless of how or from where it's called.
 *
 * ## Removal
 * Delete this file and the single call to [installIfDebug] in
 * `AiPdfReaderApp.onCreate()` once a real backend is available — nothing
 * else in the app references this class.
 */
object DebugAuthBypass {

    private const val TAG = "DebugAuthBypass"

    private const val FAKE_ACCESS_TOKEN = "debug-bypass-access-token"
    private const val FAKE_REFRESH_TOKEN = "debug-bypass-refresh-token"
    private const val FAKE_USER_EMAIL = "debug-tester@aipdfreader.local"

    /**
     * No-ops immediately in release builds. In debug builds, seeds a fake
     * session if — and only if — no session already exists, so this never
     * clobbers a real login a developer performed against a local/staging
     * backend while testing that flow.
     */
    fun installIfDebug(context: Context) {
        if (!BuildConfig.DEBUG) return

        val sessionTokenStore = SessionTokenStore(context)
        if (sessionTokenStore.isLoggedIn()) return

        sessionTokenStore.saveSession(
            accessToken = FAKE_ACCESS_TOKEN,
            refreshToken = FAKE_REFRESH_TOKEN,
            email = FAKE_USER_EMAIL
        )
        Log.w(TAG, "Debug auth bypass active — using a fake local session, no backend required. Remove before shipping.")
    }
}
