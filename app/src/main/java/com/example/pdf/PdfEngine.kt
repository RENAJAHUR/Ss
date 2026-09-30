package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.model.AnnotationType
import com.example.data.model.PageAnnotation
import com.example.data.model.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object PdfEngine {

    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext 0
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    suspend fun renderPage(
        file: File,
        pageIndex: Int,
        targetWidth: Int = 1080,
        rotationDegrees: Int = 0
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val srcW = page.width
                        val srcH = page.height
                        val scale = targetWidth.toFloat() / srcW.toFloat()
                        val targetHeight = (srcH * scale).toInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        if (rotationDegrees % 360 != 0) {
                            val matrix = Matrix().apply {
                                postRotate(rotationDegrees.toFloat())
                            }
                            val rotated = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )
                            if (rotated != bitmap) {
                                bitmap.recycle()
                            }
                            rotated
                        } else {
                            bitmap
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveAnnotatedPdf(
        originalFile: File,
        outputFile: File,
        annotations: List<PageAnnotation>,
        pageRotations: Map<Int, Int> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(originalFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount
                    val pdfDoc = PdfDocument()

                    for (i in 0 until pageCount) {
                        renderer.openPage(i).use { page ->
                            val rot = pageRotations[i] ?: 0
                            val isSideways = (rot % 180 != 0)
                            val docWidth = if (isSideways) page.height else page.width
                            val docHeight = if (isSideways) page.width else page.height

                            val pageInfo = PdfDocument.PageInfo.Builder(docWidth, docHeight, i + 1).create()
                            val pdfPage = pdfDoc.startPage(pageInfo)
                            val canvas = pdfPage.canvas

                            // Draw original PDF content onto page canvas
                            val renderBitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            val renderCanvas = Canvas(renderBitmap)
                            renderCanvas.drawColor(Color.WHITE)
                            page.render(renderBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            if (rot % 360 != 0) {
                                canvas.save()
                                canvas.translate(docWidth / 2f, docHeight / 2f)
                                canvas.rotate(rot.toFloat())
                                canvas.drawBitmap(renderBitmap, -page.width / 2f, -page.height / 2f, null)
                                canvas.restore()
                            } else {
                                canvas.drawBitmap(renderBitmap, 0f, 0f, null)
                            }
                            renderBitmap.recycle()

                            // Draw annotations belonging to this page
                            val pageAnnots = annotations.filter { it.pageIndex == i }
                            drawAnnotationsOnCanvas(canvas, pageAnnots, docWidth.toFloat(), docHeight.toFloat())

                            pdfDoc.finishPage(pdfPage)
                        }
                    }

                    FileOutputStream(outputFile).use { out ->
                        pdfDoc.writeTo(out)
                    }
                    pdfDoc.close()
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun drawAnnotationsOnCanvas(
        canvas: Canvas,
        annotations: List<PageAnnotation>,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (annot in annotations) {
            paint.color = annot.colorArgb.toInt()

            when (annot.type) {
                AnnotationType.DRAW_PATH -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = annot.strokeWidth
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    val path = Path()
                    annot.points.forEachIndexed { idx, pt ->
                        val px = pt.x * pageWidth
                        val py = pt.y * pageHeight
                        if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    canvas.drawPath(path, paint)
                }
                AnnotationType.HIGHLIGHT_PATH -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = annot.strokeWidth.coerceAtLeast(16f)
                    paint.strokeCap = Paint.Cap.SQUARE
                    paint.alpha = 110 // Semi-transparent highlight
                    val path = Path()
                    annot.points.forEachIndexed { idx, pt ->
                        val px = pt.x * pageWidth
                        val py = pt.y * pageHeight
                        if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    canvas.drawPath(path, paint)
                }
                AnnotationType.TEXT -> {
                    paint.style = Paint.Style.FILL
                    paint.textSize = annot.fontSize
                    var typefaceStyle = Typeface.NORMAL
                    if (annot.isBold && annot.isItalic) typefaceStyle = Typeface.BOLD_ITALIC
                    else if (annot.isBold) typefaceStyle = Typeface.BOLD
                    else if (annot.isItalic) typefaceStyle = Typeface.ITALIC
                    paint.typeface = Typeface.create(Typeface.DEFAULT, typefaceStyle)
                    paint.isUnderlineText = annot.isUnderline

                    val posX = annot.normalizedX * pageWidth
                    val posY = annot.normalizedY * pageHeight
                    canvas.drawText(annot.text, posX, posY, paint)
                }
                AnnotationType.SHAPE_RECTANGLE -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = annot.strokeWidth
                    val left = annot.normalizedX * pageWidth
                    val top = annot.normalizedY * pageHeight
                    val right = left + annot.width
                    val bottom = top + annot.height
                    canvas.drawRect(left, top, right, bottom, paint)
                }
                AnnotationType.SHAPE_CIRCLE -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = annot.strokeWidth
                    val cx = annot.normalizedX * pageWidth + annot.width / 2f
                    val cy = annot.normalizedY * pageHeight + annot.height / 2f
                    val radius = (annot.width.coerceAtMost(annot.height)) / 2f
                    canvas.drawCircle(cx, cy, radius, paint)
                }
                AnnotationType.SHAPE_ARROW -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = annot.strokeWidth
                    val startX = annot.normalizedX * pageWidth
                    val startY = annot.normalizedY * pageHeight
                    val endX = startX + annot.width
                    val endY = startY + annot.height

                    canvas.drawLine(startX, startY, endX, endY, paint)

                    // Draw arrowhead
                    val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
                    val arrowSize = 16f
                    val x1 = (endX - arrowSize * cos(angle - Math.PI / 6)).toFloat()
                    val y1 = (endY - arrowSize * sin(angle - Math.PI / 6)).toFloat()
                    val x2 = (endX - arrowSize * cos(angle + Math.PI / 6)).toFloat()
                    val y2 = (endY - arrowSize * sin(angle + Math.PI / 6)).toFloat()
                    canvas.drawLine(endX, endY, x1, y1, paint)
                    canvas.drawLine(endX, endY, x2, y2, paint)
                }
                AnnotationType.SHAPE_LINE -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = annot.strokeWidth
                    val startX = annot.normalizedX * pageWidth
                    val startY = annot.normalizedY * pageHeight
                    val endX = startX + annot.width
                    val endY = startY + annot.height
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
                AnnotationType.SIGNATURE -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = annot.strokeWidth.coerceAtLeast(3f)
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    val baseX = annot.normalizedX * pageWidth
                    val baseY = annot.normalizedY * pageHeight
                    val path = Path()
                    annot.points.forEachIndexed { idx, pt ->
                        val px = baseX + pt.x * annot.width
                        val py = baseY + pt.y * annot.height
                        if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    canvas.drawPath(path, paint)

                    // Small signature baseline
                    val linePaint = Paint().apply {
                        color = Color.DKGRAY
                        strokeWidth = 1.5f
                        style = Paint.Style.STROKE
                        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
                    }
                    canvas.drawLine(baseX, baseY + annot.height + 4f, baseX + annot.width, baseY + annot.height + 4f, linePaint)
                }
                AnnotationType.STAMP -> {
                    // Draw a sleek stamped badge
                    val px = annot.normalizedX * pageWidth
                    val py = annot.normalizedY * pageHeight
                    val rect = RectF(px, py, px + annot.width, py + annot.height)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    canvas.drawRoundRect(rect, 8f, 8f, paint)

                    paint.style = Paint.Style.FILL
                    paint.textSize = 15f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val textW = paint.measureText(annot.text)
                    val textX = px + (annot.width - textW) / 2f
                    val textY = py + annot.height / 2f + 5f
                    canvas.drawText(annot.text, textX, textY, paint)
                }
                AnnotationType.FORM_TEXT_FIELD -> {
                    val px = annot.normalizedX * pageWidth
                    val py = annot.normalizedY * pageHeight
                    val rect = RectF(px, py, px + annot.width, py + annot.height)

                    // Field box background & border
                    val bgPaint = Paint().apply {
                        color = Color.argb(40, 79, 70, 229)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(rect, bgPaint)

                    val borderPaint = Paint().apply {
                        color = Color.rgb(79, 70, 229)
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5f
                    }
                    canvas.drawRect(rect, borderPaint)

                    paint.style = Paint.Style.FILL
                    paint.textSize = 14f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText(annot.text, px + 8f, py + annot.height * 0.7f, paint)
                }
                AnnotationType.FORM_CHECKBOX -> {
                    val px = annot.normalizedX * pageWidth
                    val py = annot.normalizedY * pageHeight
                    val size = 22f
                    val rect = RectF(px, py, px + size, py + size)

                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    canvas.drawRoundRect(rect, 4f, 4f, paint)

                    if (annot.isChecked) {
                        paint.style = Paint.Style.FILL
                        paint.strokeWidth = 2.5f
                        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = annot.colorArgb.toInt()
                            style = Paint.Style.STROKE
                            strokeWidth = 2.5f
                            strokeCap = Paint.Cap.ROUND
                        }
                        canvas.drawLine(px + 4f, py + 12f, px + 9f, py + 17f, checkPaint)
                        canvas.drawLine(px + 9f, py + 17f, px + 18f, py + 6f, checkPaint)
                    }

                    if (annot.text.isNotEmpty()) {
                        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.BLACK
                            textSize = 14f
                        }
                        canvas.drawText(annot.text, px + size + 8f, py + 16f, textPaint)
                    }
                }
            }
        }
    }

    // Page Management operations
    suspend fun addBlankPage(originalFile: File, atIndex: Int, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(originalFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount
                    val pdfDoc = PdfDocument()

                    var pageNumber = 1
                    for (i in 0..pageCount) {
                        if (i == atIndex) {
                            // Insert blank A4 page (595 x 842 points)
                            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber++).create()
                            val blankPage = pdfDoc.startPage(pageInfo)
                            blankPage.canvas.drawColor(Color.WHITE)
                            pdfDoc.finishPage(blankPage)
                        }
                        if (i < pageCount) {
                            renderer.openPage(i).use { page ->
                                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageNumber++).create()
                                val pdfPage = pdfDoc.startPage(pageInfo)
                                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                                bitmap.recycle()
                                pdfDoc.finishPage(pdfPage)
                            }
                        }
                    }
                    FileOutputStream(outputFile).use { out -> pdfDoc.writeTo(out) }
                    pdfDoc.close()
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deletePage(originalFile: File, pageIndexToDelete: Int, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(originalFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount
                    if (pageCount <= 1) return@withContext false // cannot delete only page

                    val pdfDoc = PdfDocument()
                    var pageNumber = 1
                    for (i in 0 until pageCount) {
                        if (i == pageIndexToDelete) continue
                        renderer.openPage(i).use { page ->
                            val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageNumber++).create()
                            val pdfPage = pdfDoc.startPage(pageInfo)
                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                            bitmap.recycle()
                            pdfDoc.finishPage(pdfPage)
                        }
                    }
                    FileOutputStream(outputFile).use { out -> pdfDoc.writeTo(out) }
                    pdfDoc.close()
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun duplicatePage(originalFile: File, pageIndexToDuplicate: Int, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(originalFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount
                    val pdfDoc = PdfDocument()
                    var pageNumber = 1

                    for (i in 0 until pageCount) {
                        renderer.openPage(i).use { page ->
                            val drawPage = {
                                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageNumber++).create()
                                val pdfPage = pdfDoc.startPage(pageInfo)
                                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                                bitmap.recycle()
                                pdfDoc.finishPage(pdfPage)
                            }

                            drawPage()
                            if (i == pageIndexToDuplicate) {
                                drawPage() // Draw duplicate right after
                            }
                        }
                    }
                    FileOutputStream(outputFile).use { out -> pdfDoc.writeTo(out) }
                    pdfDoc.close()
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun reorderPages(originalFile: File, newOrder: List<Int>, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(originalFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pdfDoc = PdfDocument()
                    var pageNumber = 1

                    for (pageIdx in newOrder) {
                        if (pageIdx in 0 until renderer.pageCount) {
                            renderer.openPage(pageIdx).use { page ->
                                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageNumber++).create()
                                val pdfPage = pdfDoc.startPage(pageInfo)
                                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                                bitmap.recycle()
                                pdfDoc.finishPage(pdfPage)
                            }
                        }
                    }
                    FileOutputStream(outputFile).use { out -> pdfDoc.writeTo(out) }
                    pdfDoc.close()
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun mergePdfs(files: List<File>, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val pdfDoc = PdfDocument()
            var pageNumber = 1

            for (file in files) {
                if (!file.exists()) continue
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageNumber++).create()
                                val pdfPage = pdfDoc.startPage(pageInfo)
                                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                                bitmap.recycle()
                                pdfDoc.finishPage(pdfPage)
                            }
                        }
                    }
                }
            }
            FileOutputStream(outputFile).use { out -> pdfDoc.writeTo(out) }
            pdfDoc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun splitPdf(file: File, pageRange: IntRange, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pdfDoc = PdfDocument()
                    var pageNumber = 1

                    for (i in pageRange) {
                        if (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageNumber++).create()
                                val pdfPage = pdfDoc.startPage(pageInfo)
                                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                                bitmap.recycle()
                                pdfDoc.finishPage(pdfPage)
                            }
                        }
                    }
                    FileOutputStream(outputFile).use { out -> pdfDoc.writeTo(out) }
                    pdfDoc.close()
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun compressPdf(file: File, outputFile: File, scaleFactor: Float = 0.65f): Boolean = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pdfDoc = PdfDocument()

                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val scaledW = (page.width * scaleFactor).toInt().coerceAtLeast(200)
                            val scaledH = (page.height * scaleFactor).toInt().coerceAtLeast(200)

                            val bitmap = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pageInfo = PdfDocument.PageInfo.Builder(scaledW, scaledH, i + 1).create()
                            val pdfPage = pdfDoc.startPage(pageInfo)
                            pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                            bitmap.recycle()
                            pdfDoc.finishPage(pdfPage)
                        }
                    }
                    FileOutputStream(outputFile).use { out -> pdfDoc.writeTo(out) }
                    pdfDoc.close()
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportPagesAsImages(file: File, outputDir: File): List<File> = withContext(Dispatchers.IO) {
        val exportedFiles = mutableListOf<File>()
        try {
            if (!outputDir.exists()) outputDir.mkdirs()
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val imgFile = File(outputDir, "${file.nameWithoutExtension}_page_${i + 1}.jpg")
                            FileOutputStream(imgFile).use { fos ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                            }
                            bitmap.recycle()
                            exportedFiles.add(imgFile)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        exportedFiles
    }

    suspend fun convertImagesToPdf(imageFiles: List<File>, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val pdfDoc = PdfDocument()
            var pageNumber = 1

            for (imgFile in imageFiles) {
                if (!imgFile.exists()) continue
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath) ?: continue
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageNumber++).create()
                val page = pdfDoc.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()
                pdfDoc.finishPage(page)
            }

            FileOutputStream(outputFile).use { out -> pdfDoc.writeTo(out) }
            pdfDoc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun convertTextToPdf(text: String, title: String, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val pdfDoc = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 40f
            val contentWidth = pageWidth - margin * 2

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 20, 20)
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(45, 55, 72)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val lineSpacing = 20f

            // Break lines
            val lines = mutableListOf<String>()
            text.lines().forEach { rawLine ->
                if (rawLine.isEmpty()) {
                    lines.add("")
                } else {
                    var currentLine = ""
                    val words = rawLine.split(" ")
                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        if (bodyPaint.measureText(testLine) <= contentWidth) {
                            currentLine = testLine
                        } else {
                            lines.add(currentLine)
                            currentLine = word
                        }
                    }
                    if (currentLine.isNotEmpty()) lines.add(currentLine)
                }
            }

            var lineIndex = 0
            var pageNumber = 1

            while (lineIndex < lines.size || pageNumber == 1) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                var yPos = margin + 30f

                // Header on page 1
                if (pageNumber == 1) {
                    canvas.drawText(title, margin, yPos, titlePaint)
                    yPos += 14f
                    val headerLinePaint = Paint().apply {
                        color = Color.rgb(220, 38, 38)
                        strokeWidth = 3f
                    }
                    canvas.drawLine(margin, yPos, margin + 80f, yPos, headerLinePaint)
                    yPos += 30f
                }

                while (lineIndex < lines.size && yPos + lineSpacing < pageHeight - margin) {
                    val line = lines[lineIndex]
                    canvas.drawText(line, margin, yPos, bodyPaint)
                    yPos += lineSpacing
                    lineIndex++
                }

                // Page footer
                val footerPaint = Paint().apply {
                    color = Color.GRAY
                    textSize = 10f
                }
                val footerText = "Page $pageNumber"
                val fw = footerPaint.measureText(footerText)
                canvas.drawText(footerText, pageWidth - margin - fw, pageHeight - margin / 2f, footerPaint)

                pdfDoc.finishPage(page)
                pageNumber++
                if (lineIndex >= lines.size) break
            }

            FileOutputStream(outputFile).use { out -> pdfDoc.writeTo(out) }
            pdfDoc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Helper to generate professional sample documents so the user can immediately test!
    suspend fun generateSamplePdfs(context: Context): List<File> = withContext(Dispatchers.IO) {
        val sampleDir = File(context.filesDir, "sample_pdfs")
        if (!sampleDir.exists()) sampleDir.mkdirs()

        val invoiceFile = File(sampleDir, "Business_Invoice_and_Proposal.pdf")
        if (!invoiceFile.exists() || invoiceFile.length() == 0L) {
            createSampleInvoicePdf(invoiceFile)
        }

        val contractFile = File(sampleDir, "Employment_Agreement.pdf")
        if (!contractFile.exists() || contractFile.length() == 0L) {
            createSampleContractPdf(contractFile)
        }

        val aiDocFile = File(sampleDir, "AI_Technology_Report.pdf")
        if (!aiDocFile.exists() || aiDocFile.length() == 0L) {
            createSampleTechReportPdf(aiDocFile)
        }

        listOf(invoiceFile, contractFile, aiDocFile)
    }

    private fun createSampleInvoicePdf(file: File) {
        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(217, 45, 58)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 12f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 13f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Header
        canvas.drawText("JAHUR TECH SOLUTIONS", 40f, 60f, headerPaint)
        canvas.drawText("TAX INVOICE & PROJECT PROPOSAL", 40f, 80f, boldPaint)
        canvas.drawText("Invoice #: INV-2026-0894 | Date: 30 September 2026", 40f, 100f, subPaint)

        // Divider
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 2f
        }
        canvas.drawLine(40f, 115f, 555f, 115f, linePaint)

        // Billed To / From
        canvas.drawText("Billed To:", 40f, 140f, boldPaint)
        canvas.drawText("Apex Global Enterprise Ltd.", 40f, 160f, textPaint)
        canvas.drawText("Email: billing@apexglobal.com", 40f, 178f, subPaint)
        canvas.drawText("Dubai Internet City, UAE", 40f, 196f, subPaint)

        canvas.drawText("Service Provider:", 320f, 140f, boldPaint)
        canvas.drawText("Jahur Mobile Systems", 320f, 160f, textPaint)
        canvas.drawText("Email: jahur4974@gmail.com", 320f, 178f, subPaint)
        canvas.drawText("GSTIN/VAT: 9924-AE-871109", 320f, 196f, subPaint)

        // Items Table Header
        val tableBg = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRect(40f, 225f, 555f, 255f, tableBg)
        canvas.drawText("Description", 50f, 245f, boldPaint)
        canvas.drawText("Hours", 320f, 245f, boldPaint)
        canvas.drawText("Rate", 410f, 245f, boldPaint)
        canvas.drawText("Amount", 490f, 245f, boldPaint)

        var y = 285f
        val items = listOf(
            Triple("1. Android Native Architecture & Jetpack Compose UI", "60 hrs", "$4,800.00"),
            Triple("2. Gemini AI Integration & Multimodal Processing", "40 hrs", "$3,600.00"),
            Triple("3. PDF Engine, Canvas Drawing & OCR Pipeline", "50 hrs", "$4,250.00"),
            Triple("4. Security, Room DB Encryption & Cloud Export", "25 hrs", "$2,000.00")
        )
        for (item in items) {
            canvas.drawText(item.first, 50f, y, textPaint)
            canvas.drawText(item.second, 330f, y, subPaint)
            canvas.drawText("$80/hr", 410f, y, subPaint)
            canvas.drawText(item.third, 490f, y, textPaint)
            canvas.drawLine(40f, y + 10f, 555f, y + 10f, linePaint)
            y += 35f
        }

        // Total Summary Box
        y += 20f
        val totalBox = Paint().apply { color = Color.rgb(254, 242, 242) }
        canvas.drawRoundRect(RectF(320f, y, 555f, y + 90f), 8f, 8f, totalBox)
        canvas.drawText("Subtotal:", 340f, y + 25f, subPaint)
        canvas.drawText("$14,650.00", 470f, y + 25f, textPaint)
        canvas.drawText("Tax (VAT 5%):", 340f, y + 50f, subPaint)
        canvas.drawText("$732.50", 470f, y + 50f, textPaint)
        canvas.drawText("Grand Total:", 340f, y + 75f, boldPaint)
        val grandTotalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(217, 45, 58)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("$15,382.50", 460f, y + 75f, grandTotalPaint)

        // Terms & Form Signatures
        y += 120f
        canvas.drawText("Terms & Conditions:", 40f, y, boldPaint)
        y += 20f
        canvas.drawText("• Payment due within 15 business days (Due: October 15, 2026).", 40f, y, subPaint)
        y += 18f
        canvas.drawText("• Milestone Deliverables: Alpha preview, Beta QA, Production Store release.", 40f, y, subPaint)
        y += 18f
        canvas.drawText("• Accepted in English, Hindi (हिंदी) and Arabic (العربية).", 40f, y, subPaint)

        // Signature placeholders
        y += 50f
        canvas.drawLine(40f, y + 30f, 200f, y + 30f, linePaint)
        canvas.drawText("Authorized Signature", 40f, y + 48f, subPaint)

        canvas.drawLine(350f, y + 30f, 510f, y + 30f, linePaint)
        canvas.drawText("Client Acceptance Date", 350f, y + 48f, subPaint)

        pdfDoc.finishPage(page)
        FileOutputStream(file).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
    }

    private fun createSampleContractPdf(file: File) {
        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 12f
        }

        canvas.drawText("CONFIDENTIALITY & SERVICE AGREEMENT", 40f, 60f, headerPaint)
        canvas.drawText("Effective Date: October 1, 2026", 40f, 85f, textPaint)

        var y = 120f
        canvas.drawText("1. Parties Involved", 40f, y, boldPaint)
        y += 20f
        canvas.drawText("This Non-Disclosure Agreement (NDA) is entered into between Jahur AI Corp", 40f, y, textPaint)
        y += 18f
        canvas.drawText("and the Contractor / Recipient. Both parties agree to protect proprietary source code.", 40f, y, textPaint)

        y += 35f
        canvas.drawText("2. Scope of Confidential Information", 40f, y, boldPaint)
        y += 20f
        canvas.drawText("Confidential Information includes AI algorithms, database schemas, customer data,", 40f, y, textPaint)
        y += 18f
        canvas.drawText("PDF generation engines, and encryption keys. Disclosure without written consent is prohibited.", 40f, y, textPaint)

        y += 35f
        canvas.drawText("3. Interactive Form Fields Checklist", 40f, y, boldPaint)
        y += 24f
        val boxPaint = Paint().apply {
            color = Color.GRAY
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRect(40f, y - 12f, 54f, y + 2f, boxPaint)
        canvas.drawText("I agree to all non-disclosure terms and confidentiality obligations.", 64f, y, textPaint)
        y += 24f
        canvas.drawRect(40f, y - 12f, 54f, y + 2f, boxPaint)
        canvas.drawText("I confirm I have received the developer credentials and security token.", 64f, y, textPaint)
        y += 24f
        canvas.drawRect(40f, y - 12f, 54f, y + 2f, boxPaint)
        canvas.drawText("I authorize identity verification via legal Government photo ID.", 64f, y, textPaint)

        y += 60f
        canvas.drawText("Signatures & Acknowledgment", 40f, y, boldPaint)
        y += 40f
        val signLine = Paint().apply { color = Color.LTGRAY; strokeWidth = 2f }
        canvas.drawLine(40f, y, 220f, y, signLine)
        canvas.drawText("Contractor Signature (Sign here)", 40f, y + 20f, textPaint)

        canvas.drawLine(340f, y, 520f, y, signLine)
        canvas.drawText("Company Executive Signature", 340f, y + 20f, textPaint)

        pdfDoc.finishPage(page)
        FileOutputStream(file).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
    }

    private fun createSampleTechReportPdf(file: File) {
        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(79, 70, 229)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 12f
        }

        canvas.drawText("ARTIFICIAL INTELLIGENCE RESEARCH REPORT", 40f, 60f, titlePaint)
        canvas.drawText("Volume 14, Issue 3 • Jahur AI Labs • Published September 2026", 40f, 82f, textPaint)

        var y = 120f
        canvas.drawText("Executive Summary / सारांश / ملخص", 40f, y, boldPaint)
        y += 20f
        canvas.drawText("This research paper investigates multimodal reasoning in document analysis engines.", 40f, y, textPaint)
        y += 18f
        canvas.drawText("With the advent of Gemini 3.5 Flash and fast native OCR pipelines, mobile apps can", 40f, y, textPaint)
        y += 18f
        canvas.drawText("now parse complex tables, handwritten notes, and multilingual forms instantly.", 40f, y, textPaint)

        y += 35f
        canvas.drawText("Key Findings & Milestones (2026 - 2027)", 40f, y, boldPaint)
        y += 20f
        canvas.drawText("• Milestone Alpha (November 15, 2026): 99.4% OCR accuracy in Hindi and Arabic.", 40f, y, textPaint)
        y += 18f
        canvas.drawText("• Milestone Beta (January 20, 2027): Zero-latency on-device text-to-speech engine.", 40f, y, textPaint)
        y += 18f
        canvas.drawText("• Milestone Gold (March 30, 2027): Instant cross-language bidirectional translation.", 40f, y, textPaint)

        y += 35f
        canvas.drawText("Multilingual Support Verification", 40f, y, boldPaint)
        y += 20f
        canvas.drawText("English: Document editing, digital signatures, and page management.", 40f, y, textPaint)
        y += 18f
        canvas.drawText("Hindi: दस्तावेज़ संपादन, डिजिटल हस्ताक्षर, और पृष्ठ प्रबंधन।", 40f, y, textPaint)
        y += 18f
        canvas.drawText("Arabic: تحرير المستندات، التوقيع الرقمي، وإدارة الصفحات.", 40f, y, textPaint)

        y += 35f
        canvas.drawText("Key Contact Persons:", 40f, y, boldPaint)
        y += 20f
        canvas.drawText("Lead Researcher: Dr. Jahur Ahmed | Phone: +91-98765-43210", 40f, y, textPaint)
        y += 18f
        canvas.drawText("QA Lead: Sarah Jenkins | Email: sarah.j@jahurai.org", 40f, y, textPaint)

        pdfDoc.finishPage(page)
        FileOutputStream(file).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
    }
}
