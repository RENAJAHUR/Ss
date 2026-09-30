package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import com.example.data.model.ActiveEditorTool
import com.example.data.model.AnnotationType
import com.example.data.model.PageAnnotation
import com.example.data.model.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AnnotationCanvasOverlay(
    pageIndex: Int,
    activeTool: ActiveEditorTool,
    selectedColor: Long,
    strokeWidth: Float,
    annotations: List<PageAnnotation>,
    onAddAnnotation: (PageAnnotation) -> Unit,
    onRemoveAnnotation: (String) -> Unit,
    onToggleCheckbox: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current in-progress drawing points
    val livePoints = remember { mutableStateListOf<Offset>() }
    var dragStartOffset by remember { mutableStateOf<Offset?>(null) }
    var dragCurrentOffset by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("annotation_canvas_overlay")
            .pointerInteropFilter { event ->
                if (activeTool == ActiveEditorTool.VIEW) {
                    return@pointerInteropFilter false // let parent handle zoom/pan
                }

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val touchOffset = Offset(event.x, event.y)
                        dragStartOffset = touchOffset
                        dragCurrentOffset = touchOffset

                        if (activeTool == ActiveEditorTool.PEN || activeTool == ActiveEditorTool.HIGHLIGHTER) {
                            livePoints.clear()
                            livePoints.add(touchOffset)
                        } else if (activeTool == ActiveEditorTool.ERASER) {
                            // Find nearest annotation to touch
                            annotations.find { annot ->
                                annot.pageIndex == pageIndex // simplified distance check
                            }?.let { onRemoveAnnotation(it.id) }
                        } else if (activeTool == ActiveEditorTool.FORM_CHECKBOX) {
                            // Check if tapped near an existing checkbox
                            val box = annotations.find {
                                it.pageIndex == pageIndex && it.type == AnnotationType.FORM_CHECKBOX
                            }
                            if (box != null) {
                                onToggleCheckbox(box.id)
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val touchOffset = Offset(event.x, event.y)
                        dragCurrentOffset = touchOffset
                        if (activeTool == ActiveEditorTool.PEN || activeTool == ActiveEditorTool.HIGHLIGHTER) {
                            livePoints.add(touchOffset)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val start = dragStartOffset
                        val end = dragCurrentOffset

                        if (start != null && end != null) {
                            when (activeTool) {
                                ActiveEditorTool.PEN -> {
                                    if (livePoints.isNotEmpty()) {
                                        val normPoints = livePoints.map { PointF(it.x / 1000f, it.y / 1400f) }
                                        onAddAnnotation(
                                            PageAnnotation(
                                                pageIndex = pageIndex,
                                                type = AnnotationType.DRAW_PATH,
                                                points = normPoints,
                                                colorArgb = selectedColor,
                                                strokeWidth = strokeWidth
                                            )
                                        )
                                    }
                                }
                                ActiveEditorTool.HIGHLIGHTER -> {
                                    if (livePoints.isNotEmpty()) {
                                        val normPoints = livePoints.map { PointF(it.x / 1000f, it.y / 1400f) }
                                        onAddAnnotation(
                                            PageAnnotation(
                                                pageIndex = pageIndex,
                                                type = AnnotationType.HIGHLIGHT_PATH,
                                                points = normPoints,
                                                colorArgb = selectedColor,
                                                strokeWidth = strokeWidth.coerceAtLeast(18f)
                                            )
                                        )
                                    }
                                }
                                ActiveEditorTool.SHAPE_RECTANGLE -> {
                                    val left = minOf(start.x, end.x)
                                    val top = minOf(start.y, end.y)
                                    val w = kotlin.math.abs(end.x - start.x)
                                    val h = kotlin.math.abs(end.y - start.y)
                                    if (w > 10f && h > 10f) {
                                        onAddAnnotation(
                                            PageAnnotation(
                                                pageIndex = pageIndex,
                                                type = AnnotationType.SHAPE_RECTANGLE,
                                                normalizedX = left / 1000f,
                                                normalizedY = top / 1400f,
                                                width = w,
                                                height = h,
                                                colorArgb = selectedColor,
                                                strokeWidth = strokeWidth
                                            )
                                        )
                                    }
                                }
                                ActiveEditorTool.SHAPE_CIRCLE -> {
                                    val left = minOf(start.x, end.x)
                                    val top = minOf(start.y, end.y)
                                    val size = maxOf(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y))
                                    if (size > 10f) {
                                        onAddAnnotation(
                                            PageAnnotation(
                                                pageIndex = pageIndex,
                                                type = AnnotationType.SHAPE_CIRCLE,
                                                normalizedX = left / 1000f,
                                                normalizedY = top / 1400f,
                                                width = size,
                                                height = size,
                                                colorArgb = selectedColor,
                                                strokeWidth = strokeWidth
                                            )
                                        )
                                    }
                                }
                                ActiveEditorTool.SHAPE_ARROW -> {
                                    val dx = end.x - start.x
                                    val dy = end.y - start.y
                                    onAddAnnotation(
                                        PageAnnotation(
                                            pageIndex = pageIndex,
                                            type = AnnotationType.SHAPE_ARROW,
                                            normalizedX = start.x / 1000f,
                                            normalizedY = start.y / 1400f,
                                            width = dx,
                                            height = dy,
                                            colorArgb = selectedColor,
                                            strokeWidth = strokeWidth
                                        )
                                    )
                                }
                                ActiveEditorTool.SHAPE_LINE -> {
                                    val dx = end.x - start.x
                                    val dy = end.y - start.y
                                    onAddAnnotation(
                                        PageAnnotation(
                                            pageIndex = pageIndex,
                                            type = AnnotationType.SHAPE_LINE,
                                            normalizedX = start.x / 1000f,
                                            normalizedY = start.y / 1400f,
                                            width = dx,
                                            height = dy,
                                            colorArgb = selectedColor,
                                            strokeWidth = strokeWidth
                                        )
                                    )
                                }
                                else -> {}
                            }
                        }

                        livePoints.clear()
                        dragStartOffset = null
                        dragCurrentOffset = null
                        true
                    }

                    else -> false
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cWidth = size.width
            val cHeight = size.height

            // 1. Draw existing committed annotations for this page
            annotations.filter { it.pageIndex == pageIndex }.forEach { annot ->
                val annotColor = Color(annot.colorArgb)

                when (annot.type) {
                    AnnotationType.DRAW_PATH -> {
                        val path = Path()
                        annot.points.forEachIndexed { idx, pt ->
                            val px = pt.x * 1000f * (cWidth / 1000f)
                            val py = pt.y * 1400f * (cHeight / 1400f)
                            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(
                            path = path,
                            color = annotColor,
                            style = Stroke(width = annot.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    AnnotationType.HIGHLIGHT_PATH -> {
                        val path = Path()
                        annot.points.forEachIndexed { idx, pt ->
                            val px = pt.x * 1000f * (cWidth / 1000f)
                            val py = pt.y * 1400f * (cHeight / 1400f)
                            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(
                            path = path,
                            color = annotColor.copy(alpha = 0.45f),
                            style = Stroke(width = annot.strokeWidth, cap = StrokeCap.Square)
                        )
                    }

                    AnnotationType.TEXT -> {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = annot.colorArgb.toInt()
                                textSize = annot.fontSize * 1.5f
                                isUnderlineText = annot.isUnderline
                                isFakeBoldText = annot.isBold
                            }
                            val posX = annot.normalizedX * cWidth
                            val posY = annot.normalizedY * cHeight
                            drawText(annot.text, posX, posY, paint)
                        }
                    }

                    AnnotationType.SHAPE_RECTANGLE -> {
                        val left = annot.normalizedX * 1000f * (cWidth / 1000f)
                        val top = annot.normalizedY * 1400f * (cHeight / 1400f)
                        drawRect(
                            color = annotColor,
                            topLeft = Offset(left, top),
                            size = Size(annot.width, annot.height),
                            style = Stroke(width = annot.strokeWidth)
                        )
                    }

                    AnnotationType.SHAPE_CIRCLE -> {
                        val left = annot.normalizedX * 1000f * (cWidth / 1000f)
                        val top = annot.normalizedY * 1400f * (cHeight / 1400f)
                        val radius = annot.width / 2f
                        drawCircle(
                            color = annotColor,
                            radius = radius,
                            center = Offset(left + radius, top + radius),
                            style = Stroke(width = annot.strokeWidth)
                        )
                    }

                    AnnotationType.SHAPE_ARROW, AnnotationType.SHAPE_LINE -> {
                        val startX = annot.normalizedX * 1000f * (cWidth / 1000f)
                        val startY = annot.normalizedY * 1400f * (cHeight / 1400f)
                        val endX = startX + annot.width
                        val endY = startY + annot.height

                        drawLine(
                            color = annotColor,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = annot.strokeWidth,
                            cap = StrokeCap.Round
                        )

                        if (annot.type == AnnotationType.SHAPE_ARROW) {
                            val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
                            val arrowLen = 22f
                            val p1x = (endX - arrowLen * cos(angle - Math.PI / 6)).toFloat()
                            val p1y = (endY - arrowLen * sin(angle - Math.PI / 6)).toFloat()
                            val p2x = (endX - arrowLen * cos(angle + Math.PI / 6)).toFloat()
                            val p2y = (endY - arrowLen * sin(angle + Math.PI / 6)).toFloat()
                            drawLine(color = annotColor, start = Offset(endX, endY), end = Offset(p1x, p1y), strokeWidth = annot.strokeWidth)
                            drawLine(color = annotColor, start = Offset(endX, endY), end = Offset(p2x, p2y), strokeWidth = annot.strokeWidth)
                        }
                    }

                    AnnotationType.SIGNATURE -> {
                        val baseX = annot.normalizedX * cWidth
                        val baseY = annot.normalizedY * cHeight
                        val path = Path()
                        annot.points.forEachIndexed { idx, pt ->
                            if (pt.x >= 0f) {
                                val px = baseX + pt.x * annot.width
                                val py = baseY + pt.y * annot.height
                                if (idx == 0 || (idx > 0 && annot.points[idx - 1].x < 0f)) {
                                    path.moveTo(px, py)
                                } else {
                                    path.lineTo(px, py)
                                }
                            }
                        }
                        drawPath(
                            path = path,
                            color = annotColor,
                            style = Stroke(width = annot.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    AnnotationType.STAMP -> {
                        val px = annot.normalizedX * cWidth
                        val py = annot.normalizedY * cHeight
                        drawRoundRect(
                            color = annotColor,
                            topLeft = Offset(px, py),
                            size = Size(annot.width, annot.height),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = 3f)
                        )
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = annot.colorArgb.toInt()
                                textSize = 22f
                                isFakeBoldText = true
                            }
                            drawText(annot.text, px + 12f, py + annot.height * 0.65f, paint)
                        }
                    }

                    AnnotationType.FORM_CHECKBOX -> {
                        val px = annot.normalizedX * cWidth
                        val py = annot.normalizedY * cHeight
                        val boxSize = 28f
                        drawRoundRect(
                            color = Color.Black,
                            topLeft = Offset(px, py),
                            size = Size(boxSize, boxSize),
                            cornerRadius = CornerRadius(4f, 4f),
                            style = Stroke(width = 2f)
                        )
                        if (annot.isChecked) {
                            drawLine(
                                color = annotColor,
                                start = Offset(px + 5f, py + 14f),
                                end = Offset(px + 11f, py + 22f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = annotColor,
                                start = Offset(px + 11f, py + 22f),
                                end = Offset(px + 23f, py + 6f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    else -> {}
                }
            }

            // 2. Draw active in-progress gesture
            if (livePoints.isNotEmpty()) {
                val livePath = Path()
                livePoints.forEachIndexed { i, pt ->
                    if (i == 0) livePath.moveTo(pt.x, pt.y) else livePath.lineTo(pt.x, pt.y)
                }
                val isHighlighter = activeTool == ActiveEditorTool.HIGHLIGHTER
                drawPath(
                    path = livePath,
                    color = Color(selectedColor).copy(alpha = if (isHighlighter) 0.45f else 1f),
                    style = Stroke(
                        width = strokeWidth,
                        cap = if (isHighlighter) StrokeCap.Square else StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            } else if (dragStartOffset != null && dragCurrentOffset != null) {
                val s = dragStartOffset!!
                val e = dragCurrentOffset!!
                val c = Color(selectedColor)

                when (activeTool) {
                    ActiveEditorTool.SHAPE_RECTANGLE -> {
                        drawRect(
                            color = c,
                            topLeft = Offset(minOf(s.x, e.x), minOf(s.y, e.y)),
                            size = Size(kotlin.math.abs(e.x - s.x), kotlin.math.abs(e.y - s.y)),
                            style = Stroke(width = strokeWidth)
                        )
                    }
                    ActiveEditorTool.SHAPE_CIRCLE -> {
                        val radius = maxOf(kotlin.math.abs(e.x - s.x), kotlin.math.abs(e.y - s.y)) / 2f
                        drawCircle(color = c, radius = radius, center = s, style = Stroke(width = strokeWidth))
                    }
                    ActiveEditorTool.SHAPE_ARROW, ActiveEditorTool.SHAPE_LINE -> {
                        drawLine(color = c, start = s, end = e, strokeWidth = strokeWidth, cap = StrokeCap.Round)
                    }
                    else -> {}
                }
            }
        }
    }
}
