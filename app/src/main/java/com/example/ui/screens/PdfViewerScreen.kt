package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.ActiveEditorTool
import com.example.data.model.AnnotationType
import com.example.data.model.PageAnnotation
import com.example.data.model.PointF
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.AddTextDialog
import com.example.ui.components.AiAssistantSheet
import com.example.ui.components.AnnotationCanvasOverlay
import com.example.ui.components.EditorToolBar
import com.example.ui.components.PageManagerDialog
import com.example.ui.components.SignatureDialog
import com.example.ui.components.ThumbnailStrip
import com.example.ui.components.TtsMiniPlayer
import com.example.ui.components.VersionHistoryDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewModel
) {
    val context = LocalContext.current
    val currentDoc by viewModel.currentDocument.collectAsState()
    val pageIndex by viewModel.currentPageIndex.collectAsState()
    val pageCount by viewModel.pageCount.collectAsState()
    val pageBitmap by viewModel.currentPageBitmap.collectAsState()
    val thumbnails by viewModel.pageThumbnails.collectAsState()
    val isNightMode by viewModel.isNightMode.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()

    val activeTool by viewModel.activeTool.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val strokeWidth by viewModel.strokeWidth.collectAsState()
    val annotations by viewModel.annotations.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val versions by viewModel.versions.collectAsState()

    val isAiSheetOpen by viewModel.isAiSheetOpen.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val aiChatHistory by viewModel.aiChatHistory.collectAsState()

    val isTtsSpeaking by viewModel.ttsHelper.isSpeaking.collectAsState()
    val ttsLanguage by viewModel.ttsHelper.currentLanguage.collectAsState()
    val ttsSpeed by viewModel.ttsHelper.speechRate.collectAsState()

    val showSignatureDialog by viewModel.showSignatureDialog.collectAsState()
    val showAddTextDialog by viewModel.showAddTextDialog.collectAsState()
    val showVersionDialog by viewModel.showVersionDialog.collectAsState()
    val showPageManagerDialog by viewModel.showPageManagerDialog.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()

    var showMoreMenu by remember { mutableStateOf(false) }
    var showThumbnailStrip by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val isBookmarked = bookmarks.any { it.pageIndex == pageIndex }

    // Back button returns to Home
    BackHandler {
        if (isFullscreen) {
            viewModel.toggleFullscreen()
        } else {
            viewModel.navigateTo(AppScreen.HOME)
        }
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusBanner()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentDoc?.title ?: "PDF Document",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "Page ${pageIndex + 1} of $pageCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.navigateTo(AppScreen.HOME) },
                            modifier = Modifier.testTag("viewer_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                        }
                    },
                    actions = {
                        // Night Mode
                        IconButton(
                            onClick = { viewModel.toggleNightMode() },
                            modifier = Modifier.testTag("viewer_night_mode_btn")
                        ) {
                            Icon(
                                if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Night Mode"
                            )
                        }

                        // Bookmark
                        IconButton(
                            onClick = { viewModel.toggleBookmarkCurrentPage() },
                            modifier = Modifier.testTag("viewer_bookmark_btn")
                        ) {
                            Icon(
                                if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark Page",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Undo
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = canUndo,
                            modifier = Modifier.testTag("viewer_undo_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }

                        // Redo
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = canRedo,
                            modifier = Modifier.testTag("viewer_redo_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }

                        // Version History
                        IconButton(
                            onClick = { viewModel.setShowVersionDialog(true) },
                            modifier = Modifier.testTag("viewer_history_btn")
                        ) {
                            Icon(Icons.Default.History, contentDescription = "Version History")
                        }

                        // More Actions
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.testTag("viewer_more_menu_btn")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Save & Export PDF") },
                                    leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                    onClick = {
                                        viewModel.saveDocument { file ->
                                            Toast.makeText(context, "Saved to ${file.name}", Toast.LENGTH_SHORT).show()
                                        }
                                        showMoreMenu = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Share PDF") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        viewModel.saveDocument { file ->
                                            sharePdf(context, file)
                                        }
                                        showMoreMenu = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Print Document") },
                                    leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                    onClick = {
                                        viewModel.saveDocument { file ->
                                            printPdf(context, file)
                                        }
                                        showMoreMenu = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Page Manager") },
                                    leadingIcon = { Icon(Icons.Default.ViewCarousel, contentDescription = null) },
                                    onClick = {
                                        viewModel.setShowPageManagerDialog(true)
                                        showMoreMenu = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Toggle Fullscreen") },
                                    leadingIcon = { Icon(Icons.Default.Fullscreen, contentDescription = null) },
                                    onClick = {
                                        viewModel.toggleFullscreen()
                                        showMoreMenu = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Toggle Thumbnails") },
                                    onClick = {
                                        showThumbnailStrip = !showThumbnailStrip
                                        showMoreMenu = false
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (isNightMode) Color(0xFF0F172A) else Color(0xFFE2E8F0))
        ) {
            // Main Viewport (Zoomable / Pannable Canvas & PDF Page)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activeTool) {
                        if (activeTool == ActiveEditorTool.VIEW) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.8f, 4f)
                                offset += pan
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentAlignment = Alignment.Center
            ) {
                if (pageBitmap != null) {
                    val nightMatrix = remember {
                        ColorMatrix(
                            floatArrayOf(
                                -1f, 0f, 0f, 0f, 255f,
                                0f, -1f, 0f, 0f, 255f,
                                0f, 0f, -1f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                    ) {
                        Image(
                            bitmap = pageBitmap!!.asImageBitmap(),
                            contentDescription = "PDF Page ${pageIndex + 1}",
                            contentScale = ContentScale.FillWidth,
                            colorFilter = if (isNightMode) ColorFilter.colorMatrix(nightMatrix) else null,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Annotation Overlay Canvas on top of the page
                        AnnotationCanvasOverlay(
                            pageIndex = pageIndex,
                            activeTool = activeTool,
                            selectedColor = selectedColor,
                            strokeWidth = strokeWidth,
                            annotations = annotations,
                            onAddAnnotation = { viewModel.addAnnotation(it) },
                            onRemoveAnnotation = { viewModel.removeAnnotation(it) },
                            onToggleCheckbox = { viewModel.toggleCheckboxAnnotation(it) },
                            modifier = Modifier.matchParentSize()
                        )
                    }
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            // Bottom Floating Controls Column
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // TTS Read Aloud Bar if speaking or triggered
                if (isTtsSpeaking) {
                    TtsMiniPlayer(
                        isSpeaking = isTtsSpeaking,
                        currentLanguage = ttsLanguage,
                        currentSpeed = ttsSpeed,
                        onTogglePlay = {
                            if (isTtsSpeaking) viewModel.stopSpeaking() else viewModel.speakCurrentPage()
                        },
                        onStop = { viewModel.stopSpeaking() },
                        onChangeLanguage = { viewModel.ttsHelper.setLanguage(it) },
                        onChangeSpeed = { viewModel.ttsHelper.setSpeechRate(it) }
                    )
                }

                // Thumbnail Strip (optional toggle)
                if (showThumbnailStrip && !isFullscreen) {
                    ThumbnailStrip(
                        pageCount = pageCount,
                        currentPageIndex = pageIndex,
                        thumbnails = thumbnails,
                        onSelectPage = { viewModel.setPageIndex(it) }
                    )
                }

                // Floating Editor Tool Strip
                if (!isFullscreen) {
                    EditorToolBar(
                        activeTool = activeTool,
                        selectedColor = selectedColor,
                        strokeWidth = strokeWidth,
                        onSelectTool = { viewModel.setActiveTool(it) },
                        onSelectColor = { viewModel.setSelectedColor(it) },
                        onChangeStrokeWidth = { viewModel.setStrokeWidth(it) },
                        onInsertStamp = { stampText ->
                            viewModel.addAnnotation(
                                PageAnnotation(
                                    pageIndex = pageIndex,
                                    type = AnnotationType.STAMP,
                                    text = stampText,
                                    colorArgb = selectedColor,
                                    normalizedX = 0.35f,
                                    normalizedY = 0.45f,
                                    width = 160f,
                                    height = 48f
                                )
                            )
                        },
                        onInsertCheckbox = {
                            viewModel.addAnnotation(
                                PageAnnotation(
                                    pageIndex = pageIndex,
                                    type = AnnotationType.FORM_CHECKBOX,
                                    text = "Checked item",
                                    colorArgb = selectedColor,
                                    normalizedX = 0.1f,
                                    normalizedY = 0.5f,
                                    width = 30f,
                                    height = 30f
                                )
                            )
                        },
                        onOpenAiSheet = { viewModel.openAiSheet() },
                        onReadAloud = { viewModel.speakCurrentPage() }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showSignatureDialog) {
        SignatureDialog(
            onDismiss = { viewModel.setShowSignatureDialog(false) },
            onSaveSignature = { points, color ->
                viewModel.addAnnotation(
                    PageAnnotation(
                        pageIndex = pageIndex,
                        type = AnnotationType.SIGNATURE,
                        points = points,
                        colorArgb = color,
                        normalizedX = 0.3f,
                        normalizedY = 0.7f,
                        width = 180f,
                        height = 70f
                    )
                )
                viewModel.saveNewVersion("Added Signature")
            }
        )
    }

    if (showAddTextDialog) {
        AddTextDialog(
            onDismiss = { viewModel.setShowAddTextDialog(false) },
            onConfirm = { text, size, color, bold, italic, underline ->
                viewModel.addAnnotation(
                    PageAnnotation(
                        pageIndex = pageIndex,
                        type = AnnotationType.TEXT,
                        text = text,
                        fontSize = size,
                        colorArgb = color,
                        isBold = bold,
                        isItalic = italic,
                        isUnderline = underline,
                        normalizedX = 0.2f,
                        normalizedY = 0.35f
                    )
                )
                viewModel.saveNewVersion("Added text: '$text'")
            }
        )
    }

    if (showVersionDialog) {
        VersionHistoryDialog(
            versions = versions,
            onRevertToVersion = { viewModel.revertToVersion(it) },
            onDismiss = { viewModel.setShowVersionDialog(false) }
        )
    }

    if (showPageManagerDialog) {
        PageManagerDialog(
            pageCount = pageCount,
            currentPageIndex = pageIndex,
            thumbnails = thumbnails,
            onSelectPage = { viewModel.setPageIndex(it) },
            onAddBlankPage = { viewModel.addBlankPage() },
            onDeletePage = { viewModel.deleteCurrentPage() },
            onDuplicatePage = { viewModel.duplicateCurrentPage() },
            onRotatePage = { viewModel.rotateCurrentPage() },
            onDismiss = { viewModel.setShowPageManagerDialog(false) }
        )
    }

    if (isAiSheetOpen) {
        AiAssistantSheet(
            isLoading = isAiLoading,
            chatHistory = aiChatHistory,
            onAskPrompt = { viewModel.askAiPrompt(it) },
            onSpeakText = { viewModel.ttsHelper.speak(it) },
            onDismiss = { viewModel.closeAiSheet() }
        )
    }
}

private fun sharePdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun printPdf(context: Context, file: File) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val printAdapter = object : android.print.PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                callback?.onLayoutFinished(
                    android.print.PrintDocumentInfo.Builder(file.name)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintAttributes.Margins.NO_MARGINS.leftMils)
                        .build(),
                    true
                )
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    file.inputStream().use { input ->
                        android.os.ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print("PDF Print: ${file.name}", printAdapter, PrintAttributes.Builder().build())
    } catch (e: Exception) {
        Toast.makeText(context, "Print service error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
