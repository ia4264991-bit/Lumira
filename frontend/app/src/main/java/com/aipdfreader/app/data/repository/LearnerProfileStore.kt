package com.aipdfreader.app.data.repository

import android.content.Context
import android.net.Uri
import com.aipdfreader.app.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class LearnerProfile(
    val uid: String,
    val name: String,
    val goal: String,
    val interests: List<String>,
    val experience: String,
    val minutesPerDay: Int,
    val photoPath: String? = null
)

@Singleton
class LearnerProfileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    private val preferences get() = context.getSharedPreferences("vision_learner_profiles", Context.MODE_PRIVATE)
    val currentUid: String? get() = authRepository.currentUserId

    fun current(): LearnerProfile? = authRepository.currentUserId?.let(::get)

    fun get(uid: String): LearnerProfile? {
        val name = preferences.getString(key(uid, "name"), null)?.takeIf(String::isNotBlank) ?: return null
        val goal = preferences.getString(key(uid, "goal"), null)?.takeIf(String::isNotBlank) ?: return null
        val interests = preferences.getString(key(uid, "interests"), "").orEmpty()
            .split('|').filter(String::isNotBlank)
        return LearnerProfile(
            uid = uid,
            name = name,
            goal = goal,
            interests = interests,
            experience = preferences.getString(key(uid, "experience"), "New learner").orEmpty(),
            minutesPerDay = preferences.getInt(key(uid, "minutes"), 10),
            photoPath = preferences.getString(key(uid, "photo"), null)
        )
    }

    fun hasProfile(uid: String): Boolean = get(uid) != null

    fun save(profile: LearnerProfile) {
        preferences.edit()
            .putString(key(profile.uid, "name"), profile.name.trim())
            .putString(key(profile.uid, "goal"), profile.goal)
            .putString(key(profile.uid, "interests"), profile.interests.joinToString("|"))
            .putString(key(profile.uid, "experience"), profile.experience)
            .putInt(key(profile.uid, "minutes"), profile.minutesPerDay)
            .apply()
    }

    suspend fun setPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        val uid = authRepository.currentUserId ?: return@withContext null
        val copied = FileUtils.copyMaterialToInternalStorage(context, uri) ?: return@withContext null
        val old = preferences.getString(key(uid, "photo"), null)
        preferences.edit().putString(key(uid, "photo"), copied.path).apply()
        if (old != null && old != copied.path) FileUtils.deleteFile(old)
        copied.path
    }

    private fun key(uid: String, field: String) = "$uid.$field"

    companion object {
        fun hasProfile(context: Context, uid: String): Boolean =
            context.getSharedPreferences("vision_learner_profiles", Context.MODE_PRIVATE)
                .getString("$uid.name", null)?.isNotBlank() == true &&
                context.getSharedPreferences("vision_learner_profiles", Context.MODE_PRIVATE)
                    .getString("$uid.goal", null)?.isNotBlank() == true
    }
}
