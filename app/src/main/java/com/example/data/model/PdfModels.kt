package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

@Entity(tableName = "recent_documents")
data class RecentDocument(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val thumbnailPath: String? = null
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val pageIndex: Int,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AnnotationType {
    DRAW_PATH,
    HIGHLIGHT_PATH,
    TEXT,
    SHAPE_RECTANGLE,
    SHAPE_CIRCLE,
    SHAPE_ARROW,
    SHAPE_LINE,
    SIGNATURE,
    STAMP,
    FORM_TEXT_FIELD,
    FORM_CHECKBOX
}

data class PointF(val x: Float, val y: Float)

data class PageAnnotation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageIndex: Int,
    val type: AnnotationType,
    val points: List<PointF> = emptyList(), // For draw, highlight, shapes, lines
    val text: String = "", // For text, stamp, form fields
    val colorArgb: Long = 0xFFFF0000,
    val strokeWidth: Float = 4f,
    val fontSize: Float = 18f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isChecked: Boolean = false, // For checkbox form fields
    val normalizedX: Float = 0.5f, // Position on page (0.0 to 1.0)
    val normalizedY: Float = 0.5f,
    val width: Float = 120f,
    val height: Float = 50f
)

data class DocumentVersion(
    val versionNumber: Int,
    val versionName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val annotations: List<PageAnnotation>,
    val pageRotations: Map<Int, Int> = emptyMap()
)

enum class ActiveEditorTool {
    VIEW,
    PEN,
    HIGHLIGHTER,
    TEXT,
    SHAPE_RECTANGLE,
    SHAPE_CIRCLE,
    SHAPE_ARROW,
    SHAPE_LINE,
    SIGNATURE,
    STAMP,
    FORM_TEXT,
    FORM_CHECKBOX,
    ERASER
}
