package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ApiKeyItem
import com.example.data.AppSettings
import com.example.data.KeyStatus

@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    apiKeys: List<ApiKeyItem>,
    memoryCount: Int = 0,
    onOpenMemoryVault: () -> Unit = {},
    onSaveSettings: (AppSettings) -> Unit,
    onAddKey: (String) -> Unit,
    onBulkAddKeys: (List<String>) -> Unit,
    onDeleteKey: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onClose: () -> Unit
) {
    var primaryModel by remember { mutableStateOf(currentSettings.primaryModel) }
    var nonThinkingMode by remember { mutableStateOf(currentSettings.nonThinkingMode) }
    var tokenStreaming by remember { mutableStateOf(currentSettings.tokenStreaming) }
    var messagingStreamlining by remember { mutableStateOf(currentSettings.messagingStreamlining) }
    var transcriptionModel by remember { mutableStateOf(currentSettings.transcriptionModel) }
    var smoothingModel by remember { mutableStateOf(currentSettings.smoothingModel) }
    var contextualSmoothing by remember { mutableStateOf(currentSettings.contextualSmoothing) }
    var grammarInspector by remember { mutableStateOf(currentSettings.grammarInspector) }
    var micSensitivity by remember { mutableStateOf(currentSettings.micSensitivity) }
    var silencePauseDuration by remember { mutableFloatStateOf(currentSettings.silencePauseDuration) }
    var maxFontSizeSp by remember { mutableIntStateOf(currentSettings.maxFontSizeSp) }
    var longTermMemoryEnabled by remember { mutableStateOf(currentSettings.longTermMemoryEnabled) }
    var autoMemoryExtraction by remember { mutableStateOf(currentSettings.autoMemoryExtraction) }
    var maxRetrievedMemories by remember { mutableIntStateOf(currentSettings.maxRetrievedMemories) }

    var newKeyInput by remember { mutableStateOf("") }
    var showBulkDialog by remember { mutableStateOf(false) }
    var bulkKeysInput by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1012))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                tint = Color(0xFF00A3FF),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }

        // Scrollable Settings Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            // Section: Core Chat Preferences
            Text(
                text = "Chat & Streaming",
                color = Color(0xFF00A3FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Token Streaming Toggle
            SettingToggleItem(
                title = "Token Streaming",
                description = "Streams response tokens in real-time as they generate so you see the response sooner.",
                checked = tokenStreaming,
                onCheckedChange = { tokenStreaming = it },
                testTag = "toggle_token_streaming"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Messaging Streamlining Toggle
            SettingToggleItem(
                title = "Messaging Streamlining",
                description = "Shows only the AI's response full-screen per page. When turned off, shows both your message in a subtle white frame and the AI response in a subtle blue frame.",
                checked = messagingStreamlining,
                onCheckedChange = { messagingStreamlining = it },
                testTag = "toggle_messaging_streamlining"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Long-Term Memory & Memory Vault
            Text(
                text = "Long-Term Cognitive Memory",
                color = Color(0xFF00A3FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Multi-year persistent memory engine. Uses Hybrid Vector RAG to inject only relevant facts into context.",
                color = Color(0xFF71717A),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            // Memory Vault Access Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, Color(0xFF00A3FF).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable { onOpenMemoryVault() }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00A3FF).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF00A3FF).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color(0xFF00A3FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Open Memory Vault",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$memoryCount notes",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Browse, search, edit notes & simulate RAG recall",
                            color = Color(0xFFA1A1AA),
                            fontSize = 11.5.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Open",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Memory Toggle
            SettingToggleItem(
                title = "Enable Long-Term Memory",
                description = "Retrieves top atomic memories using hybrid vector similarity and provides context to the AI model.",
                checked = longTermMemoryEnabled,
                onCheckedChange = { longTermMemoryEnabled = it },
                testTag = "toggle_long_term_memory"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Auto-Extraction Toggle
            SettingToggleItem(
                title = "Auto-Extract from Chats",
                description = "Automatically synthesizes and stores new user facts, preferences, and projects in the background after conversations.",
                checked = autoMemoryExtraction,
                onCheckedChange = { autoMemoryExtraction = it },
                testTag = "toggle_auto_memory_extraction"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Max Retrieved Memories
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Max Injected Memories per Prompt",
                    color = Color(0xFFD4D4D8),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$maxRetrievedMemories items",
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Controls how many top-ranking memories to include. Keeps prompt concise and fast.",
                color = Color(0xFF71717A),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
            Slider(
                value = maxRetrievedMemories.toFloat(),
                onValueChange = { maxRetrievedMemories = it.toInt() },
                valueRange = 1f..8f,
                steps = 6,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00A3FF),
                    activeTrackColor = Color(0xFF00A3FF),
                    inactiveTrackColor = Color(0xFF27272A)
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section: API Key Pool
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "API Key Pool (${apiKeys.size} keys)",
                    color = Color(0xFF00A3FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { /* trigger pool refresh/test */ },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh keys",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = "Automatic failover across models & APIs. Cycles to the next key when one fails.",
                color = Color(0xFF71717A),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            // Keys list
            apiKeys.forEachIndexed { index, keyItem ->
                ApiKeyCard(
                    item = keyItem,
                    index = index + 1,
                    onDelete = { onDeleteKey(keyItem.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Add Key Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newKeyInput,
                    onValueChange = { newKeyInput = it },
                    placeholder = { Text("Paste Gemini API Key (AIza...)", fontSize = 13.sp, color = Color(0xFF71717A)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF18191D),
                        unfocusedContainerColor = Color(0xFF18191D),
                        focusedBorderColor = Color(0xFF00A3FF),
                        unfocusedBorderColor = Color(0xFF27272A)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_api_key")
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (newKeyInput.isNotBlank()) {
                            onAddKey(newKeyInput.trim())
                            newKeyInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3FF)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("add_key_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Bulk Add Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBulkDialog = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Subject,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Bulk add multiple API keys (one per line)",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section: Chat Model & Reasoning Options
            Text(
                text = "Chat Model & Reasoning",
                color = Color(0xFF00A3FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Choose model and thinking budget (None: zero reasoning latency, Low: light reasoning).",
                color = Color(0xFF71717A),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            // Chat model options requested:
            // 1. Gemini 3.5/lite - None
            // 2. Gemini 3.5/lite - Low
            // 3. Gemini 3.8/ - None
            // 4. Gemini 3.8/ - Low
            val modelOptions = listOf(
                Triple("Gemini 3.5/lite (None)", "gemini-3.5-flash-lite", true),
                Triple("Gemini 3.5/lite (Low)", "gemini-3.5-flash-lite", false),
                Triple("Gemini 3.8/ (None)", "gemini-3.8-flash", true),
                Triple("Gemini 3.8/ (Low)", "gemini-3.8-flash", false)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modelOptions.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowOptions.forEach { (label, modelKey, isNonThinking) ->
                            val isSelected = primaryModel == modelKey && nonThinkingMode == isNonThinking
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF00A3FF) else Color(0xFF1E2024))
                                    .border(1.dp, if (isSelected) Color(0xFF00A3FF) else Color(0xFF2E3138), RoundedCornerShape(8.dp))
                                    .clickable {
                                        primaryModel = modelKey
                                        nonThinkingMode = isNonThinking
                                    }
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFFA1A1AA),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Audio Transcription Model
            Text(
                text = "Audio Transcription Model",
                color = Color(0xFF00A3FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Speech chunks are transcribed using $transcriptionModel. Chat responses and linguistic polishing strictly use $primaryModel.",
                color = Color(0xFF71717A),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF18191D))
                    .border(1.dp, Color(0xFF00A3FF).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$transcriptionModel (Live Speech-to-Text)",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SettingToggleItem(
                title = "Contextual Smoothing",
                description = "Smooths sentence boundaries & punctuation across chunked audio while preserving spoken words",
                checked = contextualSmoothing,
                onCheckedChange = { contextualSmoothing = it },
                testTag = "toggle_contextual_smoothing"
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingToggleItem(
                title = "Grammar & Word Choice Inspector",
                description = "Marks grammar errors in Red and word choices in Yellow for tap-to-fix",
                checked = grammarInspector,
                onCheckedChange = { grammarInspector = it },
                testTag = "toggle_grammar_inspector"
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Microphone Sensitivity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Microphone Sensitivity",
                    color = Color(0xFF00A3FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(text = micSensitivity, color = Color(0xFFA1A1AA), fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("High", "Normal", "Low").forEach { level ->
                    val isSelected = micSensitivity == level
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF00A3FF) else Color(0xFF1E2024))
                            .border(1.dp, if (isSelected) Color(0xFF00A3FF) else Color(0xFF2E3138), RoundedCornerShape(8.dp))
                            .clickable { micSensitivity = level }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = level,
                            color = if (isSelected) Color.White else Color(0xFFA1A1AA),
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Silence Pause Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Silence Pause Duration",
                    color = Color(0xFF00A3FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${silencePauseDuration} s",
                    color = Color(0xFF10B981),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Live adjustment: Pausing for this exact duration sends audio seamlessly.",
                color = Color(0xFF71717A),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1.0f, 1.5f, 2.0f, 2.5f, 3.0f).forEach { dur ->
                    val isSelected = (silencePauseDuration - dur) in -0.1f..0.1f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF10B981) else Color(0xFF1E2024))
                            .border(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFF2E3138), RoundedCornerShape(8.dp))
                            .clickable { silencePauseDuration = dur }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${dur}s",
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Max Font Size & Live Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Max Font Size",
                    color = Color(0xFF00A3FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "$maxFontSizeSp sp", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Target maximum font size. Text will automatically shrink to fit inside the screen if needed.",
                color = Color(0xFF71717A),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )

            Slider(
                value = maxFontSizeSp.toFloat(),
                onValueChange = { maxFontSizeSp = it.toInt() },
                valueRange = 16f..36f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00A3FF),
                    activeTrackColor = Color(0xFF00A3FF),
                    inactiveTrackColor = Color(0xFF27272A)
                )
            )

            // Live Preview Card showing Auto-Shrink behavior
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF18191E))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Live Preview: Transcribed text and AI replies will appear like this, automatically shrinking when longer to remain fully visible.",
                    color = Color.White,
                    fontSize = maxFontSizeSp.sp,
                    lineHeight = (maxFontSizeSp * 1.4f).sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom Action Bar: Defaults / Save & Close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    onResetDefaults()
                    val def = AppSettings()
                    primaryModel = def.primaryModel
                    nonThinkingMode = def.nonThinkingMode
                    tokenStreaming = def.tokenStreaming
                    messagingStreamlining = def.messagingStreamlining
                    transcriptionModel = def.transcriptionModel
                    smoothingModel = def.smoothingModel
                    contextualSmoothing = def.contextualSmoothing
                    grammarInspector = def.grammarInspector
                    micSensitivity = def.micSensitivity
                    silencePauseDuration = def.silencePauseDuration
                    maxFontSizeSp = def.maxFontSizeSp
                    longTermMemoryEnabled = def.longTermMemoryEnabled
                    autoMemoryExtraction = def.autoMemoryExtraction
                    maxRetrievedMemories = def.maxRetrievedMemories
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2024)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(12.dp))
                    .testTag("defaults_button")
            ) {
                Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Defaults", color = Color.White, fontSize = 14.sp)
            }

            Button(
                onClick = {
                    val updated = currentSettings.copy(
                        primaryModel = primaryModel,
                        nonThinkingMode = nonThinkingMode,
                        tokenStreaming = tokenStreaming,
                        messagingStreamlining = messagingStreamlining,
                        transcriptionModel = transcriptionModel,
                        smoothingModel = smoothingModel,
                        contextualSmoothing = contextualSmoothing,
                        grammarInspector = grammarInspector,
                        micSensitivity = micSensitivity,
                        silencePauseDuration = silencePauseDuration,
                        maxFontSizeSp = maxFontSizeSp,
                        longTermMemoryEnabled = longTermMemoryEnabled,
                        autoMemoryExtraction = autoMemoryExtraction,
                        maxRetrievedMemories = maxRetrievedMemories
                    )
                    onSaveSettings(updated)
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3FF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp)
                    .testTag("save_settings_button")
            ) {
                Text("Save & Close", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Bulk Add Dialog
    if (showBulkDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDialog = false },
            title = { Text("Bulk Add Gemini API Keys", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Paste multiple Gemini API keys below, one per line. They will be added to the failover pool.",
                        color = Color(0xFFA1A1AA),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bulkKeysInput,
                        onValueChange = { bulkKeysInput = it },
                        placeholder = { Text("AIzaSy...\nAIzaSy...", color = Color(0xFF52525B)) },
                        minLines = 4,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF18191D),
                            unfocusedContainerColor = Color(0xFF18191D),
                            focusedBorderColor = Color(0xFF00A3FF),
                            unfocusedBorderColor = Color(0xFF27272A)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("bulk_keys_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lines = bulkKeysInput.lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        if (lines.isNotEmpty()) {
                            onBulkAddKeys(lines)
                            bulkKeysInput = ""
                        }
                        showBulkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3FF))
                ) {
                    Text("Add All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDialog = false }) {
                    Text("Cancel", color = Color(0xFFA1A1AA))
                }
            },
            containerColor = Color(0xFF1A1C20)
        )
    }
}

@Composable
private fun SettingToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, color = Color(0xFF71717A), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00A3FF),
                uncheckedThumbColor = Color(0xFFA1A1AA),
                uncheckedTrackColor = Color(0xFF27272A)
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun ApiKeyCard(
    item: ApiKeyItem,
    index: Int,
    onDelete: () -> Unit
) {
    val borderColor = when (item.status) {
        KeyStatus.WORKING -> Color(0xFF00A3FF)
        KeyStatus.FAILED -> Color(0xFFEF4444)
        KeyStatus.IDLE -> Color(0xFF2E3138)
    }

    val statusDotColor = when (item.status) {
        KeyStatus.WORKING -> Color(0xFF10B981)
        KeyStatus.FAILED -> Color(0xFFEF4444)
        KeyStatus.IDLE -> Color(0xFF71717A)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141518))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Key #$index",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = item.maskedKey,
                    color = Color(0xFFA1A1AA),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp).testTag("delete_key_$index")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete key",
                        tint = Color(0xFF71717A),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (item.status == KeyStatus.FAILED && !item.errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Failed: ${item.errorMessage}",
                    color = Color(0xFFF87171),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
