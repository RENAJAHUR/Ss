package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiAiService
import com.example.data.db.AppDatabase
import com.example.data.model.ActiveEditorTool
import com.example.data.model.AnnotationType
import com.example.data.model.Bookmark
import com.example.data.model.DocumentVersion
import com.example.data.model.PageAnnotation
import com.example.data.model.PointF
import com.example.data.model.RecentDocument
import com.example.data.repository.PdfRepository
import com.example.pdf.PdfEngine
import com.example.pdf.PdfTextExtractor
import com.example.tts.TextToSpeechHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class AppScreen {
    HOME,
    VIEWER,
    TOOLS,
    SCANNER
}

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = PdfRepository(db.pdfDao())
    val ttsHelper = TextToSpeechHelper(application)

    val recentDocuments: StateFlow<List<RecentDocument>> = repository.allRecentDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<RecentDocument>> = repository.favoriteDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Navigation
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Document State
    private val _currentDocument = MutableStateFlow<RecentDocument?>(null)
    val currentDocument: StateFlow<RecentDocument?> = _currentDocument.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _pageCount = MutableStateFlow(1)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private val _currentPageBitmap = MutableStateFlow<Bitmap?>(null)
    val currentPageBitmap: StateFlow<Bitmap?> = _currentPageBitmap.asStateFlow()

    private val _pageThumbnails = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val pageThumbnails: StateFlow<Map<Int, Bitmap>> = _pageThumbnails.asStateFlow()

    private val _pageRotations = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val pageRotations: StateFlow<Map<Int, Int>> = _pageRotations.asStateFlow()

    private val _isNightMode = MutableStateFlow(false)
    val isNightMode: StateFlow<Boolean> = _isNightMode.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _zoomScale = MutableStateFlow(1f)
    val zoomScale: StateFlow<Float> = _zoomScale.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyMap<Int, Bookmark>().values.toList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    // Annotations & Tools
    private val _activeTool = MutableStateFlow(ActiveEditorTool.VIEW)
    val activeTool: StateFlow<ActiveEditorTool> = _activeTool.asStateFlow()

    private val _selectedColor = MutableStateFlow(0xFFD92D3A) // Crimson Red default
    val selectedColor: StateFlow<Long> = _selectedColor.asStateFlow()

    private val _strokeWidth = MutableStateFlow(4f)
    val strokeWidth: StateFlow<Float> = _strokeWidth.asStateFlow()

    private val _fontSize = MutableStateFlow(18f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val _isBold = MutableStateFlow(false)
    val isBold: StateFlow<Boolean> = _isBold.asStateFlow()

    private val _isItalic = MutableStateFlow(false)
    val isItalic: StateFlow<Boolean> = _isItalic.asStateFlow()

    private val _isUnderline = MutableStateFlow(false)
    val isUnderline: StateFlow<Boolean> = _isUnderline.asStateFlow()

    private val _annotations = MutableStateFlow<List<PageAnnotation>>(emptyList())
    val annotations: StateFlow<List<PageAnnotation>> = _annotations.asStateFlow()

    private val _undoStack = MutableStateFlow<List<List<PageAnnotation>>>(emptyList())
    private val _redoStack = MutableStateFlow<List<List<PageAnnotation>>>(emptyList())

    val canUndo: StateFlow<Boolean> = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = MutableStateFlow(false)

    // Version History
    private val _versions = MutableStateFlow<List<DocumentVersion>>(emptyList())
    val versions: StateFlow<List<DocumentVersion>> = _versions.asStateFlow()

    // AI & Search
    private val _isAiSheetOpen = MutableStateFlow(false)
    val isAiSheetOpen: StateFlow<Boolean> = _isAiSheetOpen.asStateFlow()

    private val _aiResponse = MutableStateFlow("")
    val aiResponse: StateFlow<String> = _aiResponse.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiChatHistory = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val aiChatHistory: StateFlow<List<Pair<String, String>>> = _aiChatHistory.asStateFlow()

    private val _extractedPageText = MutableStateFlow("")
    val extractedPageText: StateFlow<String> = _extractedPageText.asStateFlow()

    // Dialog flags
    private val _showSignatureDialog = MutableStateFlow(false)
    val showSignatureDialog: StateFlow<Boolean> = _showSignatureDialog.asStateFlow()

    private val _showAddTextDialog = MutableStateFlow(false)
    val showAddTextDialog: StateFlow<Boolean> = _showAddTextDialog.asStateFlow()

    private val _showVersionDialog = MutableStateFlow(false)
    val showVersionDialog: StateFlow<Boolean> = _showVersionDialog.asStateFlow()

    private val _showPageManagerDialog = MutableStateFlow(false)
    val showPageManagerDialog: StateFlow<Boolean> = _showPageManagerDialog.asStateFlow()

    private val _showPasswordDialog = MutableStateFlow(false)
    val showPasswordDialog: StateFlow<Boolean> = _showPasswordDialog.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initSamplesIfEmpty(getApplication())
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun openDocument(doc: RecentDocument) {
        viewModelScope.launch {
            val file = File(doc.filePath)
            if (!file.exists()) {
                _statusBanner.value = "File does not exist: ${doc.title}"
                return@launch
            }
            val registered = repository.registerOpenedDocument(file)
            _currentDocument.value = registered
            _currentPageIndex.value = 0
            _pageCount.value = registered.pageCount.coerceAtLeast(1)
            _pageRotations.value = emptyMap()
            _annotations.value = emptyList()
            _undoStack.value = emptyList()
            _redoStack.value = emptyList()

            // Initialize Original Version
            _versions.value = listOf(
                DocumentVersion(
                    versionNumber = 0,
                    versionName = "Original Document",
                    annotations = emptyList()
                )
            )

            loadCurrentPage()
            loadThumbnails(file, registered.pageCount)
            observeBookmarks(doc.filePath)
            _currentScreen.value = AppScreen.VIEWER
        }
    }

    fun openDocumentFromUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_doc_${System.currentTimeMillis()}.pdf"
                val destFile = File(context.filesDir, if (displayName.endsWith(".pdf")) displayName else "$displayName.pdf")

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val registered = repository.registerOpenedDocument(destFile)
                openDocument(registered)
            } catch (e: Exception) {
                e.printStackTrace()
                _statusBanner.value = "Failed to open PDF from storage: ${e.message}"
            }
        }
    }

    private fun observeBookmarks(filePath: String) {
        viewModelScope.launch {
            repository.getBookmarks(filePath).collect {
                _bookmarks.value = it
            }
        }
    }

    fun loadCurrentPage() {
        val doc = _currentDocument.value ?: return
        val file = File(doc.filePath)
        val pageIdx = _currentPageIndex.value
        val rot = _pageRotations.value[pageIdx] ?: 0

        viewModelScope.launch {
            val bitmap = PdfEngine.renderPage(file, pageIdx, targetWidth = 1080, rotationDegrees = rot)
            _currentPageBitmap.value = bitmap

            // Also preload extracted text for TTS & AI
            _extractedPageText.value = PdfTextExtractor.extractTextFromPage(file, pageIdx)
        }
    }

    private fun loadThumbnails(file: File, count: Int) {
        viewModelScope.launch {
            val map = mutableMapOf<Int, Bitmap>()
            for (i in 0 until count.coerceAtMost(10)) {
                val thumb = PdfEngine.renderPage(file, i, targetWidth = 240)
                if (thumb != null) {
                    map[i] = thumb
                }
            }
            _pageThumbnails.value = map
        }
    }

    fun setPageIndex(index: Int) {
        if (index in 0 until _pageCount.value) {
            _currentPageIndex.value = index
            loadCurrentPage()
        }
    }

    fun nextPage() = setPageIndex(_currentPageIndex.value + 1)
    fun previousPage() = setPageIndex(_currentPageIndex.value - 1)

    fun rotateCurrentPage() {
        val current = _pageRotations.value[_currentPageIndex.value] ?: 0
        val newRot = (current + 90) % 360
        _pageRotations.value = _pageRotations.value + (_currentPageIndex.value to newRot)
        loadCurrentPage()
        saveNewVersion("Page ${_currentPageIndex.value + 1} Rotated 90°")
    }

    fun toggleNightMode() {
        _isNightMode.value = !_isNightMode.value
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun setZoomScale(scale: Float) {
        _zoomScale.value = scale.coerceIn(0.8f, 4.0f)
    }

    fun toggleBookmarkCurrentPage() {
        val doc = _currentDocument.value ?: return
        viewModelScope.launch {
            val idx = _currentPageIndex.value
            repository.toggleBookmark(doc.filePath, idx, "Page ${idx + 1}")
        }
    }

    // Annotation Tools
    fun setActiveTool(tool: ActiveEditorTool) {
        _activeTool.value = tool
        if (tool == ActiveEditorTool.SIGNATURE) {
            _showSignatureDialog.value = true
        } else if (tool == ActiveEditorTool.TEXT) {
            _showAddTextDialog.value = true
        }
    }

    fun setSelectedColor(color: Long) {
        _selectedColor.value = color
    }

    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
    }

    fun toggleBold() { _isBold.value = !_isBold.value }
    fun toggleItalic() { _isItalic.value = !_isItalic.value }
    fun toggleUnderline() { _isUnderline.value = !_isUnderline.value }

    fun addAnnotation(annotation: PageAnnotation) {
        pushUndoState()
        _annotations.value = _annotations.value + annotation
        _redoStack.value = emptyList()
        updateUndoRedoStatus()
    }

    fun removeAnnotation(annotationId: String) {
        pushUndoState()
        _annotations.value = _annotations.value.filterNot { it.id == annotationId }
        _redoStack.value = emptyList()
        updateUndoRedoStatus()
    }

    fun toggleCheckboxAnnotation(annotationId: String) {
        _annotations.value = _annotations.value.map {
            if (it.id == annotationId) it.copy(isChecked = !it.isChecked) else it
        }
    }

    private fun pushUndoState() {
        val current = _annotations.value
        _undoStack.value = _undoStack.value + listOf(current)
    }

    fun undo() {
        val stack = _undoStack.value
        if (stack.isNotEmpty()) {
            val previous = stack.last()
            _undoStack.value = stack.dropLast(1)
            _redoStack.value = _redoStack.value + listOf(_annotations.value)
            _annotations.value = previous
            updateUndoRedoStatus()
        }
    }

    fun redo() {
        val stack = _redoStack.value
        if (stack.isNotEmpty()) {
            val next = stack.last()
            _redoStack.value = stack.dropLast(1)
            _undoStack.value = _undoStack.value + listOf(_annotations.value)
            _annotations.value = next
            updateUndoRedoStatus()
        }
    }

    private fun updateUndoRedoStatus() {
        (canUndo as MutableStateFlow).value = _undoStack.value.isNotEmpty()
        (canRedo as MutableStateFlow).value = _redoStack.value.isNotEmpty()
    }

    fun saveNewVersion(description: String) {
        val newVerNum = _versions.value.size
        val newVer = DocumentVersion(
            versionNumber = newVerNum,
            versionName = "Version $newVerNum: $description",
            annotations = _annotations.value,
            pageRotations = _pageRotations.value
        )
        _versions.value = _versions.value + newVer
        _statusBanner.value = "Saved snapshot: Version $newVerNum"
    }

    fun revertToVersion(version: DocumentVersion) {
        _annotations.value = version.annotations
        _pageRotations.value = version.pageRotations
        _undoStack.value = emptyList()
        _redoStack.value = emptyList()
        updateUndoRedoStatus()
        loadCurrentPage()
        _statusBanner.value = "Reverted to ${version.versionName}"
    }

    // Save and Export
    fun saveDocument(onComplete: (File) -> Unit) {
        val doc = _currentDocument.value ?: return
        viewModelScope.launch {
            val originalFile = File(doc.filePath)
            val outputName = "${doc.title}_edited_${System.currentTimeMillis()}.pdf"
            val outputFile = File(getApplication<Application>().filesDir, outputName)

            val success = PdfEngine.saveAnnotatedPdf(
                originalFile = originalFile,
                outputFile = outputFile,
                annotations = _annotations.value,
                pageRotations = _pageRotations.value
            )

            if (success) {
                val updated = repository.registerOpenedDocument(outputFile)
                _currentDocument.value = updated
                saveNewVersion("Document Exported & Saved")
                _statusBanner.value = "Saved successfully as ${outputFile.name}"
                onComplete(outputFile)
            } else {
                _statusBanner.value = "Failed to save PDF"
            }
        }
    }

    // Page Management operations
    fun addBlankPage() {
        val doc = _currentDocument.value ?: return
        viewModelScope.launch {
            val file = File(doc.filePath)
            val outFile = File(getApplication<Application>().filesDir, "${doc.title}_blankpage_${System.currentTimeMillis()}.pdf")
            val success = PdfEngine.addBlankPage(file, _currentPageIndex.value + 1, outFile)
            if (success) {
                val updated = repository.registerOpenedDocument(outFile)
                openDocument(updated)
                _statusBanner.value = "Blank page added!"
            }
        }
    }

    fun deleteCurrentPage() {
        val doc = _currentDocument.value ?: return
        if (_pageCount.value <= 1) {
            _statusBanner.value = "Cannot delete the only page in the document."
            return
        }
        viewModelScope.launch {
            val file = File(doc.filePath)
            val outFile = File(getApplication<Application>().filesDir, "${doc.title}_delpage_${System.currentTimeMillis()}.pdf")
            val success = PdfEngine.deletePage(file, _currentPageIndex.value, outFile)
            if (success) {
                val updated = repository.registerOpenedDocument(outFile)
                openDocument(updated)
                _statusBanner.value = "Page ${_currentPageIndex.value + 1} deleted."
            }
        }
    }

    fun duplicateCurrentPage() {
        val doc = _currentDocument.value ?: return
        viewModelScope.launch {
            val file = File(doc.filePath)
            val outFile = File(getApplication<Application>().filesDir, "${doc.title}_duppage_${System.currentTimeMillis()}.pdf")
            val success = PdfEngine.duplicatePage(file, _currentPageIndex.value, outFile)
            if (success) {
                val updated = repository.registerOpenedDocument(outFile)
                openDocument(updated)
                _statusBanner.value = "Page ${_currentPageIndex.value + 1} duplicated!"
            }
        }
    }

    // AI PDF Assistant actions
    fun openAiSheet() {
        _isAiSheetOpen.value = true
        if (_aiChatHistory.value.isEmpty()) {
            askAiPrompt("Summarize this document in 3-4 key bullet points.")
        }
    }

    fun closeAiSheet() {
        _isAiSheetOpen.value = false
    }

    fun askAiPrompt(prompt: String) {
        val docText = _extractedPageText.value
        _isAiLoading.value = true
        viewModelScope.launch {
            val answer = when {
                prompt.contains("summary", ignoreCase = true) || prompt.contains("सारांश") -> {
                    GeminiAiService.summarizeDocument(docText)
                }
                prompt.contains("हिंदी") || prompt.contains("क्या लिखा है") -> {
                    GeminiAiService.explainInHindi(docText)
                }
                prompt.contains("date", ignoreCase = true) || prompt.contains("deadline", ignoreCase = true) -> {
                    GeminiAiService.extractDatesAndDeadlines(docText)
                }
                prompt.contains("name", ignoreCase = true) || prompt.contains("number", ignoreCase = true) -> {
                    GeminiAiService.extractNamesAndNumbers(docText)
                }
                prompt.contains("arabic", ignoreCase = true) || prompt.contains("العربية") -> {
                    GeminiAiService.translateDocument(docText, "Arabic")
                }
                else -> {
                    GeminiAiService.askDocument(docText, prompt)
                }
            }
            _aiResponse.value = answer
            _aiChatHistory.value = _aiChatHistory.value + Pair(prompt, answer)
            _isAiLoading.value = false
        }
    }

    // Read Aloud (TTS)
    fun speakCurrentPage() {
        val text = _extractedPageText.value
        if (text.isNotBlank()) {
            ttsHelper.speak(text)
        } else {
            _statusBanner.value = "No readable text on this page."
        }
    }

    fun stopSpeaking() {
        ttsHelper.stop()
    }

    // Dialog toggles
    fun setShowSignatureDialog(show: Boolean) { _showSignatureDialog.value = show }
    fun setShowAddTextDialog(show: Boolean) { _showAddTextDialog.value = show }
    fun setShowVersionDialog(show: Boolean) { _showVersionDialog.value = show }
    fun setShowPageManagerDialog(show: Boolean) { _showPageManagerDialog.value = show }
    fun setShowPasswordDialog(show: Boolean) { _showPasswordDialog.value = show }

    fun clearStatusBanner() { _statusBanner.value = null }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
    }
}
