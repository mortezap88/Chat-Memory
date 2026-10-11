package com.example.network

import android.util.Log
import com.example.data.ApiKeyItem
import com.example.data.AppSettings
import com.example.data.ChatMessage
import com.example.data.ExtractedMemoryItem
import com.example.data.FailoverEvent
import com.example.data.KeyStatus
import com.example.data.LinguisticCorrection
import com.example.data.ThreePartParsedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiPoolService(
    private var apiKeys: List<ApiKeyItem>,
    private var onKeyStatusChanged: (List<ApiKeyItem>) -> Unit = {},
    private var onFailover: (FailoverEvent) -> Unit = {}
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var activeKeyIndex = 0

    init {
        val firstUsable = apiKeys.indexOfFirst { it.status != KeyStatus.FAILED }
        if (firstUsable >= 0) {
            activeKeyIndex = firstUsable
        }
    }

    fun updateKeys(newKeys: List<ApiKeyItem>) {
        apiKeys = newKeys
        if (activeKeyIndex >= apiKeys.size) {
            activeKeyIndex = 0
        }
    }

    fun getActiveKeyIndex(): Int = activeKeyIndex
    fun getKeysCount(): Int = apiKeys.size

    suspend fun executeChatRequest(
        userText: String,
        audioBase64: String? = null,
        audioMimeType: String = "audio/wav",
        settings: AppSettings,
        conversationHistory: List<ChatMessage> = emptyList(),
        retrievedMemoriesContext: String? = null,
        onMainAnswerReceived: (String) -> Unit,
        onPolishedReceived: (String) -> Unit = {},
        onCorrectionsReceived: (List<LinguisticCorrection>) -> Unit = {}
    ): Result<ThreePartParsedResponse> = withContext(Dispatchers.IO) {
        if (apiKeys.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No API keys in pool. Please add a key in Settings."))
        }

        val totalAttempts = apiKeys.size
        var attemptsMade = 0
        var lastError: Throwable? = null

        val targetModel = resolveModelName(settings.primaryModel.ifBlank { "gemini-3.5-flash-lite" })

        while (attemptsMade < totalAttempts) {
            val currentItem = apiKeys[activeKeyIndex]
            val currentKey = currentItem.key

            try {
                val fullResponse = if (settings.tokenStreaming) {
                    callStreamApi(
                        apiKey = currentKey,
                        model = targetModel,
                        prompt = userText,
                        audioBase64 = audioBase64,
                        audioMime = audioMimeType,
                        nonThinking = settings.nonThinkingMode,
                        conversationHistory = conversationHistory,
                        memoryContext = retrievedMemoriesContext,
                        onMainAnswerReceived = onMainAnswerReceived,
                        onPolishedReceived = onPolishedReceived,
                        onCorrectionsReceived = onCorrectionsReceived
                    )
                } else {
                    callNonStreamApi(
                        apiKey = currentKey,
                        model = targetModel,
                        prompt = userText,
                        audioBase64 = audioBase64,
                        audioMime = audioMimeType,
                        nonThinking = settings.nonThinkingMode,
                        conversationHistory = conversationHistory,
                        memoryContext = retrievedMemoriesContext,
                        onMainAnswerReceived = onMainAnswerReceived,
                        onPolishedReceived = onPolishedReceived,
                        onCorrectionsReceived = onCorrectionsReceived
                    )
                }

                updateKeyStatus(activeKeyIndex, KeyStatus.WORKING, null)
                return@withContext Result.success(fullResponse)

            } catch (e: Exception) {
                lastError = e
                val errorMsg = e.message ?: "Unknown error"
                Log.w("GeminiPool", "Key #${activeKeyIndex + 1} (${currentItem.maskedKey}) failed: $errorMsg")

                updateKeyStatus(activeKeyIndex, KeyStatus.FAILED, errorMsg)

                val prevKeyMasked = currentItem.maskedKey
                activeKeyIndex = (activeKeyIndex + 1) % apiKeys.size
                val nextKeyMasked = apiKeys[activeKeyIndex].maskedKey
                attemptsMade++
                onFailover(
                    FailoverEvent(
                        fromKeyMasked = prevKeyMasked,
                        toKeyMasked = nextKeyMasked,
                        reason = formatReason(errorMsg)
                    )
                )
            }
        }

        Result.failure(lastError ?: IllegalStateException("All $totalAttempts API keys failed."))
    }

    /**
     * Transcribes an audio chunk into plain text using the dedicated transcription model.
     */
    suspend fun transcribeAudioChunk(
        audioBase64: String,
        audioMimeType: String = "audio/wav",
        preferredModel: String = "gemini-3.5-transcribe-live"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKeys.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No API keys available for transcription"))
        }

        val key = apiKeys[activeKeyIndex].key
        val model = resolveModelName(preferredModel)

        try {
            val res = callRawTextApi(
                apiKey = key,
                model = model,
                prompt = "Transcribe this audio chunk verbatim into plain text. Output only the spoken words, without any explanations or quotation marks.",
                audioBase64 = audioBase64,
                audioMime = audioMimeType
            )
            Result.success(res.trim())
        } catch (e: Exception) {
            Log.w("GeminiPool", "Transcription chunk failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun resolveModelName(model: String): String {
        val clean = model.trim().removePrefix("models/").lowercase()
        return when {
            clean.contains("transcribe") -> "gemini-3.5-transcribe-live"
            clean.contains("flashlight") -> "gemini-3.5-flash-lite"
            clean.isNotBlank() -> clean
            else -> "gemini-3.5-flash-lite"
        }
    }

    private fun formatReason(errorMsg: String): String {
        return when {
            errorMsg.contains("400") -> "(API Error)"
            errorMsg.contains("404") -> "(Model Not Found)"
            errorMsg.contains("429") || errorMsg.contains("quota", ignoreCase = true) -> "(Quota Exceeded)"
            errorMsg.contains("403") || errorMsg.contains("permission", ignoreCase = true) -> "(Forbidden / Invalid Key)"
            errorMsg.contains("timeout", ignoreCase = true) -> "(Connection Timeout)"
            else -> "(API Error)"
        }
    }

    private fun updateKeyStatus(index: Int, status: KeyStatus, errorMsg: String?) {
        val updated = apiKeys.toMutableList()
        val item = updated[index]
        updated[index] = item.copy(
            status = status,
            errorMessage = errorMsg,
            failCount = if (status == KeyStatus.FAILED) item.failCount + 1 else item.failCount
        )
        apiKeys = updated
        onKeyStatusChanged(updated)
    }

    private fun buildRequestBody(
        prompt: String,
        audioBase64: String?,
        audioMime: String,
        nonThinking: Boolean,
        conversationHistory: List<ChatMessage> = emptyList(),
        includeThreePartInstructions: Boolean = true,
        memoryContext: String? = null
    ): String {
        val root = JSONObject()

        if (includeThreePartInstructions) {
            val systemInstructionObj = JSONObject()
            val sysParts = JSONArray()
            val sysTextPart = JSONObject()
            val sysContent = buildString {
                appendLine("""
                You are a smart, articulate AI conversational partner and personal language assistant.
                You MUST format your entire response in EXACTLY three consecutive sections:

                [Part 1: Your direct, conversational, helpful answer to the user's message]

                ===POLISHED===
                [Part 2: The user's input polished into fluent, natural wording while preserving their intended meaning. Clean up speech chunk seams, grammar, and stutter repetitions.]
                ===POLISHED===

                ===CORRECTIONS===
                [Part 3: A valid JSON array of linguistic corrections and word choice improvements you noticed in the user's original message, or [] if none.]
                [
                  {
                    "type": "GRAMMAR",
                    "original": "<substring from user input>",
                    "suggested": "<improved wording>",
                    "reason": "<short 1-sentence reason>"
                  },
                  {
                    "type": "WORD_CHOICE",
                    "original": "<substring from user input>",
                    "suggested": "<more natural wording>",
                    "reason": "<short 1-sentence reason>"
                  }
                ]
                ===CORRECTIONS===

                Rules:
                1. Start directly with your main answer. Do NOT put any fence around Part 1.
                2. Put the user's polished message strictly between ===POLISHED=== and ===POLISHED===.
                3. Put the JSON corrections array strictly between ===CORRECTIONS=== and ===CORRECTIONS===.
                4. "type" in corrections must strictly be either "GRAMMAR" or "WORD_CHOICE".
                5. Do not include any text after the closing ===CORRECTIONS=== fence.
                """.trimIndent())

                if (!memoryContext.isNullOrBlank()) {
                    appendLine()
                    appendLine(memoryContext)
                }
            }
            sysTextPart.put("text", sysContent)
            sysParts.put(sysTextPart)
            systemInstructionObj.put("parts", sysParts)
            root.put("systemInstruction", systemInstructionObj)
        }

        val contentsArray = JSONArray()

        // Prior conversation history (User: polished text, Model: main answer ONLY, corrections strictly excluded)
        for (past in conversationHistory) {
            if (past.userText.isNotBlank()) {
                val userTurn = JSONObject()
                val userParts = JSONArray()
                val uPart = JSONObject()
                uPart.put("text", past.userText)
                userParts.put(uPart)
                userTurn.put("role", "user")
                userTurn.put("parts", userParts)
                contentsArray.put(userTurn)
            }
            if (past.aiResponse.isNotBlank()) {
                val modelTurn = JSONObject()
                val modelParts = JSONArray()
                val mPart = JSONObject()
                mPart.put("text", past.aiResponse)
                modelParts.put(mPart)
                modelTurn.put("role", "model")
                modelTurn.put("parts", modelParts)
                contentsArray.put(modelTurn)
            }
        }

        // Current user turn
        val currentTurn = JSONObject()
        val currentParts = JSONArray()

        if (!audioBase64.isNullOrEmpty()) {
            val audioPart = JSONObject()
            val inlineData = JSONObject()
            inlineData.put("mimeType", audioMime)
            inlineData.put("data", audioBase64)
            audioPart.put("inlineData", inlineData)
            currentParts.put(audioPart)
        }

        val textPart = JSONObject()
        val promptText = if (prompt.isNotBlank()) prompt else "silence"
        textPart.put("text", promptText)
        currentParts.put(textPart)

        currentTurn.put("role", "user")
        currentTurn.put("parts", currentParts)
        contentsArray.put(currentTurn)

        root.put("contents", contentsArray)

        val generationConfig = JSONObject()
        if (!nonThinking) {
            val thinkingConfig = JSONObject()
            thinkingConfig.put("thinkingLevel", "low")
            generationConfig.put("thinkingConfig", thinkingConfig)
        }
        generationConfig.put("temperature", 0.7)
        root.put("generationConfig", generationConfig)

        return root.toString()
    }

    private fun callStreamApi(
        apiKey: String,
        model: String,
        prompt: String,
        audioBase64: String?,
        audioMime: String,
        nonThinking: Boolean,
        conversationHistory: List<ChatMessage>,
        memoryContext: String? = null,
        onMainAnswerReceived: (String) -> Unit,
        onPolishedReceived: (String) -> Unit,
        onCorrectionsReceived: (List<LinguisticCorrection>) -> Unit
    ): ThreePartParsedResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val jsonPayload = buildRequestBody(
            prompt = prompt,
            audioBase64 = audioBase64,
            audioMime = audioMime,
            nonThinking = nonThinking,
            conversationHistory = conversationHistory,
            includeThreePartInstructions = true,
            memoryContext = memoryContext
        )
        val body = jsonPayload.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            val parsedError = parseApiErrorMessage(response.code, errorBody)
            throw IllegalStateException(parsedError)
        }

        val responseBody = response.body ?: throw IllegalStateException("Empty response body from Gemini API")
        val source = responseBody.source()
        val accumulatedText = StringBuilder()
        val accumulatedThoughts = StringBuilder()

        try {
            while (!source.exhausted()) {
                val current = source.readUtf8Line() ?: break
                val trimmed = current.trim()
                if (!trimmed.startsWith("data:")) continue
                val payload = trimmed.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue

                try {
                    val jsonObj = JSONObject(payload)
                    val candidates = jsonObj.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            for (p in 0 until parts.length()) {
                                val part = parts.getJSONObject(p)
                                val isThought = part.optBoolean("thought", false)
                                val token = part.optString("text", "")
                                if (token.isNotEmpty()) {
                                    if (isThought) {
                                        accumulatedThoughts.append(token)
                                    } else {
                                        accumulatedText.append(token)
                                        val intermediate = ThreePartMessageParser.parse(
                                            rawText = accumulatedText.toString(),
                                            isStreamFinished = false
                                        )
                                        onMainAnswerReceived(intermediate.mainAnswer)
                                        if (intermediate.polishedText != null) {
                                            onPolishedReceived(intermediate.polishedText)
                                        }
                                        if (intermediate.corrections.isNotEmpty()) {
                                            onCorrectionsReceived(intermediate.corrections)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.v("GeminiPool", "Chunk parse skip: ${e.message}")
                }
            }
        } finally {
            source.close()
        }

        val rawResult = if (accumulatedText.isNotEmpty()) {
            accumulatedText.toString()
        } else {
            accumulatedThoughts.toString()
        }

        if (rawResult.isEmpty()) {
            throw IllegalStateException("Received empty stream response from $model")
        }

        return ThreePartMessageParser.parse(rawText = rawResult, isStreamFinished = true)
    }

    private fun callNonStreamApi(
        apiKey: String,
        model: String,
        prompt: String,
        audioBase64: String?,
        audioMime: String,
        nonThinking: Boolean,
        conversationHistory: List<ChatMessage> = emptyList(),
        memoryContext: String? = null,
        onMainAnswerReceived: (String) -> Unit = {},
        onPolishedReceived: (String) -> Unit = {},
        onCorrectionsReceived: (List<LinguisticCorrection>) -> Unit = {}
    ): ThreePartParsedResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val jsonPayload = buildRequestBody(
            prompt = prompt,
            audioBase64 = audioBase64,
            audioMime = audioMime,
            nonThinking = nonThinking,
            conversationHistory = conversationHistory,
            includeThreePartInstructions = true,
            memoryContext = memoryContext
        )
        val body = jsonPayload.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            val parsedError = parseApiErrorMessage(response.code, errorBody)
            throw IllegalStateException(parsedError)
        }

        val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response body")
        val rawText = extractTextFromGenerateContentResponse(responseBody)
        val parsed = ThreePartMessageParser.parse(rawText = rawText, isStreamFinished = true)

        onMainAnswerReceived(parsed.mainAnswer)
        if (parsed.polishedText != null) {
            onPolishedReceived(parsed.polishedText)
        }
        if (parsed.corrections.isNotEmpty()) {
            onCorrectionsReceived(parsed.corrections)
        }

        return parsed
    }

    private fun callRawTextApi(
        apiKey: String,
        model: String,
        prompt: String,
        audioBase64: String?,
        audioMime: String
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val jsonPayload = buildRequestBody(
            prompt = prompt,
            audioBase64 = audioBase64,
            audioMime = audioMime,
            nonThinking = true,
            conversationHistory = emptyList(),
            includeThreePartInstructions = false
        )
        val body = jsonPayload.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            val parsedError = parseApiErrorMessage(response.code, errorBody)
            throw IllegalStateException(parsedError)
        }

        val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response body")
        return extractTextFromGenerateContentResponse(responseBody)
    }

    private fun extractTextFromGenerateContentResponse(responseBody: String): String {
        val jsonObj = JSONObject(responseBody)
        val candidates = jsonObj.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                val sb = StringBuilder()
                val thoughtSb = StringBuilder()
                for (p in 0 until parts.length()) {
                    val part = parts.getJSONObject(p)
                    val isThought = part.optBoolean("thought", false)
                    val text = part.optString("text", "")
                    if (isThought) {
                        thoughtSb.append(text)
                    } else if (text.isNotEmpty()) {
                        sb.append(text)
                    }
                }
                if (sb.isNotEmpty()) return sb.toString()
                if (thoughtSb.isNotEmpty()) return thoughtSb.toString()
            }
        }
        return "No text response generated."
    }

    private fun parseApiErrorMessage(code: Int, body: String): String {
        try {
            val json = JSONObject(body)
            val errObj = json.optJSONObject("error")
            if (errObj != null) {
                val message = errObj.optString("message", "")
                if (message.isNotEmpty()) {
                    return "Gemini API error ($code): $message"
                }
            }
        } catch (_: Exception) {}
        return "Gemini API error ($code)"
    }

    /**
     * Generates a semantic vector embedding using text-embedding-004.
     */
    suspend fun generateEmbedding(text: String): Result<List<Float>> = withContext(Dispatchers.IO) {
        if (apiKeys.isEmpty() || text.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No API keys available"))
        }

        val totalAttempts = apiKeys.size
        var attemptsMade = 0

        while (attemptsMade < totalAttempts) {
            val currentItem = apiKeys[activeKeyIndex]
            val currentKey = currentItem.key

            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=$currentKey"
                val root = JSONObject()
                root.put("model", "models/text-embedding-004")
                val contentObj = JSONObject()
                val partsArray = JSONArray()
                val p = JSONObject()
                p.put("text", text)
                partsArray.put(p)
                contentObj.put("parts", partsArray)
                root.put("content", contentObj)

                val body = root.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(body).build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val respStr = response.body?.string() ?: ""
                    val json = JSONObject(respStr)
                    val embeddingObj = json.optJSONObject("embedding")
                    val values = embeddingObj?.optJSONArray("values")
                    if (values != null && values.length() > 0) {
                        val result = ArrayList<Float>(values.length())
                        for (i in 0 until values.length()) {
                            result.add(values.getDouble(i).toFloat())
                        }
                        return@withContext Result.success(result)
                    }
                }
            } catch (e: Exception) {
                Log.w("GeminiPool", "Embedding attempt failed: ${e.message}")
            }

            activeKeyIndex = (activeKeyIndex + 1) % apiKeys.size
            attemptsMade++
        }

        Result.failure(IllegalStateException("Could not generate embedding with available keys"))
    }

    /**
     * Extracts lasting facts, personal background, preferences, and goals from a conversation turn.
     * Returns structured ExtractedMemoryItem objects.
     */
    suspend fun extractMemoriesFromConversation(
        userText: String,
        aiResponse: String,
        model: String
    ): Result<List<ExtractedMemoryItem>> = withContext(Dispatchers.IO) {
        if (apiKeys.isEmpty() || userText.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        val targetModel = resolveModelName(model)
        val currentKey = apiKeys[activeKeyIndex].key

        try {
            val prompt = """
            Analyze this conversational turn and extract any LASTING facts, preferences, background details, ongoing projects, or linguistic goals explicitly revealed about the user.
            Rules:
            1. DO NOT extract temporary pleasantries, fleeting remarks, or transient actions (e.g., "I'm having dinner right now", "Hello", "Thanks").
            2. Only extract information that would be valuable to remember 6 months from now.
            3. Each memory must be an atomic declarative statement about the user in third person (e.g. "User works as a mobile developer building audio apps").
            4. Output ONLY a valid JSON array of objects with fields:
               - "content": string
               - "category": "FACT" | "PREFERENCE" | "PROJECT" | "LINGUISTIC" | "EPISODIC" | "GENERAL"
               - "importance": integer from 1 to 5
               - "keywords": array of 2-5 strings
            5. If NO lasting user details are revealed, return an empty array: []

            Conversation exchange:
            User: $userText
            AI: $aiResponse
            """.trimIndent()

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$currentKey"
            val root = JSONObject()
            val contentsArray = JSONArray()
            val turn = JSONObject()
            turn.put("role", "user")
            val partsArray = JSONArray()
            val p = JSONObject()
            p.put("text", prompt)
            partsArray.put(p)
            turn.put("parts", partsArray)
            contentsArray.put(turn)
            root.put("contents", contentsArray)

            // Generation config with low temperature
            val genConfig = JSONObject()
            genConfig.put("temperature", 0.1)
            root.put("generationConfig", genConfig)

            val body = root.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val respStr = response.body?.string() ?: ""
                val text = extractTextFromGenerateContentResponse(respStr)
                val jsonStart = text.indexOf('[')
                val jsonEnd = text.lastIndexOf(']')
                if (jsonStart < 0 || jsonEnd <= jsonStart) {
                    return@withContext Result.success(emptyList())
                }
                val jsonSubstring = text.substring(jsonStart, jsonEnd + 1)

                val array = JSONArray(jsonSubstring)
                val items = mutableListOf<ExtractedMemoryItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val content = obj.optString("content", "").trim()
                    if (content.isNotBlank()) {
                        val category = obj.optString("category", "GENERAL")
                        val importance = obj.optInt("importance", 3)
                        val kwArray = obj.optJSONArray("keywords")
                        val kwList = mutableListOf<String>()
                        if (kwArray != null) {
                            for (k in 0 until kwArray.length()) {
                                kwList.add(kwArray.getString(k))
                            }
                        }
                        items.add(ExtractedMemoryItem(content, category, importance, kwList))
                    }
                }
                return@withContext Result.success(items)
            }
        } catch (e: Exception) {
            Log.w("GeminiPool", "Memory extraction failed: ${e.message}")
        }

        Result.success(emptyList())
    }
}
