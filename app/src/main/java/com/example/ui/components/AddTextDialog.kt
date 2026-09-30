package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddTextDialog(
    onDismiss: () -> Unit,
    onConfirm: (text: String, fontSize: Float, colorArgb: Long, isBold: Boolean, isItalic: Boolean, isUnderline: Boolean) -> Unit
) {
    var textValue by remember { mutableStateOf("") }
    var fontSize by remember { mutableFloatStateOf(18f) }
    var selectedColor by remember { mutableLongStateOf(0xFF1E293B) }
    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }
    var isUnderline by remember { mutableStateOf(false) }

    val presetColors = listOf(
        0xFF1E293B, // Dark Black
        0xFFDC2626, // Red
        0xFF2563EB, // Blue
        0xFF16A34A, // Green
        0xFFD97706, // Amber
        0xFF9333EA  // Purple
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Text to Document", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Enter text") },
                    placeholder = { Text("Type here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("text_input_field")
                )

                // Quick presets (Date, Approved, Verified)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            textValue = dateFormat.format(Date())
                        },
                        label = { Text("Today's Date", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { textValue = "APPROVED" },
                        label = { Text("APPROVED", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { textValue = "CONFIDENTIAL" },
                        label = { Text("CONFIDENTIAL", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                // Font Styling Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        IconToggleButton(checked = isBold, onCheckedChange = { isBold = it }) {
                            Icon(Icons.Default.FormatBold, contentDescription = "Bold")
                        }
                        IconToggleButton(checked = isItalic, onCheckedChange = { isItalic = it }) {
                            Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
                        }
                        IconToggleButton(checked = isUnderline, onCheckedChange = { isUnderline = it }) {
                            Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline")
                        }
                    }

                    Text("Size: ${fontSize.toInt()}sp", style = MaterialTheme.typography.labelMedium)
                }

                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 12f..48f,
                    modifier = Modifier.fillMaxWidth()
                )

                // Color Chooser
                Text("Text Color", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    presetColors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { selectedColor = c }
                        )
                    }
                }

                // Live Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (textValue.isNotBlank()) textValue else "Preview Text",
                        fontSize = (fontSize * 0.8f).sp,
                        color = Color(selectedColor),
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textValue.isNotBlank()) {
                        onConfirm(textValue, fontSize, selectedColor, isBold, isItalic, isUnderline)
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_add_text_btn")
            ) {
                Text("Insert Text")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
