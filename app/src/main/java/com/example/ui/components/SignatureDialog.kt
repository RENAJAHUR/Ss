package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PointF

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SignatureDialog(
    onDismiss: () -> Unit,
    onSaveSignature: (points: List<PointF>, colorArgb: Long) -> Unit
) {
    val strokePoints = remember { mutableStateListOf<Offset>() }
    var selectedColor by remember { mutableStateOf(0xFF1E293B) } // Dark ink

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Draw Digital Signature", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Sign inside the box below with your finger or stylus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Ink Color Chooser
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    val colors = listOf(0xFF1E293B, 0xFF1D4ED8, 0xFFDC2626)
                    val labels = listOf("Black Ink", "Blue Ink", "Red Ink")
                    colors.forEachIndexed { i, c ->
                        OutlinedButton(
                            onClick = { selectedColor = c },
                            colors = if (selectedColor == c) ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(c).copy(alpha = 0.15f)
                            ) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(c))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(labels[i], style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Drawing Pad
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .background(Color(0xFFFAFAFA))
                        .testTag("signature_canvas")
                        .pointerInteropFilter { motionEvent ->
                            when (motionEvent.action) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                    strokePoints.add(Offset(motionEvent.x, motionEvent.y))
                                    true
                                }
                                MotionEvent.ACTION_UP -> {
                                    strokePoints.add(Offset.Unspecified) // Break line
                                    true
                                }
                                else -> false
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val path = Path()
                        var isFirst = true

                        strokePoints.forEach { pt ->
                            if (pt == Offset.Unspecified) {
                                isFirst = true
                            } else if (isFirst) {
                                path.moveTo(pt.x, pt.y)
                                isFirst = false
                            } else {
                                path.lineTo(pt.x, pt.y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = Color(selectedColor),
                            style = Stroke(
                                width = 5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Watermark guide line
                    Text(
                        "Signature Line ____________________",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (strokePoints.isNotEmpty()) {
                        // Normalize points to 0.0 - 1.0 bounding box
                        val validPoints = strokePoints.filter { it != Offset.Unspecified }
                        if (validPoints.isNotEmpty()) {
                            val minX = validPoints.minOf { it.x }
                            val maxX = validPoints.maxOf { it.x }
                            val minY = validPoints.minOf { it.y }
                            val maxY = validPoints.maxOf { it.y }
                            val w = (maxX - minX).coerceAtLeast(1f)
                            val h = (maxY - minY).coerceAtLeast(1f)

                            val normalized = strokePoints.map {
                                if (it == Offset.Unspecified) PointF(-1f, -1f)
                                else PointF((it.x - minX) / w, (it.y - minY) / h)
                            }
                            onSaveSignature(normalized, selectedColor)
                        }
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("save_signature_btn")
            ) {
                Icon(Icons.Default.Done, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Place on PDF")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(
                    onClick = { strokePoints.clear() },
                    modifier = Modifier.testTag("clear_signature_btn")
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors()
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
