package com.example.memory.retrieval

import org.json.JSONArray
import kotlin.math.sqrt

object VectorMath {

    /**
     * Computes the cosine similarity between two float arrays.
     * Returns a float between -1.0 and 1.0 (or 0.0 if vectors are empty/zero).
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty()) return 0f
        val len = minOf(v1.size, v2.size)
        var dot = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in 0 until len) {
            val a = v1[i].toDouble()
            val b = v2[i].toDouble()
            dot += a * b
            norm1 += a * a
            norm2 += b * b
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 1e-9) {
            (dot / denominator).toFloat().coerceIn(-1f, 1f)
        } else {
            0f
        }
    }

    /**
     * Serializes a list of floats to a compact JSON string.
     */
    fun serializeEmbedding(values: List<Float>): String {
        val array = JSONArray()
        for (v in values) {
            array.put(v.toDouble())
        }
        return array.toString()
    }

    /**
     * Deserializes a JSON string into a FloatArray.
     */
    fun deserializeEmbedding(json: String?): FloatArray? {
        if (json.isNullOrBlank()) return null
        return try {
            val array = JSONArray(json)
            val result = FloatArray(array.length())
            for (i in 0 until array.length()) {
                result[i] = array.getDouble(i).toFloat()
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a fast, deterministic local dense vector (384 dimensions) using
     * hashed n-gram feature projections and L2 normalization.
     * Guarantees high-speed vector retrieval even offline or when API quota is exhausted.
     */
    fun generateLocalEmbedding(text: String, dimensions: Int = 384): List<Float> {
        val vector = DoubleArray(dimensions)
        val normalizedText = text.lowercase().trim()
        val tokens = normalizedText.split(Regex("[\\s,.:;!?'\"()\\[\\]{}]+")).filter { it.length > 1 }

        // Token n-grams and character 3-grams
        for (token in tokens) {
            val weight = 1.0 / (1.0 + kotlin.math.ln(1.0 + token.length))
            val hash1 = (token.hashCode() and 0x7FFFFFFF) % dimensions
            vector[hash1] += 2.0 * weight

            // Character trigrams
            if (token.length >= 3) {
                for (i in 0..token.length - 3) {
                    val tri = token.substring(i, i + 3)
                    val hashTri = (tri.hashCode() and 0x7FFFFFFF) % dimensions
                    vector[hashTri] += 0.8 * weight
                }
            }
        }

        // L2 Normalization
        var sumSquares = 0.0
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)

        return if (norm > 1e-9) {
            vector.map { (it / norm).toFloat() }
        } else {
            List(dimensions) { 0f }
        }
    }
}
