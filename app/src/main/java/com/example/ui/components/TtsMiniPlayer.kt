package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun TtsMiniPlayer(
    isSpeaking: Boolean,
    currentLanguage: String,
    currentSpeed: Float,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onChangeLanguage: (String) -> Unit,
    onChangeSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLangMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("tts_mini_player"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Play/Pause button
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("tts_play_pause_btn")
                ) {
                    Icon(
                        if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Read Aloud",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = if (isSpeaking) "Reading Page Aloud..." else "Listen to Document",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Controls (Language & Speed)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Language Dropdown
                Box {
                    TextButton(onClick = { showLangMenu = true }) {
                        Text(
                            when (currentLanguage) {
                                "hi" -> "हिंदी"
                                "ar" -> "عربي"
                                else -> "EN"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    DropdownMenu(
                        expanded = showLangMenu,
                        onDismissRequest = { showLangMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("English (US)") },
                            onClick = { onChangeLanguage("en"); showLangMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Hindi (हिंदी)") },
                            onClick = { onChangeLanguage("hi"); showLangMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Arabic (العربية)") },
                            onClick = { onChangeLanguage("ar"); showLangMenu = false }
                        )
                    }
                }

                // Speed Dropdown
                Box {
                    IconButton(onClick = { showSpeedMenu = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = "Speed", modifier = Modifier.size(16.dp))
                            Text("${currentSpeed}x", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                            DropdownMenuItem(
                                text = { Text("${spd}x") },
                                onClick = { onChangeSpeed(spd); showSpeedMenu = false }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Player", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
