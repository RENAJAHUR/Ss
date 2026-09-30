package com.example.data.repository

import android.content.Context
import com.example.data.db.PdfDao
import com.example.data.model.Bookmark
import com.example.data.model.RecentDocument
import com.example.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class PdfRepository(private val pdfDao: PdfDao) {

    val allRecentDocuments: Flow<List<RecentDocument>> = pdfDao.getAllRecentDocuments()
    val favoriteDocuments: Flow<List<RecentDocument>> = pdfDao.getFavoriteDocuments()

    fun getBookmarks(filePath: String): Flow<List<Bookmark>> = pdfDao.getBookmarksForDocument(filePath)

    suspend fun registerOpenedDocument(file: File): RecentDocument = withContext(Dispatchers.IO) {
        val existing = pdfDao.getDocumentByPath(file.absolutePath)
        val pageCount = PdfEngine.getPageCount(file).coerceAtLeast(1)
        val fileSizeBytes = file.length()

        val doc = if (existing != null) {
            existing.copy(
                pageCount = pageCount,
                fileSizeBytes = fileSizeBytes,
                lastOpenedTimestamp = System.currentTimeMillis()
            ).also { pdfDao.updateDocument(it) }
        } else {
            val newDoc = RecentDocument(
                title = file.nameWithoutExtension.replace("_", " "),
                filePath = file.absolutePath,
                pageCount = pageCount,
                fileSizeBytes = fileSizeBytes,
                lastOpenedTimestamp = System.currentTimeMillis()
            )
            val id = pdfDao.insertDocument(newDoc)
            newDoc.copy(id = id)
        }
        doc
    }

    suspend fun toggleFavorite(document: RecentDocument) = withContext(Dispatchers.IO) {
        pdfDao.updateDocument(document.copy(isFavorite = !document.isFavorite))
    }

    suspend fun deleteDocument(document: RecentDocument) = withContext(Dispatchers.IO) {
        pdfDao.deleteDocumentById(document.id)
    }

    suspend fun toggleBookmark(filePath: String, pageIndex: Int, label: String) = withContext(Dispatchers.IO) {
        val current = pdfDao.getBookmarksForDocument(filePath).first()
        val exists = current.any { it.pageIndex == pageIndex }
        if (exists) {
            pdfDao.deleteBookmark(filePath, pageIndex)
        } else {
            pdfDao.insertBookmark(
                Bookmark(filePath = filePath, pageIndex = pageIndex, label = label)
            )
        }
    }

    suspend fun initSamplesIfEmpty(context: Context) = withContext(Dispatchers.IO) {
        val count = pdfDao.getAllRecentDocuments().first().size
        if (count == 0) {
            val sampleFiles = PdfEngine.generateSamplePdfs(context)
            sampleFiles.forEach { file ->
                registerOpenedDocument(file)
            }
        }
    }
}
