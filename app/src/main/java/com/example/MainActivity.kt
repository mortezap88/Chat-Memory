package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioRecorderManager
import com.example.data.ApiKeyItem
import com.example.data.AppSettings
import com.example.data.ChatMessage
import com.example.data.FailoverEvent
import com.example.data.LinguisticCorrection
import com.example.data.UnifiedLinguisticResult
import com.example.data.SettingsRepository
import com.example.memory.MemoryRepository
import com.example.memory.retrieval.HybridMemoryRetriever
import com.example.network.GeminiPoolService
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.MemoryVaultScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var audioRecorderManager: AudioRecorderManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsRepository = SettingsRepository(this)
        audioRecorderManager = AudioRecorderManager(this)

        setContent {
            MyApplicationTheme {
                MainAppContainer(
                    settingsRepository = settingsRepository,
                    audioRecorderManager = audioRecorderManager,
                    context = this
                )
            }
        }
    }
}

enum class ScreenState {
    CHAT,
    SETTINGS,
    MEMORY_VAULT
}

@Composable
fun MainAppContainer(
    settingsRepository: SettingsRepository,
    audioRecorderManager: AudioRecorderManager,
    context: android.content.Context
) {
    val coroutineScope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(ScreenState.CHAT) }
    var settings by remember { mutableStateOf(settingsRepository.loadSettings()) }
    val apiKeys = remember { mutableStateListOf<ApiKeyItem>().apply { addAll(settingsRepository.loadApiKeys()) } }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            addAll(settingsRepository.loadChatMessages())
        }
    }

    var failoverEvent by remember { mutableStateOf<FailoverEvent?>(null) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var activeKeyIndex by remember { mutableStateOf(0) }

    val preTranscribedChunks = remember { Collections.synchronizedList(mutableListOf<String>()) }

    val poolService = remember {
        GeminiPoolService(
            apiKeys = apiKeys.toList(),
            onKeyStatusChanged = { updatedList ->
                coroutineScope.launch(Dispatchers.Main) {
                    apiKeys.clear()
                    apiKeys.addAll(updatedList)
                    settingsRepository.saveApiKeys(updatedList)
                }
            },
            onFailover = { event ->
                coroutineScope.launch(Dispatchers.Main) {
                    failoverEvent = event
                }
            }
        )
    }

    val memoryRepository = remember {
        MemoryRepository(
            context = context,
            poolService = poolService
        )
    }
    val allMemories by memoryRepository.allMemories.collectAsStateWithLifecycle(initialValue = emptyList())

    // Process a user message with unified 3-part streaming (Main Answer, Polished Text, Corrections)
    fun submitMessage(prompt: String, isAudio: Boolean = false, audioBase64: String? = null) {
        val messageId = UUID.randomUUID().toString()
        val newMessage = ChatMessage(
            id = messageId,
            userText = if (isAudio) "🎤 Spoken Voice Input" else prompt,
            isAudioInput = isAudio,
            aiResponse = "",
            isStreaming = true,
            modelUsed = settings.primaryModel,
            isAnalyzingCorrections = true
        )
        messages.add(newMessage)

        coroutineScope.launch {
            poolService.updateKeys(apiKeys.toList())
            activeKeyIndex = poolService.getActiveKeyIndex()

            // Retrieve relevant long-term memories if enabled
            val recalledMemories = if (settings.longTermMemoryEnabled && prompt.isNotBlank()) {
                try {
                    memoryRepository.retrieveRelevantMemories(prompt, settings.maxRetrievedMemories)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            val memoryContext = if (recalledMemories.isNotEmpty()) {
                HybridMemoryRetriever.formatMemoriesForContext(recalledMemories)
            } else {
                null
            }

            // Pass previous conversation history (only past userText and past aiResponse, NEVER corrections)
            val history = messages.filter { it.id != messageId && it.aiResponse.isNotBlank() }

            val result = poolService.executeChatRequest(
                userText = prompt,
                audioBase64 = audioBase64,
                audioMimeType = "audio/wav",
                settings = settings,
                conversationHistory = history,
                retrievedMemoriesContext = memoryContext,
                onMainAnswerReceived = { mainAnswer ->
                    coroutineScope.launch(Dispatchers.Main) {
                        val idx = messages.indexOfFirst { it.id == messageId }
                        if (idx >= 0) {
                            messages[idx] = messages[idx].copy(
                                aiResponse = mainAnswer,
                                isStreaming = true
                            )
                        }
                    }
                },
                onPolishedReceived = { polished ->
                    coroutineScope.launch(Dispatchers.Main) {
                        val idx = messages.indexOfFirst { it.id == messageId }
                        if (idx >= 0) {
                            messages[idx] = messages[idx].copy(
                                userText = polished
                            )
                        }
                    }
                },
                onCorrectionsReceived = { corrections ->
                    coroutineScope.launch(Dispatchers.Main) {
                        val idx = messages.indexOfFirst { it.id == messageId }
                        if (idx >= 0) {
                            messages[idx] = messages[idx].copy(
                                corrections = corrections,
                                isAnalyzingCorrections = false
                            )
                        }
                    }
                }
            )

            coroutineScope.launch(Dispatchers.Main) {
                activeKeyIndex = poolService.getActiveKeyIndex()
                val finalIdx = messages.indexOfFirst { it.id == messageId }
                if (finalIdx >= 0) {
                    if (result.isSuccess) {
                        val finalData = result.getOrNull()
                        val current = messages[finalIdx]
                        messages[finalIdx] = current.copy(
                            aiResponse = finalData?.mainAnswer?.ifBlank { current.aiResponse } ?: current.aiResponse,
                            userText = finalData?.polishedText ?: current.userText,
                            corrections = if (finalData != null && finalData.corrections.isNotEmpty()) finalData.corrections else current.corrections,
                            isStreaming = false,
                            isAnalyzingCorrections = false,
                            error = null
                        )
                        settingsRepository.saveChatMessages(messages.toList())

                        // Automatic background long-term memory extraction
                        if (settings.autoMemoryExtraction && finalData != null && prompt.isNotBlank()) {
                            coroutineScope.launch(Dispatchers.IO) {
                                memoryRepository.autoExtractAndSave(
                                    userText = prompt,
                                    aiResponse = finalData.mainAnswer,
                                    primaryModel = settings.primaryModel
                                )
                            }
                        }
                    } else {
                        messages[finalIdx] = messages[finalIdx].copy(
                            isStreaming = false,
                            isAnalyzingCorrections = false,
                            error = result.exceptionOrNull()?.message ?: "Failed to generate response."
                        )
                        settingsRepository.saveChatMessages(messages.toList())
                    }
                }
            }
        }
    }

    fun startVoiceRecording() {
        preTranscribedChunks.clear()
        audioRecorderManager.onChunkAvailable = { chunkBase64 ->
            coroutineScope.launch(Dispatchers.IO) {
                val transcribeRes = poolService.transcribeAudioChunk(
                    audioBase64 = chunkBase64,
                    audioMimeType = "audio/wav",
                    preferredModel = settings.transcriptionModel
                )
                if (transcribeRes.isSuccess) {
                    val rawPiece = transcribeRes.getOrNull()?.trim()
                    if (!rawPiece.isNullOrEmpty()) {
                        preTranscribedChunks.add(rawPiece)
                    }
                }
            }
        }

        val started = audioRecorderManager.startRecording(
            silencePauseSeconds = settings.silencePauseDuration,
            sensitivity = settings.micSensitivity
        )
        if (started) {
            isRecordingAudio = true
        }
    }

    fun stopVoiceRecordingAndSend() {
        if (!isRecordingAudio) return
        val recordingResult = audioRecorderManager.stopRecording()
        isRecordingAudio = false

        if (recordingResult == null) return

        coroutineScope.launch {
            val finalSnippet = recordingResult.finalChunkBase64
            var lastPiece = ""
            if (!finalSnippet.isNullOrEmpty()) {
                val lastPieceRes = poolService.transcribeAudioChunk(
                    audioBase64 = finalSnippet,
                    audioMimeType = "audio/wav",
                    preferredModel = settings.transcriptionModel
                )
                if (lastPieceRes.isSuccess) {
                    lastPiece = lastPieceRes.getOrNull()?.trim().orEmpty()
                }
            }

            val fullTranscript = buildString {
                synchronized(preTranscribedChunks) {
                    preTranscribedChunks.forEach { part ->
                        if (part.isNotBlank()) {
                            if (isNotEmpty()) append(" ")
                            append(part)
                        }
                    }
                }
                if (lastPiece.isNotBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(lastPiece)
                }
            }.trim()

            coroutineScope.launch(Dispatchers.Main) {
                if (fullTranscript.isNotBlank()) {
                    submitMessage(prompt = fullTranscript, isAudio = true)
                } else {
                    submitMessage(
                        prompt = "silence",
                        isAudio = true,
                        audioBase64 = recordingResult.fullAudioBase64
                    )
                }
            }
        }
    }

    fun applyCorrection(targetMessage: ChatMessage, correction: LinguisticCorrection) {
        val idx = messages.indexOfFirst { it.id == targetMessage.id }
        if (idx >= 0) {
            val current = messages[idx]
            val updatedText = current.userText.replaceFirst(correction.original, correction.suggested)
            val updatedCorrections = current.corrections.filter { it.id != correction.id }
            messages[idx] = current.copy(
                userText = updatedText,
                corrections = updatedCorrections
            )
            settingsRepository.saveChatMessages(messages.toList())
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceRecording()
        }
    }

    fun handleToggleVoiceRecording() {
        if (isRecordingAudio) {
            stopVoiceRecordingAndSend()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler(enabled = currentScreen == ScreenState.MEMORY_VAULT) {
        currentScreen = ScreenState.SETTINGS
    }

    BackHandler(enabled = currentScreen == ScreenState.SETTINGS) {
        currentScreen = ScreenState.CHAT
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF090A0C)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                ScreenState.CHAT -> {
                    ChatScreen(
                        messages = messages,
                        settings = settings,
                        apiKeys = apiKeys,
                        activeKeyIndex = activeKeyIndex,
                        failoverEvent = failoverEvent,
                        isRecordingAudio = isRecordingAudio,
                        onStartAudioRecording = { handleToggleVoiceRecording() },
                        onStopAudioRecording = { stopVoiceRecordingAndSend() },
                        onSendMessage = { text -> submitMessage(text) },
                        onOpenSettings = { currentScreen = ScreenState.SETTINGS },
                        onClearChat = {
                            messages.clear()
                            settingsRepository.saveChatMessages(emptyList())
                        },
                        onDismissFailover = { failoverEvent = null },
                        onRetryMessage = { chatMsg ->
                            submitMessage(chatMsg.userText, isAudio = chatMsg.isAudioInput)
                        },
                        onApplyCorrection = { msg, corr ->
                            applyCorrection(msg, corr)
                        }
                    )
                }

                ScreenState.SETTINGS -> {
                    SettingsScreen(
                        currentSettings = settings,
                        apiKeys = apiKeys,
                        memoryCount = allMemories.size,
                        onOpenMemoryVault = { currentScreen = ScreenState.MEMORY_VAULT },
                        onSaveSettings = { updated ->
                            settings = updated
                            settingsRepository.saveSettings(updated)
                        },
                        onAddKey = { newKey ->
                            val newItem = ApiKeyItem(
                                key = newKey,
                                label = "Key #${apiKeys.size + 1}"
                            )
                            apiKeys.add(newItem)
                            settingsRepository.saveApiKeys(apiKeys.toList())
                            poolService.updateKeys(apiKeys.toList())
                        },
                        onBulkAddKeys = { keysList ->
                            val currentSize = apiKeys.size
                            val newItems = keysList.mapIndexed { idx, k ->
                                ApiKeyItem(
                                    key = k,
                                    label = "Key #${currentSize + idx + 1}"
                                )
                            }
                            apiKeys.addAll(newItems)
                            settingsRepository.saveApiKeys(apiKeys.toList())
                            poolService.updateKeys(apiKeys.toList())
                        },
                        onDeleteKey = { keyId ->
                            apiKeys.removeAll { it.id == keyId }
                            settingsRepository.saveApiKeys(apiKeys.toList())
                            poolService.updateKeys(apiKeys.toList())
                        },
                        onResetDefaults = {
                            val def = AppSettings()
                            settings = def
                            settingsRepository.saveSettings(def)
                        },
                        onClose = { currentScreen = ScreenState.CHAT }
                    )
                }

                ScreenState.MEMORY_VAULT -> {
                    MemoryVaultScreen(
                        memories = allMemories,
                        onAddMemory = { content, category, importance, keywords ->
                            coroutineScope.launch {
                                memoryRepository.addMemoryNote(
                                    content = content,
                                    category = category,
                                    importance = importance,
                                    keywords = keywords
                                )
                            }
                        },
                        onUpdateMemory = { memory ->
                            coroutineScope.launch {
                                memoryRepository.updateMemory(memory)
                            }
                        },
                        onDeleteMemory = { id ->
                            coroutineScope.launch {
                                memoryRepository.deleteMemory(id)
                            }
                        },
                        onClearAllMemories = {
                            coroutineScope.launch {
                                memoryRepository.clearAll()
                            }
                        },
                        onSimulateRetrieval = { query ->
                            memoryRepository.simulateRetrieval(query)
                        },
                        onBack = { currentScreen = ScreenState.SETTINGS }
                    )
                }
            }
        }
    }
}
