package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.CorrectionType
import com.example.data.LinguisticCorrection

@Composable
fun CorrectionDialog(
    correction: LinguisticCorrection,
    onApply: (LinguisticCorrection) -> Unit,
    onDismiss: () -> Unit
) {
    val isGrammar = correction.type == CorrectionType.GRAMMAR
    val primaryColor = if (isGrammar) Color(0xFFEF4444) else Color(0xFFF59E0B)
    val cardBg = if (isGrammar) Color(0xFF221718) else Color(0xFF242114)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF16171A))
                .border(1.dp, Color(0xFF2A2B30), RoundedCornerShape(20.dp))
                .padding(20.dp)
                .testTag("correction_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isGrammar) Icons.Default.ErrorOutline else Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGrammar) "Grammar Mistake" else "Word Choice Improvement",
                        color = primaryColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).testTag("close_correction_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Comparison Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardBg)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column {
                        // Original
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Original:",
                                color = Color(0xFFA1A1AA),
                                fontSize = 13.5.sp,
                                modifier = Modifier.width(68.dp)
                            )
                            Text(
                                text = correction.original,
                                color = Color(0xFFF87171),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Suggest
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Suggest:",
                                color = Color(0xFFA1A1AA),
                                fontSize = 13.5.sp,
                                modifier = Modifier.width(68.dp)
                            )
                            Text(
                                text = correction.suggested,
                                color = Color(0xFF34D399),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Why explanation
                Text(
                    text = "Why:",
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = correction.reason,
                    color = Color(0xFFF4F4F5),
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons: Ignore & Apply
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA1A1AA)),
                        border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("ignore_correction_button")
                    ) {
                        Text("Ignore", fontSize = 14.sp)
                    }

                    Button(
                        onClick = { onApply(correction) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                            .testTag("apply_correction_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = if (isGrammar) Color.White else Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Apply",
                            color = if (isGrammar) Color.White else Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

data class TextSegment(
    val text: String,
    val correction: LinguisticCorrection? = null
)

/**
 * Renders user text with interactive outlined correction pills.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveCorrectedText(
    text: String,
    corrections: List<LinguisticCorrection>,
    fontSizeSp: Int,
    onCorrectionClick: (LinguisticCorrection) -> Unit,
    modifier: Modifier = Modifier
) {
    if (corrections.isEmpty()) {
        Text(
            text = text,
            color = Color.White,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.45f).sp,
            modifier = modifier
        )
        return
    }

    val segments = remember(text, corrections) {
        buildTextSegments(text, corrections)
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Center
    ) {
        segments.forEach { segment ->
            val corr = segment.correction
            if (corr != null) {
                val isGrammar = corr.type == CorrectionType.GRAMMAR
                val borderColor = if (isGrammar) Color(0xFFEF4444) else Color(0xFFF59E0B)
                val bgColor = if (isGrammar) Color(0x33EF4444) else Color(0x33F59E0B)
                val textColor = if (isGrammar) Color(0xFFFCA5A5) else Color(0xFFFDE68A)

                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                        .clickable { onCorrectionClick(corr) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = segment.text,
                        color = textColor,
                        fontSize = fontSizeSp.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text(
                    text = segment.text,
                    color = Color.White,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.45f).sp
                )
            }
        }
    }
}

private fun buildTextSegments(
    fullText: String,
    corrections: List<LinguisticCorrection>
): List<TextSegment> {
    val result = mutableListOf<TextSegment>()
    var remaining = fullText

    while (remaining.isNotEmpty()) {
        var earliestIndex = -1
        var bestCorrection: LinguisticCorrection? = null

        for (corr in corrections) {
            val idx = remaining.indexOf(corr.original, ignoreCase = true)
            if (idx >= 0 && (earliestIndex == -1 || idx < earliestIndex)) {
                earliestIndex = idx
                bestCorrection = corr
            }
        }

        if (earliestIndex >= 0 && bestCorrection != null) {
            if (earliestIndex > 0) {
                result.add(TextSegment(text = remaining.substring(0, earliestIndex)))
            }
            val matchEnd = earliestIndex + bestCorrection.original.length
            result.add(TextSegment(text = remaining.substring(earliestIndex, matchEnd), correction = bestCorrection))
            remaining = remaining.substring(matchEnd)
        } else {
            result.add(TextSegment(text = remaining))
            break
        }
    }

    return result
}
