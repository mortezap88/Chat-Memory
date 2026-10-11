package com.example.network

import com.example.data.CorrectionType
import com.example.data.LinguisticCorrection
import com.example.data.ThreePartParsedResponse
import org.json.JSONArray

object ThreePartMessageParser {
    private val POLISHED_MARKERS = listOf(
        "===POLISHED===",
        ":::POLISHED:::",
        "<<<POLISHED>>>",
        "---POLISHED---",
        "🍎🍎🍎",
        "```polished"
    )
    private val CORRECTIONS_MARKERS = listOf(
        "===CORRECTIONS===",
        ":::CORRECTIONS:::",
        "<<<CORRECTIONS>>>",
        "---CORRECTIONS---",
        "🍏🍏🍏",
        "```corrections"
    )

    fun parse(rawText: String, isStreamFinished: Boolean): ThreePartParsedResponse {
        // Find opening of POLISHED block
        var polishedStartIndex = -1
        var matchedPolishedMarker = ""
        for (m in POLISHED_MARKERS) {
            val idx = rawText.indexOf(m, ignoreCase = true)
            if (idx >= 0 && (polishedStartIndex == -1 || idx < polishedStartIndex)) {
                polishedStartIndex = idx
                matchedPolishedMarker = m
            }
        }

        // Find opening of CORRECTIONS block
        var correctionsStartIndex = -1
        var matchedCorrectionsMarker = ""
        for (m in CORRECTIONS_MARKERS) {
            val idx = rawText.indexOf(m, ignoreCase = true)
            if (idx >= 0 && (correctionsStartIndex == -1 || idx < correctionsStartIndex)) {
                correctionsStartIndex = idx
                matchedCorrectionsMarker = m
            }
        }

        // 1. MAIN ANSWER (everything before first fence)
        val mainAnswer = when {
            polishedStartIndex >= 0 && correctionsStartIndex >= 0 -> {
                val firstFence = minOf(polishedStartIndex, correctionsStartIndex)
                rawText.substring(0, firstFence).trim()
            }
            polishedStartIndex >= 0 -> rawText.substring(0, polishedStartIndex).trim()
            correctionsStartIndex >= 0 -> rawText.substring(0, correctionsStartIndex).trim()
            else -> {
                cleanTrailingFencePrefix(rawText).trim()
            }
        }

        // 2. POLISHED TEXT (Wait for closing marker!)
        var polishedText: String? = null
        if (polishedStartIndex >= 0) {
            val contentStart = polishedStartIndex + matchedPolishedMarker.length
            val closeIdx = findClosingMarkerIndex(rawText, contentStart, matchedPolishedMarker)

            if (closeIdx >= contentStart) {
                val textInside = rawText.substring(contentStart, closeIdx).trim().removeSurrounding("```").trim()
                if (textInside.isNotEmpty()) {
                    polishedText = textInside
                }
            } else if (correctionsStartIndex > contentStart) {
                val textInside = rawText.substring(contentStart, correctionsStartIndex).trim().removeSurrounding("```").trim()
                if (textInside.isNotEmpty()) {
                    polishedText = textInside
                }
            } else if (isStreamFinished) {
                val textInside = rawText.substring(contentStart).trim().removeSurrounding("```").trim()
                if (textInside.isNotEmpty()) {
                    polishedText = textInside
                }
            }
        }

        // 3. CORRECTIONS (Wait for closing marker!)
        val corrections = mutableListOf<LinguisticCorrection>()
        if (correctionsStartIndex >= 0) {
            val contentStart = correctionsStartIndex + matchedCorrectionsMarker.length
            val closeIdx = findClosingMarkerIndex(rawText, contentStart, matchedCorrectionsMarker)

            if (closeIdx >= contentStart || isStreamFinished) {
                val jsonSnippet = if (closeIdx >= contentStart) {
                    rawText.substring(contentStart, closeIdx).trim()
                } else {
                    rawText.substring(contentStart).trim()
                }
                if (jsonSnippet.isNotEmpty()) {
                    parseCorrectionsJson(jsonSnippet, corrections)
                }
            }
        }

        return ThreePartParsedResponse(
            mainAnswer = mainAnswer,
            polishedText = polishedText,
            corrections = corrections
        )
    }

