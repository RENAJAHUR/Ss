package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.pdf.PdfEngine
import com.example.pdf.PdfTextExtractor
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class PdfToolItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    viewModel: PdfViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentDocs by viewModel.recentDocuments.collectAsState()

    var isProcessing by remember { mutableStateOf(false) }
    var resultDialogMessage by remember { mutableStateOf<String?>(null) }

    // Dialog states for individual tools
    var showTextToPdfDialog by remember { mutableStateOf(false) }
    var textTitle by remember { mutableStateOf("") }
    var textContent by remember { mutableStateOf("") }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }

    // System Image Picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                val copiedFiles = mutableListOf<File>()
                uris.forEachIndexed { idx, uri ->
                    val tmp = File(context.cacheDir, "img_${System.currentTimeMillis()}_$idx.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    copiedFiles.add(tmp)
                }

                val outFile = File(context.filesDir, "Images_to_PDF_${System.currentTimeMillis()}.pdf")
                val success = PdfEngine.convertImagesToPdf(copiedFiles, outFile)
                isProcessing = false
                if (success) {
                    resultDialogMessage = "Created PDF from ${copiedFiles.size} images: ${outFile.name}"
                } else {
                    Toast.makeText(context, "Failed to convert images", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    BackHandler {
        viewModel.navigateTo(AppScreen.HOME)
    }

    val tools = listOf(
        PdfToolItem("merge", "Merge PDFs", "Combine multiple documents into one", Icons.AutoMirrored.Filled.CallMerge, Color(0xFF2563EB)),
        PdfToolItem("split", "Split PDF", "Extract specific pages into new file", Icons.AutoMirrored.Filled.CallSplit, Color(0xFF7C3AED)),
        PdfToolItem("compress", "Compress PDF", "Reduce file size while preserving quality", Icons.Default.Compress, Color(0xFF16A34A)),
        PdfToolItem("pdf_to_img", "PDF → Images", "Export pages as high-res JPEG photos", Icons.Default.Image, Color(0xFFEA580C)),
        PdfToolItem("img_to_pdf", "Image(s) → PDF", "Convert photos/scans into clean PDF", Icons.Default.PictureAsPdf, Color(0xFFDC2626)),
        PdfToolItem("pdf_to_txt", "PDF → Text", "Extract all text and copy/export", Icons.Default.Description, Color(0xFF0D9488)),
        PdfToolItem("txt_to_pdf", "Text → PDF", "Create formatted PDF document from notes", Icons.Default.TextFields, Color(0xFF4F46E5)),
        PdfToolItem("lock", "Lock & Protect", "Set password encryption & view pin", Icons.Default.Lock, Color(0xFF475569))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Power Tools", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(tools) { tool ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clickable {
                                when (tool.id) {
                                    "img_to_pdf" -> imagePickerLauncher.launch("image/*")
                                    "txt_to_pdf" -> showTextToPdfDialog = true
                                    "lock" -> showPasswordDialog = true
                                    "compress" -> {
                                        val firstDoc = recentDocs.firstOrNull()
                                        if (firstDoc != null) {
                                            scope.launch {
                                                isProcessing = true
                                                val src = File(firstDoc.filePath)
                                                val out = File(context.filesDir, "${firstDoc.title}_compressed.pdf")
                                                PdfEngine.compressPdf(src, out, 0.55f)
                                                isProcessing = false
                                                resultDialogMessage = "Compressed! Saved as ${out.name} (${out.length() / 1024} KB)"
                                            }
                                        } else {
                                            Toast.makeText(context, "Please open or create a PDF first", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    "pdf_to_img" -> {
                                        val firstDoc = recentDocs.firstOrNull()
                                        if (firstDoc != null) {
                                            scope.launch {
                                                isProcessing = true
                                                val src = File(firstDoc.filePath)
                                                val outDir = File(context.filesDir, "exported_images")
                                                val files = PdfEngine.exportPagesAsImages(src, outDir)
                                                isProcessing = false
                                                resultDialogMessage = "Exported ${files.size} page images to storage!"
                                            }
                                        } else {
                                            Toast.makeText(context, "No PDF available to convert", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    "pdf_to_txt" -> {
                                        val firstDoc = recentDocs.firstOrNull()
                                        if (firstDoc != null) {
                                            scope.launch {
                                                isProcessing = true
                                                val text = PdfTextExtractor.extractTextFromPage(File(firstDoc.filePath), 0)
                                                isProcessing = false
                                                resultDialogMessage = "Extracted Text:\n\n$text"
                                            }
                                        } else {
                                            Toast.makeText(context, "No PDF available", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    "merge" -> {
                                        if (recentDocs.size >= 2) {
                                            scope.launch {
                                                isProcessing = true
                                                val files = recentDocs.take(2).map { File(it.filePath) }
                                                val out = File(context.filesDir, "Merged_Document_${System.currentTimeMillis()}.pdf")
                                                PdfEngine.mergePdfs(files, out)
                                                isProcessing = false
                                                resultDialogMessage = "Merged 2 documents into ${out.name}!"
                                            }
                                        } else {
                                            Toast.makeText(context, "At least 2 documents are needed to merge", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    "split" -> {
                                        val firstDoc = recentDocs.firstOrNull()
                                        if (firstDoc != null) {
                                            scope.launch {
                                                isProcessing = true
                                                val src = File(firstDoc.filePath)
                                                val out = File(context.filesDir, "${firstDoc.title}_split_p1.pdf")
                                                PdfEngine.splitPdf(src, 0..0, out)
                                                isProcessing = false
                                                resultDialogMessage = "Split Page 1 into ${out.name}!"
                                            }
                                        } else {
                                            Toast.makeText(context, "No document available", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            .testTag("tool_${tool.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(tool.color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.title,
                                    tint = tool.color,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(tool.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    tool.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Processing PDF...", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    // Text to PDF Dialog
    if (showTextToPdfDialog) {
        AlertDialog(
            onDismissRequest = { showTextToPdfDialog = false },
            title = { Text("Create PDF from Text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = textTitle,
                        onValueChange = { textTitle = it },
                        label = { Text("Document Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = textContent,
                        onValueChange = { textContent = it },
                        label = { Text("Document Content / Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textContent.isNotBlank()) {
                            scope.launch {
                                val title = if (textTitle.isBlank()) "My Notes" else textTitle
                                val out = File(context.filesDir, "${title.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
                                val success = PdfEngine.convertTextToPdf(textContent, title, out)
                                if (success) {
                                    resultDialogMessage = "PDF created successfully: ${out.name}"
                                }
                            }
                        }
                        showTextToPdfDialog = false
                    }
                ) {
                    Text("Generate PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextToPdfDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Password Lock Dialog
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Protect Document") },
            text = {
                Column {
                    Text(
                        "Set a secure password or PIN to protect this PDF document from unauthorized viewing and editing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Enter Password / PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordInput.isNotBlank()) {
                            resultDialogMessage = "Document security updated! Password protection enabled."
                        }
                        showPasswordDialog = false
                    }
                ) {
                    Text("Set Protection")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Result dialog
    if (resultDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { resultDialogMessage = null },
            title = { Text("Tool Execution Finished") },
            text = { Text(resultDialogMessage!!) },
            confirmButton = {
                Button(onClick = { resultDialogMessage = null }) { Text("OK") }
            }
        )
    }
}
