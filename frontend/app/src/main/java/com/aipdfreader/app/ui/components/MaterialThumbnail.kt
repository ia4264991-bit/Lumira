package com.aipdfreader.app.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MaterialThumbnail(
    displayName: String,
    mimeType: String,
    filePath: String? = null,
    modifier: Modifier = Modifier
) {
    val extension = displayName.substringAfterLast('.', "FILE").uppercase().take(5)
    val file = filePath?.let(::File)
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        when {
            mimeType.startsWith("image/") && file?.isFile == true ->
                AsyncImage(model = file, contentDescription = "Preview of $displayName",
                    modifier = Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
            (mimeType == "application/pdf" || displayName.endsWith(".pdf", true)) && file?.isFile == true -> {
                val bitmap = pdfCover(file.absolutePath)
                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "First page of $displayName",
                    modifier = Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
                else FileBadge(extension, isPdf = true)
            }
            else -> FileBadge(extension, isPdf = mimeType == "application/pdf")
        }
    }
}

@Composable
private fun FileBadge(extension: String, isPdf: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.InsertDriveFile,
            contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(5.dp))
        Text(extension, color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun pdfCover(path: String): Bitmap? {
    val context = LocalContext.current
    val bitmapState = produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    val renderer = PdfRenderer(descriptor)
                    try {
                        if (renderer.pageCount == 0) null
                        else {
                            val page = renderer.openPage(0)
                            try {
                                val width = 360
                                val height = (width.toFloat() * page.height / page.width).toInt().coerceIn(120, 520)
                                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { preview ->
                                    preview.eraseColor(android.graphics.Color.WHITE)
                                    page.render(preview, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            } finally { page.close() }
                        }
                    } finally { renderer.close() }
                }
            }.getOrNull()
        }
    }
    return bitmapState.value
}
