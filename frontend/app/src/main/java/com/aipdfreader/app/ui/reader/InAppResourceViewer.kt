package com.aipdfreader.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

private const val MAX_TEXT_BYTES = 2 * 1024 * 1024
private const val MAX_ARCHIVE_ENTRIES = 500
private const val MAX_PACKAGE_XML_BYTES = 1024 * 1024
private const val MAX_PACKAGE_INFLATED_BYTES = 16 * 1024 * 1024

/** Opens common local document, image, audio, and video formats inside Vision. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppResourceViewer(
    uri: Uri,
    displayName: String,
    mimeType: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val extension = displayName.substringAfterLast('.', "").lowercase()
    val kind = previewKind(extension, mimeType)
    var preview by remember(uri, kind) { mutableStateOf<ResourcePreview?>(null) }
    var playbackMessage by remember(uri) { mutableStateOf<String?>(null) }

    BackHandler(onBack = onDismiss)
    if (kind != PreviewKind.VIDEO && kind != PreviewKind.AUDIO) {
        LaunchedEffect(uri, kind) {
            preview = withContext(Dispatchers.IO) { loadPreview(context, uri, displayName, kind) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close reader")
                        }
                    }
                )
            }
        ) { padding ->
            when (kind) {
                PreviewKind.VIDEO -> VideoPreview(uri, playbackMessage, { playbackMessage = it }, Modifier.padding(padding))
                PreviewKind.AUDIO -> AudioPreview(context, uri, playbackMessage, { playbackMessage = it }, Modifier.padding(padding))
                else -> when (val result = preview) {
                    null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is ResourcePreview.Image -> Image(
                        bitmap = result.bitmap.asImageBitmap(),
                        contentDescription = displayName,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    is ResourcePreview.Text -> Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
                    ) {
                        Text(result.content, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                            softWrap = true, color = MaterialTheme.colorScheme.onSurface)
                    }
                    is ResourcePreview.Archive -> Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Archive contents · read-only", style = MaterialTheme.typography.titleMedium)
                        result.entries.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (result.truncated) Text("Showing the first $MAX_ARCHIVE_ENTRIES entries.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is ResourcePreview.Unsupported -> Column(
                        Modifier.fillMaxSize().padding(padding).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.InsertDriveFile, contentDescription = null,
                            modifier = Modifier.width(48.dp).height(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(result.message, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("The file stays saved in Vision. It will not be sent to another app.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private enum class PreviewKind { IMAGE, TEXT, OFFICE_PACKAGE, ARCHIVE, AUDIO, VIDEO, UNSUPPORTED }

private sealed interface ResourcePreview {
    data class Image(val bitmap: Bitmap) : ResourcePreview
    data class Text(val content: String) : ResourcePreview
    data class Archive(val entries: List<String>, val truncated: Boolean) : ResourcePreview
    data class Unsupported(val message: String) : ResourcePreview
}

private fun previewKind(extension: String, mimeType: String): PreviewKind = when {
    extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif") -> PreviewKind.IMAGE
    extension in setOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "wma", "aif", "aiff", "amr", "mid", "midi", "opus") || mimeType.startsWith("audio/") -> PreviewKind.AUDIO
    extension in setOf("mp4", "mov", "avi", "wmv", "mkv", "webm", "m4v", "mpeg", "mpg", "3gp", "flv", "ogv", "ts", "mts", "m2ts") || mimeType.startsWith("video/") -> PreviewKind.VIDEO
    extension in setOf("docx", "docm", "pptx", "pptm", "xlsx", "xlsm", "odt", "ods", "odp", "epub") -> PreviewKind.OFFICE_PACKAGE
    extension in setOf("zip", "imscc", "h5p", "mbz", "cbz", "kmz") -> PreviewKind.ARCHIVE
    extension in TEXT_EXTENSIONS || mimeType.startsWith("text/") || mimeType.contains("json") || mimeType.contains("xml") -> PreviewKind.TEXT
    else -> PreviewKind.UNSUPPORTED
}

private val TEXT_EXTENSIONS = setOf(
    "txt", "text", "log", "md", "markdown", "tex", "latex", "bib", "csv", "tsv", "json", "jsonl", "ndjson",
    "xml", "yaml", "yml", "toml", "ini", "html", "htm", "mhtml", "mht", "css", "js", "mjs", "cjs", "ts", "tsx",
    "jsx", "py", "ipynb", "r", "rmd", "qmd", "java", "c", "h", "cpp", "cc", "cxx", "hpp", "cs", "php", "rb",
    "go", "rs", "swift", "kt", "kts", "sql", "sh", "bash", "zsh", "ps1", "jl", "scala", "dart", "lua", "pl", "vb",
    "asm", "ics", "vcs", "vcf", "srt", "vtt", "ass", "ssa", "ttml", "dfxp", "sbv", "geojson", "kml", "gpx",
    "mol", "sdf", "pdb", "cif", "xyz", "wl", "nb", "m", "R"
)

private fun loadPreview(context: Context, uri: Uri, displayName: String, kind: PreviewKind): ResourcePreview = try {
    when (kind) {
        PreviewKind.IMAGE -> ResourcePreview.Image(decodeSampledBitmap(context, uri))
        PreviewKind.TEXT -> ResourcePreview.Text(readText(context.contentResolver.openInputStream(uri)!!, MAX_TEXT_BYTES))
        PreviewKind.OFFICE_PACKAGE -> ResourcePreview.Text(extractPackageText(context.contentResolver.openInputStream(uri)!!, displayName))
        PreviewKind.ARCHIVE -> readArchive(context.contentResolver.openInputStream(uri)!!)
        else -> ResourcePreview.Unsupported("Vision does not have a built-in reader for this file format yet.")
    }
} catch (_: Exception) {
    ResourcePreview.Unsupported("Vision couldn’t read this file. It may be damaged, encrypted, or in an unsupported format.")
}

private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longestEdge = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            val scale = minOf(1f, 1800f / longestEdge)
            decoder.setTargetSize(
                (info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1)
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: error("File is unavailable")
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    val sample = if (longest > 2400) (longest / 1600).coerceAtLeast(1) else 1
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Image could not be decoded")
}

private fun readText(stream: InputStream, maxBytes: Int): String = stream.use { input ->
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(8192)
    var total = 0
    while (total < maxBytes) {
        val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
        if (count <= 0) break
        output.write(buffer, 0, count)
        total += count
    }
    val text = output.toString(Charsets.UTF_8.name())
    if (total >= maxBytes) "$text\n\n[Preview limited to ${maxBytes / 1024} KB.]" else text
}

private fun readArchive(stream: InputStream): ResourcePreview.Archive = stream.use { input ->
    val entries = mutableListOf<String>()
    val zip = ZipInputStream(input)
    var entry = zip.nextEntry
    var truncated = false
    var inflatedBytes = 0
    val discardBuffer = ByteArray(8192)
    while (entry != null) {
        if (entries.size >= MAX_ARCHIVE_ENTRIES) { truncated = true; break }
        entries += if (entry.isDirectory) "📁 ${entry.name}" else "📄 ${entry.name} · ${entry.size.coerceAtLeast(0)} bytes"
        while (inflatedBytes < MAX_PACKAGE_INFLATED_BYTES) {
            val count = zip.read(discardBuffer, 0, minOf(discardBuffer.size, MAX_PACKAGE_INFLATED_BYTES - inflatedBytes))
            if (count <= 0) break
            inflatedBytes += count
        }
        if (inflatedBytes >= MAX_PACKAGE_INFLATED_BYTES) { truncated = true; break }
        entry = zip.nextEntry
    }
    ResourcePreview.Archive(entries, truncated)
}

private fun extractPackageText(stream: InputStream, displayName: String): String = stream.use { input ->
    val extension = displayName.substringAfterLast('.', "").lowercase()
    val wanted: (String) -> Boolean = when (extension) {
        "docx", "docm" -> { path -> path == "word/document.xml" || path.startsWith("word/header") || path.startsWith("word/footer") }
        "pptx", "pptm" -> { path -> path.startsWith("ppt/slides/slide") && path.endsWith(".xml") }
        "xlsx", "xlsm" -> { path -> path == "xl/sharedStrings.xml" || path.startsWith("xl/worksheets/sheet") && path.endsWith(".xml") }
        "odt", "ods", "odp" -> { path -> path == "content.xml" }
        "epub" -> { path -> path.endsWith(".xhtml") || path.endsWith(".html") }
        else -> { _ -> false }
    }
    val output = StringBuilder()
    val zip = ZipInputStream(input)
    var entry = zip.nextEntry
    var inspected = 0
    var inflatedBytes = 0
    val buffer = ByteArray(8192)
    while (entry != null && inspected < MAX_ARCHIVE_ENTRIES && output.length < MAX_TEXT_BYTES &&
        inflatedBytes < MAX_PACKAGE_INFLATED_BYTES) {
        val shouldReadText = !entry.isDirectory && wanted(entry.name)
        val entryOutput = if (shouldReadText) ByteArrayOutputStream() else null
        var entryBytes = 0
        while (inflatedBytes < MAX_PACKAGE_INFLATED_BYTES && (entryOutput == null || entryBytes < MAX_PACKAGE_XML_BYTES)) {
            val remaining = if (entryOutput == null) MAX_PACKAGE_INFLATED_BYTES - inflatedBytes
                else minOf(MAX_PACKAGE_XML_BYTES - entryBytes, MAX_PACKAGE_INFLATED_BYTES - inflatedBytes)
            val count = zip.read(buffer, 0, minOf(buffer.size, remaining))
            if (count <= 0) break
            inflatedBytes += count
            entryBytes += count
            entryOutput?.write(buffer, 0, count)
        }
        if (entryOutput != null) {
            val bytes = entryOutput.toByteArray()
            val text = bytes.toString(Charsets.UTF_8)
                .replace(Regex("<[^>]+>"), " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .replace(Regex("\\s+"), " ").trim()
            if (text.isNotBlank()) output.append(text).append("\n\n")
        }
        if (entryBytes >= MAX_PACKAGE_XML_BYTES && shouldReadText) break
        entry = zip.nextEntry
        inspected++
    }
    output.toString().ifBlank { error("No readable document text was found") }
}

private fun readLimitedEntry(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (total < limit) {
        val count = input.read(buffer, 0, minOf(buffer.size, limit - total))
        if (count <= 0) break
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}

@Composable
private fun AudioPreview(context: Context, uri: Uri, message: String?, onMessage: (String?) -> Unit, modifier: Modifier) {
    var player by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    var ready by remember(uri) { mutableStateOf(false) }
    var playing by remember(uri) { mutableStateOf(false) }
    DisposableEffect(uri) {
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setOnPreparedListener { ready = true; onMessage(null) }
        mediaPlayer.setOnCompletionListener { playing = false }
        mediaPlayer.setOnErrorListener { _, _, _ -> onMessage("This audio codec isn’t supported on this device."); true }
        runCatching { mediaPlayer.setDataSource(context, uri); mediaPlayer.prepareAsync() }
            .onFailure { onMessage("Vision couldn’t play this audio file.") }
        onDispose { runCatching { mediaPlayer.release() }; player = null }
    }
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Audio", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        Button(enabled = ready, onClick = {
            runCatching {
                if (playing) player?.pause() else player?.start()
                playing = !playing
            }.onFailure { onMessage("Vision couldn’t play this audio file.") }
        }) {
            Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp)); Text(if (playing) "Pause" else if (ready) "Play" else "Loading audio…")
        }
        message?.let { Text(it, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun VideoPreview(uri: Uri, message: String?, onMessage: (String?) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var videoView by remember(uri) { mutableStateOf<VideoView?>(null) }
    DisposableEffect(videoView) { onDispose { runCatching { videoView?.stopPlayback() } } }
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { viewContext ->
                VideoView(viewContext).also { view ->
                    videoView = view
                    view.setMediaController(MediaController(viewContext))
                    view.setVideoURI(uri)
                    view.setOnPreparedListener { onMessage(null); it.start() }
                    view.setOnErrorListener { _, _, _ -> onMessage("This video format or codec isn’t supported on this device."); true }
                }
            }
        )
        message?.let { Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
    }
}
