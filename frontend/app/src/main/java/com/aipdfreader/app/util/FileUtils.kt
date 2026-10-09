package com.aipdfreader.app.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Helpers for turning a user-picked `content://` PDF Uri into a stable file the
 * app owns, since content Uri permissions can be revoked between app launches.
 */
object FileUtils {

    fun queryDisplayName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) name = it.getString(nameIndex)
        }
        return name
    }

    fun resolveMimeType(context: Context, uri: Uri, displayName: String? = queryDisplayName(context, uri)): String {
        val fromExtension = mimeTypeForFileName(displayName.orEmpty())
        return fromExtension ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
    }

    fun mimeTypeForFileName(displayName: String): String? = when (displayName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "csv" -> "text/csv"
            "txt" -> "text/plain"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> null
        }

    suspend fun copyPdfToInternalStorage(context: Context, uri: Uri): CopiedFile? =
        withContext(Dispatchers.IO) {
            try {
                val displayName = queryDisplayName(context, uri) ?: "document.pdf"
                val dir = File(context.filesDir, Constants.PDF_STORAGE_DIR).apply { mkdirs() }
                val destFile = File(dir, "${UUID.randomUUID()}.pdf")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext null

                CopiedFile(path = destFile.absolutePath, displayName = displayName, sizeBytes = destFile.length())
            } catch (e: Exception) {
                null
            }
        }

    suspend fun copyMaterialToInternalStorage(context: Context, uri: Uri): CopiedFile? =
        withContext(Dispatchers.IO) {
            var destination: File? = null
            try {
                val displayName = queryDisplayName(context, uri)
                    ?.substringAfterLast('/')
                    ?.substringAfterLast('\\')
                    ?.takeIf(String::isNotBlank)
                    ?: "material"
                val directory = File(context.filesDir, "materials").apply { mkdirs() }
                val safeFileName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                val destinationFile = File(directory, "${UUID.randomUUID()}_$safeFileName")
                destination = destinationFile
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    destinationFile.delete()
                    return@withContext null
                }
                inputStream.use { input ->
                    destinationFile.outputStream().use { output -> input.copyTo(output) }
                }
                CopiedFile(destinationFile.absolutePath, displayName, destinationFile.length())
            } catch (_: Exception) {
                destination?.delete()
                null
            }
        }

    fun deleteFile(path: String) {
        runCatching { File(path).delete() }
    }

    data class CopiedFile(
        val path: String,
        val displayName: String,
        val sizeBytes: Long
    )
}
