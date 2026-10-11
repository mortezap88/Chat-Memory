package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stream_chat_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PRIMARY_MODEL = "primary_model"
        private const val KEY_NON_THINKING = "non_thinking"
        private const val KEY_TOKEN_STREAMING = "token_streaming"
        private const val KEY_STREAMLINING = "messaging_streamlining"
        private const val KEY_TRANSCRIPTION_MODEL = "transcription_model"
        private const val KEY_SMOOTHING_MODEL = "smoothing_model"
        private const val KEY_CONTEXTUAL_SMOOTHING = "contextual_smoothing"
        private const val KEY_GRAMMAR_INSPECTOR = "grammar_inspector"
        private const val KEY_MIC_SENSITIVITY = "mic_sensitivity"
        private const val KEY_SILENCE_PAUSE = "silence_pause"
        private const val KEY_FONT_SIZE = "max_font_size"
        private const val KEY_MEMORY_ENABLED = "long_term_memory_enabled"
        private const val KEY_AUTO_EXTRACTION = "auto_memory_extraction"
        private const val KEY_MAX_MEMORIES = "max_retrieved_memories"
        private const val KEY_API_KEYS_JSON = "api_keys_json"
        private const val KEY_CHAT_MESSAGES_JSON = "chat_messages_json"
    }

    fun loadSettings(): AppSettings {
        return AppSettings(
            primaryModel = prefs.getString(KEY_PRIMARY_MODEL, "gemini-3.8-flash") ?: "gemini-3.8-flash",
            nonThinkingMode = prefs.getBoolean(KEY_NON_THINKING, true),
            tokenStreaming = prefs.getBoolean(KEY_TOKEN_STREAMING, true),
            messagingStreamlining = prefs.getBoolean(KEY_STREAMLINING, true),
            transcriptionModel = prefs.getString(KEY_TRANSCRIPTION_MODEL, "gemini-3.5-transcribe-live") ?: "gemini-3.5-transcribe-live",
            smoothingModel = prefs.getString(KEY_SMOOTHING_MODEL, "gemini-3.5-flash-lite") ?: "gemini-3.5-flash-lite",
            contextualSmoothing = prefs.getBoolean(KEY_CONTEXTUAL_SMOOTHING, true),
            grammarInspector = prefs.getBoolean(KEY_GRAMMAR_INSPECTOR, true),
            micSensitivity = prefs.getString(KEY_MIC_SENSITIVITY, "Low") ?: "Low",
            silencePauseDuration = prefs.getFloat(KEY_SILENCE_PAUSE, 2.0f),
            maxFontSizeSp = prefs.getInt(KEY_FONT_SIZE, 24),
            longTermMemoryEnabled = prefs.getBoolean(KEY_MEMORY_ENABLED, true),
            autoMemoryExtraction = prefs.getBoolean(KEY_AUTO_EXTRACTION, true),
            maxRetrievedMemories = prefs.getInt(KEY_MAX_MEMORIES, 4)
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_PRIMARY_MODEL, settings.primaryModel)
            .putBoolean(KEY_NON_THINKING, settings.nonThinkingMode)
            .putBoolean(KEY_TOKEN_STREAMING, settings.tokenStreaming)
            .putBoolean(KEY_STREAMLINING, settings.messagingStreamlining)
            .putString(KEY_TRANSCRIPTION_MODEL, settings.transcriptionModel)
            .putString(KEY_SMOOTHING_MODEL, settings.smoothingModel)
            .putBoolean(KEY_CONTEXTUAL_SMOOTHING, settings.contextualSmoothing)
            .putBoolean(KEY_GRAMMAR_INSPECTOR, settings.grammarInspector)
            .putString(KEY_MIC_SENSITIVITY, settings.micSensitivity)
            .putFloat(KEY_SILENCE_PAUSE, settings.silencePauseDuration)
            .putInt(KEY_FONT_SIZE, settings.maxFontSizeSp)
            .putBoolean(KEY_MEMORY_ENABLED, settings.longTermMemoryEnabled)
            .putBoolean(KEY_AUTO_EXTRACTION, settings.autoMemoryExtraction)
            .putInt(KEY_MAX_MEMORIES, settings.maxRetrievedMemories)
            .apply()
    }

    fun loadApiKeys(): List<ApiKeyItem> {
        val jsonStr = prefs.getString(KEY_API_KEYS_JSON, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<ApiKeyItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ApiKeyItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            key = obj.optString("key", ""),
                            label = obj.optString("label", "Key #${i + 1}"),
                            status = try {
                                KeyStatus.valueOf(obj.optString("status", "IDLE"))
                            } catch (_: Exception) {
                                KeyStatus.IDLE
                            },
                            errorMessage = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) {
                                obj.getString("errorMessage")
                            } else null,
                            failCount = obj.optInt("failCount", 0)
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {
                // fallback to default
            }
        }

        // Initialize with BuildConfig or sample keys
        val initialList = mutableListOf<ApiKeyItem>()
        val defaultKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (defaultKey.isNotBlank() && defaultKey != "MY_GEMINI_API_KEY") {
            initialList.add(
                ApiKeyItem(
                    key = defaultKey,
                    label = "Key #1",
                    status = KeyStatus.WORKING
                )
            )
        } else {
            // Provide friendly demo keys mimicking the user's pool screenshot
            initialList.add(ApiKeyItem(key = "AIzaSyAQAb8DefaultPoolKeyLAsA", label = "Key #1", status = KeyStatus.IDLE))
            initialList.add(ApiKeyItem(key = "AIzaSyAQAb8BackupFailoverRdvw", label = "Key #2", status = KeyStatus.IDLE))
            initialList.add(ApiKeyItem(key = "AIzaSyAQAb8RedundantKeye_LA", label = "Key #3", status = KeyStatus.IDLE))
            initialList.add(ApiKeyItem(key = "AIzaSyAQAb8TertiaryFallbackudJQ", label = "Key #4", status = KeyStatus.IDLE))
        }
        saveApiKeys(initialList)
        return initialList
    }

    fun saveApiKeys(keys: List<ApiKeyItem>) {
        val array = JSONArray()
        keys.forEachIndexed { index, item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("key", item.key)
                put("label", "Key #${index + 1}")
                put("status", item.status.name)
                put("errorMessage", item.errorMessage ?: JSONObject.NULL)
                put("failCount", item.failCount)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_API_KEYS_JSON, array.toString()).apply()
    }

    fun loadChatMessages(): List<ChatMessage> {
        val jsonStr = prefs.getString(KEY_CHAT_MESSAGES_JSON, null) ?: return emptyList()
        val list = mutableListOf<ChatMessage>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val corrList = mutableListOf<LinguisticCorrection>()
                val corrArray = obj.optJSONArray("corrections")
                if (corrArray != null) {
                    for (j in 0 until corrArray.length()) {
                        val cObj = corrArray.getJSONObject(j)
                        val typeStr = cObj.optString("type", "GRAMMAR")
                        val corrType = try {
                            CorrectionType.valueOf(typeStr)
                        } catch (_: Exception) {
                            CorrectionType.GRAMMAR
                        }
                        corrList.add(
                            LinguisticCorrection(
                                id = cObj.optString("id", UUID.randomUUID().toString()),
                                type = corrType,
                                original = cObj.optString("original", ""),
                                suggested = cObj.optString("suggested", ""),
                                reason = cObj.optString("reason", "")
                            )
                        )
                    }
                }
                list.add(
                    ChatMessage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        userText = obj.optString("userText", ""),
                        isAudioInput = obj.optBoolean("isAudioInput", false),
                        aiResponse = obj.optString("aiResponse", ""),
                        isStreaming = false,
                        error = if (obj.has("error") && !obj.isNull("error")) obj.getString("error") else null,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        modelUsed = obj.optString("modelUsed", "gemini-3.8-flash"),
                        corrections = corrList,
                        isAnalyzingCorrections = false
                    )
                )
            }
        } catch (_: Exception) {
            // Return whatever loaded safely
        }
        return list
    }

    fun saveChatMessages(messages: List<ChatMessage>) {
        val array = JSONArray()
        // Save the last 50 messages to keep storage fast and bounded
        val toSave = if (messages.size > 50) messages.takeLast(50) else messages
        for (msg in toSave) {
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("userText", msg.userText)
                put("isAudioInput", msg.isAudioInput)
                put("aiResponse", msg.aiResponse)
                put("error", msg.error ?: JSONObject.NULL)
                put("timestamp", msg.timestamp)
                put("modelUsed", msg.modelUsed)
                val corrArr = JSONArray()
                for (c in msg.corrections) {
                    val cObj = JSONObject().apply {
                        put("id", c.id)
                        put("type", c.type.name)
                        put("original", c.original)
                        put("suggested", c.suggested)
                        put("reason", c.reason)
                    }
                    corrArr.put(cObj)
                }
                put("corrections", corrArr)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CHAT_MESSAGES_JSON, array.toString()).apply()
    }
}

