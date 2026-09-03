package com.aipdfreader.app.util

object Constants {
    const val DATABASE_NAME = "ai_pdf_reader.db"
    const val PDF_STORAGE_DIR = "pdfs"

    /**
     * Local session storage — holds only OUR backend's auth tokens, never an
     * AI provider key. The Android app has no concept of AI providers.
     */
    const val SESSION_PREFS_NAME = "ai_pdf_reader_session"
    const val PREF_ACCESS_TOKEN = "access_token"
    const val PREF_REFRESH_TOKEN = "refresh_token"
    const val PREF_USER_EMAIL = "user_email"

    const val NETWORK_TIMEOUT_SECONDS = 30L

    /**
     * Path segment used by [com.aipdfreader.app.data.remote.AuthInterceptor]
     * to identify auth endpoints that must be called without a bearer token.
     */
    const val AUTH_PATH_SEGMENT = "/auth/"
}
