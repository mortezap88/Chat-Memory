package com.example.memory.retrieval

import com.example.memory.data.MemoryEntity
import kotlin.math.exp
import kotlin.math.ln

data class ScoredMemory(
    val memory: MemoryEntity,
    val finalScore: Float,
    val semanticScore: Float,
    val keywordScore: Float,
    val importanceScore: Float,
    val recencyScore: Float
)

object HybridMemoryRetriever {

    // Retrieval Weights
    private const val WEIGHT_SEMANTIC = 0.50f
    private const val WEIGHT_KEYWORD = 0.25f
    private const val WEIGHT_IMPORTANCE = 0.15f
    private const val WEIGHT_RECENCY = 0.10f

    /**
     * Scores all candidate memories against the query and returns the top K most relevant memories.
     */
    fun scoreAndRank(
        query: String,
        queryEmbedding: FloatArray,
        candidates: List<MemoryEntity>,
        topK: Int = 4,
        minScoreThreshold: Float = 0.18f
    ): List<ScoredMemory> {
        if (candidates.isEmpty() || query.isBlank()) return emptyList()

        val queryTokens = tokenize(query)
        val now = System.currentTimeMillis()

        val scored = candidates.map { memory ->
            // 1. Semantic Similarity
            val memEmbedding = VectorMath.deserializeEmbedding(memory.embeddingJson)
            val semanticScore = if (memEmbedding != null) {
                VectorMath.cosineSimilarity(queryEmbedding, memEmbedding).coerceAtLeast(0f)
            } else {
                0f
            }

            // 2. Keyword & Lexical Match
            val memoryTokens = tokenize("${memory.content} ${memory.keywords}")
            val keywordScore = calculateLexicalScore(queryTokens, memoryTokens)

            // 3. Importance Score (1 to 5 mapped to 0.2 .. 1.0)
            val importanceScore = (memory.importance.coerceIn(1, 5) / 5.0f)

            // 4. Recency & Access Frequency Score
            val daysAgo = ((now - memory.lastAccessed) / (1000.0 * 60 * 60 * 24)).coerceAtLeast(0.0)
            // Exponential decay over 30 days
            val recencyDecay = exp(-0.05 * daysAgo).toFloat()
            val frequencyBoost = (ln(1.0 + memory.accessCount) * 0.15).toFloat().coerceAtMost(0.4f)
            val recencyScore = (recencyDecay * 0.7f + frequencyBoost).coerceIn(0f, 1f)

            // Combined Hybrid Relevance
            val finalScore = (
                WEIGHT_SEMANTIC * semanticScore +
                WEIGHT_KEYWORD * keywordScore +
                WEIGHT_IMPORTANCE * importanceScore +
                WEIGHT_RECENCY * recencyScore
            )

            ScoredMemory(
                memory = memory,
                finalScore = finalScore,
                semanticScore = semanticScore,
                keywordScore = keywordScore,
                importanceScore = importanceScore,
                recencyScore = recencyScore
            )
        }

        return scored
            .filter { it.finalScore >= minScoreThreshold }
            .sortedByDescending { it.finalScore }
            .take(topK)
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[\\s,.:;!?'\"()\\[\\]{}]+"))
            .filter { it.length > 2 }
            .toSet()
    }

    private fun calculateLexicalScore(queryTokens: Set<String>, docTokens: Set<String>): Float {
        if (queryTokens.isEmpty() || docTokens.isEmpty()) return 0f
        val intersection = queryTokens.intersect(docTokens)
        if (intersection.isEmpty()) return 0f

        // Jaccard similarity with containment bias
        val overlapRatio = intersection.size.toFloat() / queryTokens.size.toFloat()
        val jaccard = intersection.size.toFloat() / (queryTokens.size + docTokens.size - intersection.size).toFloat()
        return (overlapRatio * 0.7f + jaccard * 0.3f).coerceIn(0f, 1f)
    }

    /**
     * Formats top memories into a clean, compact system injection block.
     * Uses zero fluff to minimize token overhead while maximizing instruction compliance.
     */
    fun formatMemoriesForContext(memories: List<MemoryEntity>): String {
        if (memories.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("\n=== LONG-TERM MEMORY (RECALLED USER CONTEXT) ===")
        sb.appendLine("The following atomic facts/preferences about the user were retrieved from long-term memory:")
        for (m in memories) {
            sb.appendLine("- [${m.category}] ${m.content}")
        }
        sb.appendLine("Instruction: Use these memories naturally to tailor responses. Do not quote this internal block verbatim.")
        sb.append("=== END LONG-TERM MEMORY ===\n")
        return sb.toString()
    }
}
