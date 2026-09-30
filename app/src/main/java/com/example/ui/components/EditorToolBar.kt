package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ActiveEditorTool

@Composable
fun EditorToolBar(
    activeTool: ActiveEditorTool,
    selectedColor: Long,
    strokeWidth: Float,
    onSelectTool: (ActiveEditorTool) -> Unit,
    onSelectColor: (Long) -> Unit,
    onChangeStrokeWidth: (Float) -> Unit,
    onInsertStamp: (String) -> Unit,
    onInsertCheckbox: () -> Unit,
    onOpenAiSheet: () -> Unit,
    onReadAloud: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showShapesMenu by remember { mutableStateOf(false) }
    var showStampMenu by remember { mutableStateOf(false) }

    val paletteColors = listOf(
        0xFFD92D3A, // Crimson Red
        0xFF1E293B, // Deep Navy/Black
        0xFF2563EB, // Royal Blue
        0xFF16A34A, // Emerald Green
        0xFFF59E0B, // Amber
        0xFF9333EA  // Purple
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expandable Palette Bar when a drawing/shape tool is active
        val showPalette = activeTool in listOf(
            ActiveEditorTool.PEN,
            ActiveEditorTool.HIGHLIGHTER,
            ActiveEditorTool.SHAPE_RECTANGLE,
            ActiveEditorTool.SHAPE_CIRCLE,
            ActiveEditorTool.SHAPE_ARROW,
            ActiveEditorTool.SHAPE_LINE
        )

        AnimatedVisibility(
            visible = showPalette,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Color bubbles
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        paletteColors.forEach { c ->
                            val isSelected = selectedColor == c
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onSelectColor(c) }
                            )
                        }
                    }

                    // Stroke width slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(140.dp)
                    ) {
                        Text(
                            "${strokeWidth.toInt()}pt",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Slider(
                            value = strokeWidth,
                            onValueChange = onChangeStrokeWidth,
                            valueRange = 2f..24f
                        )
                    }
                }
            }
        }

        // Main Tool Bar Strip
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. View / Pan
                ToolIconButton(
                    icon = Icons.Default.PanTool,
                    label = "Pan",
                    isSelected = activeTool == ActiveEditorTool.VIEW,
                    onClick = { onSelectTool(ActiveEditorTool.VIEW) },
                    tag = "tool_view"
                )

                // 2. Pen
                ToolIconButton(
                    icon = Icons.Default.Draw,
                    label = "Draw",
                    isSelected = activeTool == ActiveEditorTool.PEN,
                    onClick = { onSelectTool(ActiveEditorTool.PEN) },
                    tag = "tool_pen"
                )

                // 3. Highlighter
                ToolIconButton(
                    icon = Icons.Default.Highlight,
                    label = "Highlight",
                    isSelected = activeTool == ActiveEditorTool.HIGHLIGHTER,
                    onClick = { onSelectTool(ActiveEditorTool.HIGHLIGHTER) },
                    tag = "tool_highlighter"
                )

                // 4. Text
                ToolIconButton(
                    icon = Icons.Default.TextFields,
                    label = "Text",
                    isSelected = activeTool == ActiveEditorTool.TEXT,
                    onClick = { onSelectTool(ActiveEditorTool.TEXT) },
                    tag = "tool_text"
                )

                // 5. Shapes Dropdown
                Box {
                    ToolIconButton(
                        icon = Icons.Default.Square,
                        label = "Shapes",
                        isSelected = activeTool in listOf(
                            ActiveEditorTool.SHAPE_RECTANGLE,
                            ActiveEditorTool.SHAPE_CIRCLE,
                            ActiveEditorTool.SHAPE_ARROW,
                            ActiveEditorTool.SHAPE_LINE
                        ),
                        onClick = { showShapesMenu = true },
                        tag = "tool_shapes"
                    )
                    DropdownMenu(
                        expanded = showShapesMenu,
                        onDismissRequest = { showShapesMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rectangle Box") },
                            onClick = { onSelectTool(ActiveEditorTool.SHAPE_RECTANGLE); showShapesMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Circle / Oval") },
                            onClick = { onSelectTool(ActiveEditorTool.SHAPE_CIRCLE); showShapesMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Arrow Pointer") },
                            onClick = { onSelectTool(ActiveEditorTool.SHAPE_ARROW); showShapesMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Straight Line") },
                            onClick = { onSelectTool(ActiveEditorTool.SHAPE_LINE); showShapesMenu = false }
                        )
                    }
                }

                // 6. Signature
                ToolIconButton(
                    icon = Icons.Default.Create,
                    label = "Sign",
                    isSelected = activeTool == ActiveEditorTool.SIGNATURE,
                    onClick = { onSelectTool(ActiveEditorTool.SIGNATURE) },
                    tag = "tool_signature"
                )

                // 7. Stamp Dropdown
                Box {
                    ToolIconButton(
                        icon = Icons.Default.BorderColor,
                        label = "Stamp",
                        isSelected = activeTool == ActiveEditorTool.STAMP,
                        onClick = { showStampMenu = true },
                        tag = "tool_stamp"
                    )
                    DropdownMenu(
                        expanded = showStampMenu,
                        onDismissRequest = { showStampMenu = false }
                    ) {
                        listOf("APPROVED", "CONFIDENTIAL", "PAID", "REJECTED", "VERIFIED").forEach { stampText ->
                            DropdownMenuItem(
                                text = { Text(stampText) },
                                onClick = { onInsertStamp(stampText); showStampMenu = false }
                            )
                        }
                    }
                }

                // 8. Checkbox Form Field
                ToolIconButton(
                    icon = Icons.Default.CheckBox,
                    label = "Checkbox",
                    isSelected = activeTool == ActiveEditorTool.FORM_CHECKBOX,
                    onClick = onInsertCheckbox,
                    tag = "tool_checkbox"
                )

                // 9. Eraser
                ToolIconButton(
                    icon = Icons.Default.Clear,
                    label = "Eraser",
                    isSelected = activeTool == ActiveEditorTool.ERASER,
                    onClick = { onSelectTool(ActiveEditorTool.ERASER) },
                    tag = "tool_eraser"
                )

                // Separator
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // Read aloud TTS button
                FilledIconButton(
                    onClick = onReadAloud,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.testTag("tool_read_aloud")
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read Aloud", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }

                // AI "Ask PDF" Assistant button
                FilledIconButton(
                    onClick = onOpenAiSheet,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("tool_ask_ai")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Ask AI", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun ToolIconButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    IconButton(
        onClick = onClick,
        colors = if (isSelected) {
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            IconButtonDefaults.iconButtonColors()
        },
        modifier = Modifier.testTag(tag)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}
