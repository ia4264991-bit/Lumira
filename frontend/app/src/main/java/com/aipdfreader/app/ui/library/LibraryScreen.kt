package com.aipdfreader.app.ui.library

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aipdfreader.app.ui.components.MaterialThumbnail
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.FileProvider
import com.aipdfreader.app.domain.model.LocalMaterial
import com.aipdfreader.app.domain.model.PdfDocument
import com.aipdfreader.app.util.FileUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPdf: (Long) -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cardTargets by viewModel.cardTargets.collectAsState()
    val isAddingToCard by viewModel.isAddingToCard.collectAsState()
    val isLoadingCardTargets by viewModel.isLoadingCardTargets.collectAsState()
    var itemToAdd by remember { mutableStateOf<LibraryItem?>(null) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importFile)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.dismissError()
            }
        }
    }

    itemToAdd?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!isAddingToCard) itemToAdd = null },
            title = { Text("Add to a Card") },
            text = {
                when {
                    isLoadingCardTargets -> CircularProgressIndicator()
                    cardTargets.isEmpty() -> Text("No Cards you can upload to are available yet.")
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(cardTargets, key = { it.id }) { card ->
                            TextButton(
                                onClick = {
                                    viewModel.addToCard(item, card.id)
                                    itemToAdd = null
                                },
                                enabled = !isAddingToCard,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(card.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        when {
                                            card.isShared -> "Course Space · ${card.role}"
                                            card.id.startsWith("local:") -> "On this phone · works offline"
                                            else -> "Personal Card"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { itemToAdd = null }, enabled = !isAddingToCard) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("On this phone") },
                actions = {
                    IconButton(onClick = onOpenAccount) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Account")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(if (state.isImporting) "Saving…" else "Add file") },
                icon = {
                    if (state.isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Filled.UploadFile, contentDescription = null)
                    }
                },
                onClick = { if (!state.isImporting) pickerLauncher.launch(arrayOf("*/*")) }
            )
        }
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyLibrary(modifier = Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items, key = { it.key }) { item ->
                    when (item) {
                        is LibraryItem.Pdf -> PdfLibraryCard(
                            document = item.document,
                            onClick = { onOpenPdf(item.document.id) },
                            onAddToCard = { itemToAdd = item; viewModel.loadCardTargets() },
                            onDelete = { viewModel.delete(item) }
                        )
                        is LibraryItem.File -> LocalMaterialCard(
                            material = item.material,
                            onClick = { openLocalFile(context, item.material) },
                            onAddToCard = { itemToAdd = item; viewModel.loadCardTargets() },
                            onDelete = { viewModel.delete(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("No files saved yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Keep files here on your phone before adding them to a Card. You can also add files directly from Downloads inside a Card.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun LocalMaterialCard(material: LocalMaterial, onClick: () -> Unit, onAddToCard: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialThumbnail(material.displayName, material.mimeType, material.filePath,
                Modifier.size(width = 86.dp, height = 112.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(material.displayName, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("${material.displayName.substringAfterLast('.', "FILE").uppercase()} · ${formatSize(material.sizeBytes)} · saved on this phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onAddToCard) { Text("Add to Card") }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove file")
                    }
                }
            }
        }
    }
}

private fun openLocalFile(context: android.content.Context, material: LocalMaterial) {
    val file = File(material.filePath)
    if (!file.exists()) {
        Toast.makeText(context, "This saved file is no longer available.", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, material.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open file"))
    }.onFailure { error ->
        val message = if (error is ActivityNotFoundException) "No app can open this file type yet."
        else "Couldn't open this file."
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun PdfLibraryCard(
    document: PdfDocument,
    onClick: () -> Unit,
    onAddToCard: () -> Unit,
    onDelete: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialThumbnail("${document.title}.pdf", "application/pdf", document.filePath,
                Modifier.size(width = 86.dp, height = 112.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${document.pageCount} pages · ${formatSize(document.sizeBytes)} · saved on this phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onAddToCard) { Text("Add to Card") }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${DecimalFormat("#").format(kb)} KB"
    return "${DecimalFormat("#.#").format(kb / 1024.0)} MB"
}
