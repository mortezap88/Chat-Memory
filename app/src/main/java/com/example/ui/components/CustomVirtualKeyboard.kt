package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomVirtualKeyboard(
    visible: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    var isShifted by remember { mutableStateOf(false) }
    var isNumericMode by remember { mutableStateOf(false) }

    fun triggerKeyHaptic() {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 260)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 200)
        ) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // 1. Subtle White Line separating the entire keyboard & attached text box with the background chat
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.28f))
            )

            // 2. Color Shift Area: Dark Gray tone matching the keys background
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1C21))
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                // Attached Text Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF26282E))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Paste chip
                    IconButton(
                        onClick = {
                            triggerKeyHaptic()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clipText.isNullOrEmpty()) {
                                onTextChange(text + clipText)
                            }
                        },
                        modifier = Modifier.size(32.dp).testTag("paste_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Text display
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = "Type your message here...",
                                color = Color(0xFF71717A),
                                fontSize = 15.sp
                            )
                        } else {
                            Text(
                                text = text,
                                color = Color.White,
                                fontSize = 15.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                triggerKeyHaptic()
                                onTextChange("")
                            },
                            modifier = Modifier.size(32.dp).testTag("clear_text_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Send Button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (text.isNotBlank()) Color(0xFF00A3FF) else Color(0xFF3F3F46)
                            )
                            .clickable(
                                enabled = text.isNotBlank(),
                                onClick = {
                                    triggerKeyHaptic()
                                    val toSend = text.trim()
                                    if (toSend.isNotEmpty()) {
                                        onSend(toSend)
                                    }
                                }
                            )
                            .testTag("send_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Hide keyboard button
                    IconButton(
                        onClick = {
                            triggerKeyHaptic()
                            onClose()
                        },
                        modifier = Modifier.size(32.dp).testTag("close_keyboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Close keyboard",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Virtual Keys Matrix
                val rows = if (!isNumericMode) {
                    listOf(
                        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                        listOf("z", "x", "c", "v", "b", "n", "m")
                    )
                } else {
                    listOf(
                        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
                        listOf("!", "\"", "'", ":", ";", ",", "?")
                    )
                }

                // Row 1
                KeyRow(
                    keys = rows[0],
                    isShifted = isShifted && !isNumericMode,
                    onKeyPress = { char ->
                        triggerKeyHaptic()
                        onTextChange(text + char)
                    }
                )

                Spacer(modifier = Modifier.height(5.dp))

                // Row 2
                KeyRow(
                    keys = rows[1],
                    isShifted = isShifted && !isNumericMode,
                    onKeyPress = { char ->
                        triggerKeyHaptic()
                        onTextChange(text + char)
                    },
                    modifier = Modifier.padding(horizontal = 14.dp)
                )

                Spacer(modifier = Modifier.height(5.dp))

                // Row 3 with Shift & Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shift Key
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isShifted) Color(0xFF38BDF8) else Color(0xFF2A2B31))
                            .clickable {
                                triggerKeyHaptic()
                                isShifted = !isShifted
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardCapslock,
                            contentDescription = "Caps Lock",
                            tint = if (isShifted) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Middle Letters
                    rows[2].forEach { keyChar ->
                        val displayChar = if (isShifted && !isNumericMode) keyChar.uppercase() else keyChar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2A2B31))
                                .clickable {
                                    triggerKeyHaptic()
                                    onTextChange(text + displayChar)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayChar,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Backspace Key
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2A2B31))
                            .clickable {
                                triggerKeyHaptic()
                                if (text.isNotEmpty()) {
                                    onTextChange(text.dropLast(1))
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Row 4: Mode switch, Comma, Space, Period, Enter/Send
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ?123 / ABC toggle
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF34353C))
                            .clickable {
                                triggerKeyHaptic()
                                isNumericMode = !isNumericMode
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isNumericMode) "ABC" else "?123",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Comma
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2A2B31))
                            .clickable {
                                triggerKeyHaptic()
                                onTextChange("$text,")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = ",", color = Color.White, fontSize = 16.sp)
                    }

                    // Spacebar
                    Box(
                        modifier = Modifier
                            .weight(4.4f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2A2B31))
                            .clickable {
                                triggerKeyHaptic()
                                onTextChange("$text ")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "space",
                            color = Color(0xFFA1A1AA),
                            fontSize = 13.sp
                        )
                    }

                    // Period
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2A2B31))
                            .clickable {
                                triggerKeyHaptic()
                                onTextChange("$text.")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = ".", color = Color.White, fontSize = 16.sp)
                    }

                    // Enter / Action
                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (text.isNotBlank()) Color(0xFF00A3FF) else Color(0xFF34353C))
                            .clickable {
                                triggerKeyHaptic()
                                if (text.isNotBlank()) {
                                    onSend(text.trim())
                                } else {
                                    onTextChange("$text\n")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (text.isNotBlank()) "Send" else "↵",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyRow(
    keys: List<String>,
    isShifted: Boolean,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { key ->
            val display = if (isShifted) key.uppercase() else key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2B31))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onKeyPress(display) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = display,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
