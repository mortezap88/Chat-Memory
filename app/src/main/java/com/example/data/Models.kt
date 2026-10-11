package com.example.data

import java.util.UUID

enum class KeyStatus {
    IDLE,
    WORKING,
    FAILED
}

data class ApiKeyItem(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val label: String,
    val status: KeyStatus = KeyStatus.IDLE,
    val errorMessage: String? = null,
    val failCount: Int = 0
) {
    val maskedKey: String
        get() {
            if (key.length <= 10) return "••••"
            val start = key.take(6)
            val end = key.takeLast(4)
            return "$start••••$end"
        }
}

enum class CorrectionType {
    GRAMMAR,
    WORD_CHOICE
}

data class LinguisticCorrection(
    val id: String = UUID.randomUUID().toString(),
    val type: CorrectionType,
    val original: String,
    val suggested: String,
    val reason: String
)

data class UnifiedLinguisticResult(
    val smoothedText: String,
    val corrections: List<LinguisticCorrection>
)

data class ThreePartParsedResponse(
    val mainAnswer: String,
    val polishedText: String? = null,
    val corrections: List<LinguisticCorrection> = emptyList()
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val userText: String,
    val isAudioInput: Boolean = false,
    val aiResponse: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String = "gemini-3.5-flash-lite",
    val corrections: List<LinguisticCorrection> = emptyList(),
    val isAnalyzingCorrections: Boolean = false
)

data class FailoverEvent(
    val id: String = UUID.randomUUID().toString(),
    val fromKeyMasked: String,
    val toKeyMasked: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AppSettings(
    val primaryModel: String = "gemini-3.5-flash-lite",
    val nonThinkingMode: Boolean = true,
    val tokenStreaming: Boolean = true,
    val messagingStreamlining: Boolean = true,
    val transcriptionModel: String = "gemini-3.5-transcribe-live",
    val smoothingModel: String = "gemini-3.5-flash-lite",
    val contextualSmoothing: Boolean = true,
    val grammarInspector: Boolean = true,
    val micSensitivity: String = "Low", // "High", "Normal", "Low"
    val silencePauseDuration: Float = 2.0f,
    val maxFontSizeSp: Int = 24,
    val longTermMemoryEnabled: Boolean = true,
    val autoMemoryExtraction: Boolean = true,
    val maxRetrievedMemories: Int = 4
)

data class ExtractedMemoryItem(
    val content: String,
    val category: String,
    val importance: Int,
    val keywords: List<String>
)
