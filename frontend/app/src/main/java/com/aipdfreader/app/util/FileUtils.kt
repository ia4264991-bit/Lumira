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

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                name = it.getString(nameIndex)
            }
        }
        return name
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