    private fun findClosingMarkerIndex(rawText: String, startIndex: Int, openMarker: String): Int {
        val exactMatch = rawText.indexOf(openMarker, startIndex, ignoreCase = true)
        if (exactMatch >= startIndex) return exactMatch
        if (openMarker.startsWith("```")) {
            val codeClose = rawText.indexOf("```", startIndex)
            if (codeClose >= startIndex) return codeClose
        }
        return -1
    }

    private fun cleanTrailingFencePrefix(text: String): String {
        val markers = listOf("===", ":::", "<<<", "---", "🍎")
        for (m in markers) {
            for (len in m.length downTo 2) {
                val sub = m.substring(0, len)
                if (text.endsWith(sub)) {
                    return text.dropLast(len)
                }
            }
        }
        return text
    }

    private fun parseCorrectionsJson(jsonSnippet: String, outList: MutableList<LinguisticCorrection>) {
        try {
            val startBracket = jsonSnippet.indexOf('[')
            val endBracket = jsonSnippet.lastIndexOf(']')
            if (startBracket >= 0 && endBracket > startBracket) {
                val arrayStr = jsonSnippet.substring(startBracket, endBracket + 1)
                try {
                    val array = JSONArray(arrayStr)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val typeStr = obj.optString("type", "GRAMMAR")
                        val type = if (typeStr.equals("WORD_CHOICE", ignoreCase = true)) {
                            CorrectionType.WORD_CHOICE
                        } else {
                            CorrectionType.GRAMMAR
                        }
                        val original = obj.optString("original", "").trim()
                        val suggested = obj.optString("suggested", "").trim()
                        val reason = obj.optString("reason", "").trim()
                        if (original.isNotEmpty() && suggested.isNotEmpty()) {
                            outList.add(
                                LinguisticCorrection(
                                    type = type,
                                    original = original,
                                    suggested = suggested,
                                    reason = reason
                                )
                            )
                        }
                    }
                    if (outList.isNotEmpty()) return
                } catch (_: Throwable) {}

                // Fallback robust regex parsing for JVM unit tests and irregular JSON
                parseCorrectionsWithRegex(arrayStr, outList)
            }
        } catch (_: Throwable) {}
    }

    private fun parseCorrectionsWithRegex(snippet: String, outList: MutableList<LinguisticCorrection>) {
        val objRegex = Regex("\\{([^}]+)\\}")
        val matches = objRegex.findAll(snippet)
        for (m in matches) {
            val body = m.groupValues[1]
            val typeMatch = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"").find(body)
            val origMatch = Regex("\"original\"\\s*:\\s*\"([^\"]+)\"").find(body)
            val suggMatch = Regex("\"suggested\"\\s*:\\s*\"([^\"]+)\"").find(body)
            val reasonMatch = Regex("\"reason\"\\s*:\\s*\"([^\"]+)\"").find(body)

            val typeStr = typeMatch?.groupValues?.get(1) ?: "GRAMMAR"
            val orig = origMatch?.groupValues?.get(1)?.trim() ?: ""
            val sugg = suggMatch?.groupValues?.get(1)?.trim() ?: ""
            val reason = reasonMatch?.groupValues?.get(1)?.trim() ?: ""

            if (orig.isNotEmpty() && sugg.isNotEmpty()) {
                val type = if (typeStr.equals("WORD_CHOICE", ignoreCase = true)) {
                    CorrectionType.WORD_CHOICE
                } else {
                    CorrectionType.GRAMMAR
                }
                outList.add(
                    LinguisticCorrection(
                        type = type,
                        original = orig,
                        suggested = sugg,
                        reason = reason
                    )
                )
            }
        }
    }
}
