package com.example

import com.example.data.ApiKeyItem
import com.example.data.AppSettings
import com.example.data.ChatMessage
import com.example.data.FailoverEvent
import com.example.data.KeyStatus
import com.example.network.GeminiPoolService
import com.example.network.ThreePartMessageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StreamChatUnitTest {

    @Test
    fun testApiKeyItemMasking() {
        val key = ApiKeyItem(
            key = "AIzaSyAQAb8DefaultPoolKeyLAsA",
            label = "Key #1",
            status = KeyStatus.WORKING
        )
        val masked = key.maskedKey
        assertTrue(masked.startsWith("AIzaSy"))
        assertTrue(masked.endsWith("LAsA"))
        assertTrue(masked.contains("••••"))
    }

    @Test
    fun testDefaultSettings() {
        val settings = AppSettings()
        assertEquals("gemini-3.5-flash-lite", settings.primaryModel)
        assertTrue(settings.nonThinkingMode)
        assertTrue(settings.tokenStreaming)
        assertTrue(settings.messagingStreamlining)
        assertEquals(24, settings.maxFontSizeSp)
    }

    @Test
    fun testChatMessageCreation() {
        val msg = ChatMessage(
            userText = "Hello Gemini",
            isAudioInput = false,
            aiResponse = "Hi there!",
            modelUsed = "gemini-3.5-flash-lite"
        )
        assertNotNull(msg.id)
        assertEquals("Hello Gemini", msg.userText)
        assertEquals("Hi there!", msg.aiResponse)
    }

    @Test
    fun testFailoverEventCreation() {
        val event = FailoverEvent(
            fromKeyMasked = "AQ.Ab8••••LAsA",
            toKeyMasked = "AQ.Ab8••••Rdvw",
            reason = "(API Error)"
        )
        assertEquals("AQ.Ab8••••LAsA", event.fromKeyMasked)
        assertEquals("AQ.Ab8••••Rdvw", event.toKeyMasked)
        assertEquals("(API Error)", event.reason)
    }

    @Test
    fun testGeminiPoolServiceKeyCount() {
        val keys = listOf(
            ApiKeyItem(key = "key1", label = "Key #1"),
            ApiKeyItem(key = "key2", label = "Key #2")
        )
        val poolService = GeminiPoolService(keys)
        assertEquals(2, poolService.getKeysCount())
    }

    @Test
    fun testThreePartMessageParserFull() {
        val raw = """
            Here is the direct response to your question. It explains everything in detail.

            ===POLISHED===
            What is the weather like today in San Francisco?
            ===POLISHED===

            ===CORRECTIONS===
            [
              {
                "type": "GRAMMAR",
                "original": "What is weather like",
                "suggested": "What is the weather like",
                "reason": "Add definite article 'the' before weather."
              }
            ]
            ===CORRECTIONS===
        """.trimIndent()

        val parsed = ThreePartMessageParser.parse(raw, isStreamFinished = true)
        assertEquals("Here is the direct response to your question. It explains everything in detail.", parsed.mainAnswer)
        assertEquals("What is the weather like today in San Francisco?", parsed.polishedText)
        assertEquals(1, parsed.corrections.size)
        assertEquals("What is weather like", parsed.corrections[0].original)
        assertEquals("What is the weather like", parsed.corrections[0].suggested)
    }

    @Test
    fun testThreePartMessageParserWaitsForClosingDelimiter() {
        // Only opening fence has arrived, closing fence has not arrived yet while streaming
        val partial = """
            Here is my direct response.

            ===POLISHED===
            What is the weather
        """.trimIndent()

        val streamingParsed = ThreePartMessageParser.parse(partial, isStreamFinished = false)
        assertEquals("Here is my direct response.", streamingParsed.mainAnswer)
        // Polished text must be null until closing delimiter arrives!
        assertNull(streamingParsed.polishedText)
        assertTrue(streamingParsed.corrections.isEmpty())

        // Now closing fence arrives
        val closed = """
            Here is my direct response.

            ===POLISHED===
            What is the weather like today?
            ===POLISHED===
        """.trimIndent()

        val closedParsed = ThreePartMessageParser.parse(closed, isStreamFinished = false)
        assertEquals("Here is my direct response.", closedParsed.mainAnswer)
        assertEquals("What is the weather like today?", closedParsed.polishedText)
    }

    @Test
    fun testThreePartMessageParserAppleFencing() {
        val raw = """
            Main answer for apple fencing.

            🍎🍎🍎
            Polished text inside apples.
            🍎🍎🍎

            🍏🍏🍏
            [
              {
                "type": "WORD_CHOICE",
                "original": "nice",
                "suggested": "delightful",
                "reason": "More expressive."
              }
            ]
            🍏🍏🍏
        """.trimIndent()

        val parsed = ThreePartMessageParser.parse(raw, isStreamFinished = true)
        assertEquals("Main answer for apple fencing.", parsed.mainAnswer)
        assertEquals("Polished text inside apples.", parsed.polishedText)
        assertEquals(1, parsed.corrections.size)
        assertEquals("delightful", parsed.corrections[0].suggested)
    }

    @Test
    fun testLinguisticCorrectionModel() {
        val grammarCorr = com.example.data.LinguisticCorrection(
            type = com.example.data.CorrectionType.GRAMMAR,
            original = "because because",
            suggested = "because",
            reason = "Remove accidental repetition of the word."
        )
        assertEquals(com.example.data.CorrectionType.GRAMMAR, grammarCorr.type)
        assertEquals("because because", grammarCorr.original)
        assertEquals("because", grammarCorr.suggested)

        val wordChoiceCorr = com.example.data.LinguisticCorrection(
            type = com.example.data.CorrectionType.WORD_CHOICE,
            original = "so many things",
            suggested = "various factors",
            reason = "Upgrade vague phrasing to a more professional tone."
        )
        assertEquals(com.example.data.CorrectionType.WORD_CHOICE, wordChoiceCorr.type)
    }

    @Test
    fun testModelResolutionStrictlyPreservesChosenModel() {
        val pool = GeminiPoolService(emptyList())
        assertEquals("gemini-3.5-flash-lite", pool.resolveModelName("gemini-3.5-flash-lite"))
        assertEquals("gemini-3.5-flash-lite", pool.resolveModelName("Gemini 3.5 Flashlight"))
        assertEquals("gemini-3.8-flash", pool.resolveModelName("gemini-3.8-flash"))
        assertEquals("gemini-3.5-transcribe-live", pool.resolveModelName("gemini-3.5-transcribe-live"))
        assertEquals("gemini-3.5-transcribe-live", pool.resolveModelName("transcribe-live"))
        
        // Assert no conversion to gemini-2.5 under any input
        org.junit.Assert.assertNotEquals("gemini-2.5-flash", pool.resolveModelName("gemini-3.5-flash-lite"))
        org.junit.Assert.assertNotEquals("gemini-2.5-flash-lite", pool.resolveModelName("gemini-3.5-flash-lite"))
        org.junit.Assert.assertNotEquals("gemini-2.5-flash", pool.resolveModelName("gemini-3.8-flash"))
        org.junit.Assert.assertNotEquals("gemini-2.5-flash", pool.resolveModelName("gemini-3.5-transcribe-live"))
    }

    @Test
    fun testVectorMathCosineSimilarity() {
        val v1 = floatArrayOf(1f, 0f, 0f)
        val v2 = floatArrayOf(1f, 0f, 0f)
        val v3 = floatArrayOf(0f, 1f, 0f)

        // Exact match should have cosine similarity 1.0
        assertEquals(1.0f, com.example.memory.retrieval.VectorMath.cosineSimilarity(v1, v2), 0.001f)
        // Orthogonal should have cosine similarity 0.0
        assertEquals(0.0f, com.example.memory.retrieval.VectorMath.cosineSimilarity(v1, v3), 0.001f)

        // Test serialization / deserialization
        val list = listOf(0.12f, 0.45f, -0.67f)
        val json = com.example.memory.retrieval.VectorMath.serializeEmbedding(list)
        val deserialized = com.example.memory.retrieval.VectorMath.deserializeEmbedding(json)
        assertNotNull(deserialized)
        assertEquals(3, deserialized!!.size)
        assertEquals(0.12f, deserialized[0], 0.001f)
    }

    @Test
    fun testLocalDenseEmbeddingGeneration() {
        val emb1 = com.example.memory.retrieval.VectorMath.generateLocalEmbedding("software developer building audio apps")
        val emb2 = com.example.memory.retrieval.VectorMath.generateLocalEmbedding("software developer creating audio recording app")
        val emb3 = com.example.memory.retrieval.VectorMath.generateLocalEmbedding("baking chocolate chip cookies in the kitchen")

        assertEquals(384, emb1.size)
        assertEquals(384, emb2.size)

        // Related concepts should have much higher similarity than completely unrelated ones
        val simRelated = com.example.memory.retrieval.VectorMath.cosineSimilarity(emb1.toFloatArray(), emb2.toFloatArray())
        val simUnrelated = com.example.memory.retrieval.VectorMath.cosineSimilarity(emb1.toFloatArray(), emb3.toFloatArray())

        assertTrue("Related text similarity ($simRelated) should be greater than unrelated ($simUnrelated)", simRelated > simUnrelated)
    }

    @Test
    fun testHybridMemoryRetriever() {
        val memory1 = com.example.memory.data.MemoryEntity(
            id = "mem-1",
            content = "User is building an Android app in Kotlin Jetpack Compose",
            category = com.example.memory.data.MemoryCategory.PROJECT.name,
            importance = 5,
            keywords = "android, kotlin, compose, audio",
            embeddingJson = com.example.memory.retrieval.VectorMath.serializeEmbedding(
                com.example.memory.retrieval.VectorMath.generateLocalEmbedding("User is building an Android app in Kotlin Jetpack Compose")
            )
        )

        val memory2 = com.example.memory.data.MemoryEntity(
            id = "mem-2",
            content = "User loves Italian pasta and prefers gluten-free recipes",
            category = com.example.memory.data.MemoryCategory.PREFERENCE.name,
            importance = 3,
            keywords = "pasta, food, gluten-free, cooking",
            embeddingJson = com.example.memory.retrieval.VectorMath.serializeEmbedding(
                com.example.memory.retrieval.VectorMath.generateLocalEmbedding("User loves Italian pasta and prefers gluten-free recipes")
            )
        )

        val query = "How should I structure my Jetpack Compose state in Kotlin?"
        val queryEmb = com.example.memory.retrieval.VectorMath.generateLocalEmbedding(query).toFloatArray()

        val results = com.example.memory.retrieval.HybridMemoryRetriever.scoreAndRank(
            query = query,
            queryEmbedding = queryEmb,
            candidates = listOf(memory1, memory2),
            topK = 2
        )

        assertTrue(results.isNotEmpty())
        assertEquals("mem-1", results[0].memory.id)
        assertTrue(results[0].finalScore > 0.3f)

        val contextBlock = com.example.memory.retrieval.HybridMemoryRetriever.formatMemoriesForContext(listOf(memory1))
        assertTrue(contextBlock.contains("LONG-TERM MEMORY"))
        assertTrue(contextBlock.contains("Android app in Kotlin Jetpack Compose"))
    }
}
