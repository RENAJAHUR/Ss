package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Bookmark
import com.example.data.model.RecentDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedTimestamp DESC")
    fun getAllRecentDocuments(): Flow<List<RecentDocument>>

    @Query("SELECT * FROM recent_documents WHERE isFavorite = 1 ORDER BY lastOpenedTimestamp DESC")
    fun getFavoriteDocuments(): Flow<List<RecentDocument>>

    @Query("SELECT * FROM recent_documents WHERE filePath = :path LIMIT 1")
    suspend fun getDocumentByPath(path: String): RecentDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: RecentDocument): Long

    @Update
    suspend fun updateDocument(doc: RecentDocument)

    @Query("DELETE FROM recent_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("DELETE FROM recent_documents WHERE filePath = :path")
    suspend fun deleteDocumentByPath(path: String)

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE filePath = :path ORDER BY pageIndex ASC")
    fun getBookmarksForDocument(path: String): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Query("DELETE FROM bookmarks WHERE filePath = :path AND pageIndex = :pageIndex")
    suspend fun deleteBookmark(path: String, pageIndex: Int)
}
