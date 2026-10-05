package com.aipdfreader.app.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Helpers for copying picked `content://` files into app-owned storage or cache. */
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

    private val supportedResourceMimeTypes = mapOf(
        "pdf" to "application/pdf",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "csv" to "text/csv",
        "txt" to "text/plain",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "webp" to "image/webp"
    )

    private const val MAX_RESOURCE_FILE_BYTES = 20L * 1024 * 1024

    fun resourceExtension(filename: String?): String? = filename
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?.takeIf { supportedResourceMimeTypes.containsKey(it) }

    fun resourceMimeType(filename: String?): String? =
        resourceExtension(filename)?.let(supportedResourceMimeTypes::get)

    suspend fun copyResourceToCache(context: Context, uri: Uri): CopiedFile? =
        withContext(Dispatchers.IO) {
            val displayName = queryDisplayName(context, uri)?.substringAfterLast('/')?.substringAfterLast('\\')
                ?: return@withContext null
            val extension = resourceExtension(displayName) ?: return@withContext null
            val mimeType = supportedResourceMimeTypes.getValue(extension)
            val destination = File(context.cacheDir, "lumira-upload-" + UUID.randomUUID() + "." + extension)
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null

            try {
                var totalBytes = 0L
                input.use { source ->
                    destination.outputStream().buffered().use { target ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            if (totalBytes > MAX_RESOURCE_FILE_BYTES) {
                                throw IllegalArgumentException("Resource exceeds the upload limit.")
                            }
                            target.write(buffer, 0, count)
                        }
                    }
                }
                if (totalBytes == 0L) {
                    destination.delete()
                    null
                } else {
                    CopiedFile(destination.absolutePath, displayName, totalBytes, mimeType)
                }
            } catch (_: Exception) {
                destination.delete()
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
        val sizeBytes: Long,
        val mimeType: String? = null
    )
}
