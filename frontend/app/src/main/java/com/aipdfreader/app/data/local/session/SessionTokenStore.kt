package com.aipdfreader.app.data.local.session

import android.content.Context
import androidx.core.content.edit
import com.aipdfreader.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists OUR backend's session tokens on-device.
 *
 * This intentionally has no equivalent of the old `AiSettingsStore`: there is
 * no API key field, no base-URL override, no model field. The app knows one
 * backend (configured at build time via `BuildConfig.BACKEND_BASE_URL`) and
 * authenticates as a user of that backend — nothing more.
 *
 * For a production release, replace the backing `SharedPreferences` with
 * `androidx.security:security-crypto`'s `EncryptedSharedPreferences`; call
 * sites here are the only place that would need to change.
 */
@Singleton
class SessionTokenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(Constants.SESSION_PREFS_NAME, Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString(Constants.PREF_ACCESS_TOKEN, null)
        set(value) = prefs.edit { putString(Constants.PREF_ACCESS_TOKEN, value) }

    var refreshToken: String?
        get() = prefs.getString(Constants.PREF_REFRESH_TOKEN, null)
        set(value) = prefs.edit { putString(Constants.PREF_REFRESH_TOKEN, value) }

    var userEmail: String?
        get() = prefs.getString(Constants.PREF_USER_EMAIL, null)
        set(value) = prefs.edit { putString(Constants.PREF_USER_EMAIL, value) }

    fun isLoggedIn(): Boolean = !accessToken.isNullOrBlank()

    fun saveSession(accessToken: String, refreshToken: String, email: String?) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        email?.let { this.userEmail = it }
    }

    fun clear() {
        prefs.edit { clear() }
    }
}
